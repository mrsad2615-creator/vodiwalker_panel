package com.neurox.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxProgressBar
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.OnboardingState

/**
 * First run.
 *
 * ORDER: why the app exists, language, profile, optional baseline, privacy,
 * accessibility. Language comes before everything else that has text, and the
 * baseline comes after a profile exists because its results are stored per profile.
 *
 * The baseline step is skippable and says so. Forcing an assessment on someone who
 * just installed an app is how you lose them.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    strings: Strings,
    onLanguageChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onGuestToggle: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onRunBaseline: () -> Unit,
    onSkipBaseline: () -> Unit,
    onFinish: () -> Unit,
    /**
     * A failure from the last write, as a catalogue key, or null.
     *
     * The previous version had no way to show a failure at all, which is why a
     * guest profile that could not be created looked like a screen that simply did
     * not respond.
     */
    errorKey: String? = null,
    /** True while a write is in flight; every button is disabled to prevent double taps. */
    busy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val steps = OnboardingState.Step.entries
        NxProgressBar(
            progress = (state.step.ordinal + 1).toFloat() / steps.size.toFloat(),
            label = strings.raw("onboarding.welcome.title"),
            valueText = strings.format("unit.of", state.step.ordinal + 1, steps.size),
            modifier = Modifier.padding(top = NxSpacing.xl),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NxSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                OnboardingState.Step.WELCOME -> StepContent(
                    title = strings.raw("onboarding.welcome.title"),
                    body = strings.raw("onboarding.welcome.body"),
                )
                OnboardingState.Step.LANGUAGE -> {
                    StepContent(
                        title = strings.raw("onboarding.language.title"),
                        body = strings.raw("onboarding.language.body"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                        Strings.SUPPORTED_LANGUAGES.forEach { tag ->
                            NxChip(
                                text = strings.raw("language.$tag"),
                                selected = state.languageTag == tag,
                                tone = if (state.languageTag == tag) ChipTone.ACCENT else ChipTone.NEUTRAL,
                                onClick = { onLanguageChange(tag) },
                            )
                        }
                    }
                }
                OnboardingState.Step.PROFILE -> {
                    StepContent(
                        title = strings.raw("onboarding.profile.title"),
                        body = strings.raw("onboarding.profile.body"),
                    )
                    OutlinedTextField(
                        value = state.profileName,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isGuest,
                        label = { Text(strings.raw("profile.name")) },
                        supportingText = { Text(strings.raw("profile.nameHint")) },
                    )
                    NxSecondaryButton(
                        label = strings.raw("profile.createGuest"),
                        onClick = { onGuestToggle(!state.isGuest) },
                    )
                }
                OnboardingState.Step.BASELINE -> {
                    StepContent(
                        title = strings.raw("onboarding.baseline.title"),
                        body = strings.raw("onboarding.baseline.body"),
                    )
                }
                OnboardingState.Step.PRIVACY -> StepContent(
                    title = strings.raw("onboarding.privacy.title"),
                    body = strings.raw("onboarding.privacy.body"),
                )
                OnboardingState.Step.ACCESSIBILITY -> StepContent(
                    title = strings.raw("onboarding.accessibility.title"),
                    body = strings.raw("onboarding.accessibility.body"),
                )
                OnboardingState.Step.DONE -> StepContent(
                    title = strings.raw("onboarding.done"),
                    body = strings.raw("home.empty.body"),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NxSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NxSpacing.sm),
        ) {
            if (errorKey != null) {
                Text(
                    text = strings.raw(errorKey),
                    style = NxTypography.body,
                    color = NxTheme.colors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = NxSpacing.sm),
                )
            }
            when (state.step) {
                OnboardingState.Step.BASELINE -> {
                    NxPrimaryButton(
                        label = strings.raw("onboarding.baseline.run"),
                        onClick = onRunBaseline,
                        enabled = !busy,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NxSecondaryButton(
                        label = strings.raw("onboarding.baseline.later"),
                        onClick = onSkipBaseline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OnboardingState.Step.DONE -> NxPrimaryButton(
                    label = strings.raw("common.start"),
                    onClick = onFinish,
                    enabled = !busy,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    NxPrimaryButton(
                        label = strings.raw(if (state.step == OnboardingState.Step.WELCOME) "onboarding.welcome.action" else "common.continue"),
                        onClick = onNext,
                        enabled = state.canContinue && !busy,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.step != OnboardingState.Step.WELCOME) {
                        NxSecondaryButton(
                            label = strings.raw("common.back"),
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepContent(title: String, body: String) {
    Text(
        text = title,
        style = NxTypography.display,
        color = NxTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NxSpacing.sm))
    Text(
        text = body,
        style = NxTypography.body,
        color = NxTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}
