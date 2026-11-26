package com.neurox.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.common.result.Outcome
import com.neurox.presentation.di.AppServices
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.OnboardingFlow
import com.neurox.presentation.screen.OnboardingState
import kotlinx.coroutines.launch

/**
 * First run.
 *
 * THE BUG THIS REPLACES
 * The previous version advanced the step and created the profile in the same
 * gesture: `createProfileIfNeeded()` launched a coroutine and returned immediately,
 * then `step = next` ran regardless. Three failures followed:
 *
 *  - the profile might not exist when the flow finished, so the completion flags
 *    were written against a null id and silently skipped;
 *  - a guest profile was created with an empty name, which the repository rejects,
 *    and the rejection was discarded - guest mode never worked at all;
 *  - `onFinished()` then reloaded the shell, which found no profile and sent the
 *    user straight back to onboarding. That is the loop reported from a device.
 *
 * The decision logic now lives in [OnboardingFlow], which is plain Kotlin and
 * covered by ten tests. This file only wires it to Compose: it awaits every write,
 * shows every failure, and never advances on a step whose write did not succeed.
 */
@Composable
fun OnboardingRoute(
    services: AppServices,
    strings: Strings,
    onFinished: () -> Unit,
    onStartBaseline: () -> Unit = onFinished,
) {
    val flow = remember(services) { OnboardingFlow(services.profileRepository, services.settingsRepository) }
    var snapshot by remember(flow) { mutableStateOf(flow.snapshot()) }
    val scope = rememberCoroutineScope()

    // The screen's own model. It is derived from the flow so the two cannot disagree
    // about which step is current or whether the profile exists.
    val state = OnboardingState(
        step = snapshot.step.toScreenStep(),
        languageTag = snapshot.languageTag,
        profileName = snapshot.profileName,
        isGuest = snapshot.isGuest,
        profileCreated = snapshot.profileCreated,
    )

    fun refresh() {
        snapshot = flow.snapshot()
    }

    /** Creates the profile, then advances only if it was created. */
    fun commitProfile(thenContinue: Boolean) {
        scope.launch {
            val outcome = flow.createProfile(OnboardingFlow.guestLabel(Strings.of(snapshot.languageTag)))
            if (outcome is Outcome.Ok && thenContinue) {
                flow.advanceAfterProfile()
            }
            refresh()
        }
    }

    OnboardingScreen(
        state = state,
        strings = Strings.of(snapshot.languageTag),
        onLanguageChange = {
            flow.setLanguage(it)
            refresh()
        },
        onNameChange = { value ->
            if (value.length <= MAX_NAME_LENGTH) {
                flow.setName(value)
                refresh()
            }
        },
        onGuestToggle = {
            flow.setGuest(it)
            refresh()
        },
        onNext = {
            if (snapshot.step == OnboardingFlow.Step.PROFILE) {
                // The profile step is the only one that writes before advancing.
                if (snapshot.profileCreated) {
                    flow.advanceAfterProfile()
                    refresh()
                } else {
                    commitProfile(thenContinue = true)
                }
            } else {
                flow.next()
                refresh()
            }
        },
        onBack = {
            flow.back()
            refresh()
        },
        onRunBaseline = {
            scope.launch {
                // A profile must exist before the baseline can store anything.
                if (!snapshot.profileCreated) {
                    val created = flow.createProfile(OnboardingFlow.guestLabel(Strings.of(snapshot.languageTag)))
                    if (created is Outcome.Err) {
                        refresh()
                        return@launch
                    }
                }
                flow.complete(baselineRequested = true)
                refresh()
                if (flow.snapshot().error == null) onStartBaseline()
            }
        },
        onSkipBaseline = {
            flow.skipBaseline()
            refresh()
        },
        onFinish = {
            scope.launch {
                // Never complete without a profile: the old code returned silently and
                // left the user back at the beginning with no explanation.
                if (!snapshot.profileCreated) {
                    val created = flow.createProfile(OnboardingFlow.guestLabel(Strings.of(snapshot.languageTag)))
                    if (created is Outcome.Err) {
                        refresh()
                        return@launch
                    }
                }
                val done = flow.complete(baselineRequested = false)
                refresh()
                if (done is Outcome.Ok) onFinished()
            }
        },
        errorKey = snapshot.error?.let { "error.${it.code}" },
        busy = snapshot.busy,
    )
}

/** Maps the tested flow's step onto the screen model's step. */
private fun OnboardingFlow.Step.toScreenStep(): OnboardingState.Step = when (this) {
    OnboardingFlow.Step.WELCOME -> OnboardingState.Step.WELCOME
    OnboardingFlow.Step.LANGUAGE -> OnboardingState.Step.LANGUAGE
    OnboardingFlow.Step.PROFILE -> OnboardingState.Step.PROFILE
    OnboardingFlow.Step.BASELINE -> OnboardingState.Step.BASELINE
    OnboardingFlow.Step.PRIVACY -> OnboardingState.Step.PRIVACY
    OnboardingFlow.Step.ACCESSIBILITY -> OnboardingState.Step.ACCESSIBILITY
    OnboardingFlow.Step.DONE -> OnboardingState.Step.DONE
}

private const val MAX_NAME_LENGTH = 32
