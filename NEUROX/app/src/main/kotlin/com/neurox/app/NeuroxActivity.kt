package com.neurox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neurox.app.di.AppContainer
import com.neurox.app.nav.NeuroxNavHost
import com.neurox.app.nav.goTo
import com.neurox.common.MotionModeName
import com.neurox.data.prefs.NxPreferences
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NeuroxAppTheme
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.domain.repository.first
import com.neurox.feature.analytics.AnalyticsRoute
import com.neurox.feature.home.HomeRoute
import com.neurox.feature.library.LibraryRoute
import com.neurox.feature.onboarding.OnboardingRoute
import com.neurox.feature.profile.ProfileRoute
import com.neurox.feature.read.ReadingStudioRoute
import com.neurox.feature.session.SessionRoute
import com.neurox.feature.settings.SettingsRoute
import com.neurox.feature.settings.VaultExportRoute
import com.neurox.feature.settings.VaultImportRoute
import com.neurox.feature.vision.ModuleBrowserRoute
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.nav.AppDestination
import com.neurox.presentation.nav.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The single activity.
 *
 * RESPONSIBILITIES (and nothing else): install the splash screen, load the active
 * profile's preferences, wrap the tree in the theme, and route each destination to its
 * feature entry point. Every screen is a module; the activity knows the routes and
 * nothing about how a screen works.
 *
 * EDGE TO EDGE: enabled here and handled per screen with insets, so the session
 * surface can paint behind the system bars.
 */
class NeuroxActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The import above is the API: installSplashScreen() lives in
        // SplashScreen.Companion as an extension on ComponentActivity.
        val splash: SplashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NeuroxApplication).container

        // Hold the splash until the first real frame: a flash of an empty scaffold is
        // worse than 200 ms of branded splash.
        var ready by mutableStateOf(false)
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            var state by remember { mutableStateOf(AppUiState.initial()) }
            var reloadToken by remember { mutableStateOf(0) }

            LaunchedEffect(reloadToken) {
                state = withContext(Dispatchers.IO) { loadState(container) }
                ready = true
            }

            val strings = Strings.of(state.languageTag)
            NeuroxAppTheme(
                strings = strings,
                themeId = state.themeId,
                motionMode = state.motionMode,
                reducedMotion = state.reducedMotion,
            ) {
                if (!state.loaded) {
                    // The splash is still up; drawing anything here would race it.
                    return@NeuroxAppTheme
                }
                val start = if (state.needsOnboarding) AppDestination.ONBOARDING else AppDestination.HOME
                val navController = rememberNavController()
                val reload = { reloadToken += 1 }

                NeuroxNavHost(
                    navController = navController,
                    startDestination = start,
                    bottomBar = { current ->
                        NeuroxBottomBar(
                            current = current,
                            strings = strings,
                            onSelect = { destination -> navController.goTo(destination) },
                        )
                    },
                ) { destination, nav ->
                    when (destination) {
                        AppDestination.HOME -> HomeRoute(
                            services = container,
                            strings = strings,
                            onOpenModule = { module ->
                                nav.navigate(AppDestination.fromModule(module)?.route ?: AppDestination.HOME.route)
                            },
                            onStartExercise = { template ->
                                nav.navigate(NavigationState.sessionRoute(template.id))
                            },
                            onOpenAnalytics = { nav.navigate(AppDestination.ANALYTICS.route) },
                            onOpenLibrary = { nav.navigate(AppDestination.LIBRARY.route) },
                            onOpenProfile = { nav.navigate(AppDestination.PROFILE.route) },
                        )

                        AppDestination.VISION,
                        AppDestination.REFLEX,
                        AppDestination.READ,
                        AppDestination.TYPE,
                        -> ModuleBrowserRoute(
                            services = container,
                            module = destination.module ?: com.neurox.domain.model.Module.VISION,
                            strings = strings,
                            onStartExercise = { template ->
                                nav.navigate(NavigationState.sessionRoute(template.id))
                            },
                            onOpenReadingStudio = { nav.navigate(AppDestination.READING_STUDIO.route) },
                        )

                        AppDestination.SESSION -> SessionRoute(
                            services = container,
                            strings = strings,
                            exerciseId = nav.currentExerciseId(),
                            completionState = com.neurox.domain.model.SessionKind.TRAINING,
                            haptics = container.haptics,
                            audioRenderer = container.audioRenderer::play,
                            onFinished = { nav.popBackStack() },
                        )

                        AppDestination.BASELINE -> SessionRoute(
                            services = container,
                            strings = strings,
                            // The baseline is the adaptive engine's calibration run, so
                            // it always uses the focus-lock template.
                            exerciseId = com.neurox.domain.exercise.ExerciseTemplate.FOCUS_LOCK.id,
                            completionState = com.neurox.domain.model.SessionKind.BASELINE,
                            haptics = container.haptics,
                            audioRenderer = container.audioRenderer::play,
                            onFinished = { nav.popBackStack() },
                        )

                        AppDestination.ANALYTICS -> AnalyticsRoute(services = container, strings = strings)

                        AppDestination.LIBRARY,
                        AppDestination.EXERCISE_BUILDER,
                        -> LibraryRoute(
                            services = container,
                            strings = strings,
                            onStartExercise = { template ->
                                nav.navigate(NavigationState.sessionRoute(template.id))
                            },
                        )

                        AppDestination.PROFILE -> ProfileRoute(
                            services = container,
                            strings = strings,
                            onProfileSwitched = reload,
                        )

                        AppDestination.SETTINGS -> SettingsRoute(
                            services = container,
                            strings = strings,
                            audioRenderer = container.audioRenderer,
                            onPreferencesChanged = reload,
                            onExport = { nav.navigate(AppDestination.VAULT_EXPORT.route) },
                            onImport = { nav.navigate(AppDestination.VAULT_IMPORT.route) },
                        )

                        AppDestination.ONBOARDING -> OnboardingRoute(
                            services = container,
                            strings = strings,
                            onFinished = reload,
                            onStartBaseline = {
                                nav.navigate(AppDestination.BASELINE.route) {
                                    popUpTo(AppDestination.ONBOARDING.route) { inclusive = true }
                                }
                            },
                        )

                        AppDestination.VAULT_EXPORT -> VaultExportRoute(
                            services = container,
                            strings = strings,
                            vaultService = container.vaultService,
                            backupCoordinator = container.backupCoordinator,
                            onClose = { nav.popBackStack() },
                        )

                        AppDestination.VAULT_IMPORT -> VaultImportRoute(
                            services = container,
                            strings = strings,
                            vaultService = container.vaultService,
                            backupCoordinator = container.backupCoordinator,
                            onClose = { nav.popBackStack() },
                            onRestored = reload,
                        )

                        AppDestination.READING_STUDIO -> ReadingStudioRoute(
                            services = container,
                            strings = strings,
                            onClose = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads the active profile and its preferences.
     *
     * A fresh install has no profile, which is not an error: it is the onboarding path
     * and is represented as such, not as a null every screen has to defend against.
     *
     * SNAPSHOT, NOT SUBSCRIPTION: both reads take the first emitted value and return.
     * Collecting these streams to completion never returns (they are backed by Room
     * flows), which used to leave the splash screen up forever.
     */
    private suspend fun loadState(container: AppContainer): AppUiState {
        val profile = container.profileRepository.getActiveProfile()
        val preferences = profile?.let { active ->
            val values = container.settingsRepository.observeSettings(active.id).first().orEmpty()
            NxPreferences.from(values)
        } ?: NxPreferences()

        if (profile != null) {
            // Refresh the audio cache before the first cue can be requested, so silent
            // mode is in force from the very first sound.
            container.audioRepository.observe(profile.id).first()?.let(container.audioSettings::update)
        }

        return AppUiState(
            loaded = true,
            needsOnboarding = profile == null,
            languageTag = preferences.languageTag,
            themeId = preferences.themeId,
            motionMode = preferences.motionMode,
            reducedMotion = preferences.reducedMotion,
        )
    }
}

/** The `exerciseId` nav argument of the current destination, if any. */
private fun androidx.navigation.NavHostController.currentExerciseId(): String? =
    currentBackStackEntry?.arguments?.let { bundle ->
        @Suppress("DEPRECATION")
        bundle.getString("exerciseId")
    }

@Composable
private fun NeuroxBottomBar(
    current: AppDestination,
    strings: Strings,
    onSelect: (AppDestination) -> Unit,
) {
    // The bar is intentionally plain: five destinations, text labels, no icons to
    // invent. Text labels survive font scaling and localization; an icon set would
    // need its own accessibility pass.
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NxSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (destination in NavigationState.topLevel()) {
                NxChip(
                    text = strings.raw(destination.labelKey),
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}
