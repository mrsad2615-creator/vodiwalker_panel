package com.neurox.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.presentation.di.AppServices
import com.neurox.common.result.Outcome
import com.neurox.data.prefs.SettingsKeys
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.OnboardingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First run.
 *
 * A profile is created at the end of the profile step, not at the end of the flow, so
 * the baseline has somewhere to store its results. If the user picks a guest profile,
 * it is still a real row: "guest" means no name and no PIN, not "no data isolation".
 *
 * Language is written to the *new profile* and to nothing global, because two people
 * sharing a phone may want different languages.
 */
@Composable
fun OnboardingRoute(
    services: AppServices,
    strings: Strings,
    onFinished: () -> Unit,
    onStartBaseline: () -> Unit = onFinished,
) {
    var step by remember { mutableStateOf(OnboardingState.Step.WELCOME) }
    var language by remember { mutableStateOf(strings.languageTag) }
    var name by remember { mutableStateOf("") }
    var guest by remember { mutableStateOf(false) }
    var createdId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun createProfileIfNeeded() {
        if (createdId != null) return
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                services.profileRepository.createProfile(
                    displayName = if (guest) "" else name.trim(),
                    isGuest = guest,
                )
            }
            if (outcome is Outcome.Ok) {
                val id = outcome.value.id
                createdId = id
                withContext(Dispatchers.IO) {
                    services.profileRepository.updateSettingsPatch(id, themeId = null, languageTag = language)
                    services.settingsRepository.put(id, SettingsKeys.LANGUAGE_TAG, language)
                    services.settingsRepository.put(id, SettingsKeys.ONBOARDING_SEEN_INTRO, "true")
                }
            }
        }
    }

    val state = OnboardingState(
        step = step,
        languageTag = language,
        profileName = name,
        isGuest = guest,
        profileCreated = createdId != null,
    )

    OnboardingScreen(
        state = state,
        strings = Strings.of(language),
        onLanguageChange = { language = it },
        onNameChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
        onGuestToggle = { guest = it },
        onNext = {
            when (step) {
                OnboardingState.Step.PROFILE -> createProfileIfNeeded()
                OnboardingState.Step.WELCOME,
                OnboardingState.Step.LANGUAGE,
                OnboardingState.Step.BASELINE,
                OnboardingState.Step.PRIVACY,
                OnboardingState.Step.ACCESSIBILITY,
                -> Unit
                OnboardingState.Step.DONE -> Unit
            }
            step = OnboardingState.Step.entries.getOrElse(step.ordinal + 1) { OnboardingState.Step.DONE }
        },
        onBack = {
            step = OnboardingState.Step.entries.getOrElse(step.ordinal - 1) { OnboardingState.Step.WELCOME }
        },
        onRunBaseline = {
            // The baseline screen belongs to the host, so this only records the intent
            // and hands over; a screen that navigated by itself could not be tested.
            scope.launch {
                completeOnboarding(services, createdId, baselineRequested = true)
                onStartBaseline()
            }
        },
        onSkipBaseline = {
            scope.launch {
                createdId?.let { id ->
                    withContext(Dispatchers.IO) {
                        services.settingsRepository.put(id, SettingsKeys.BASELINE_SKIPPED, "true")
                    }
                }
                step = OnboardingState.Step.PRIVACY
            }
        },
        onFinish = {
            scope.launch {
                completeOnboarding(services, createdId, baselineRequested = false)
                onFinished()
            }
        },
    )
}

/**
 * Marks onboarding complete.
 *
 * This is the last write of the flow. If it fails, the flag stays false and onboarding
 * is offered again - the safe direction to fail, because a user who sees the intro
 * twice loses a minute, while a user who never sees it loses the explanation of what
 * the app is.
 */
private suspend fun completeOnboarding(
    services: AppServices,
    profileId: String?,
    baselineRequested: Boolean,
) {
    val id = profileId ?: return
    withContext(Dispatchers.IO) {
        services.settingsRepository.put(id, SettingsKeys.ONBOARDING_COMPLETE, "true")
        services.settingsRepository.put(id, SettingsKeys.ONBOARDING_SEEN_INTRO, "true")
        if (baselineRequested) {
            services.settingsRepository.put(id, SettingsKeys.BASELINE_COMPLETE, "false")
        }
    }
}

private const val MAX_NAME_LENGTH = 32
