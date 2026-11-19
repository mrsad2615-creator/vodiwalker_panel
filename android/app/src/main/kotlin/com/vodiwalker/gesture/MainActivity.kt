package com.vodiwalker.gesture

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.datastore.DarkThemeMode
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.R
import com.vodiwalker.gesture.core.designsystem.theme.VodiGestureTheme
import com.vodiwalker.gesture.core.model.ExecutionTelemetryRepository
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import com.vodiwalker.gesture.engine.haptics.HapticChoreographer
import com.vodiwalker.gesture.engine.power.AdaptivePowerGovernor
import com.vodiwalker.gesture.engine.prediction.AiGesturePredictionEngine
import com.vodiwalker.gesture.engine.smartaction.AppTriggerResolver
import com.vodiwalker.gesture.feature.dashboard.DashboardRoute
import com.vodiwalker.gesture.feature.dashboard.SettingsRoute
import com.vodiwalker.gesture.feature.permissions.PermissionOnboardingRoute
import com.vodiwalker.gesture.feature.recorder.GestureRecorderRoute
import com.vodiwalker.gesture.feature.triggers.AppTriggersRoute
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt providers for the five pure-Kotlin engines (clock, guard, engines).
 */
@Module
@InstallIn(SingletonComponent::class)
object MultiEngineModule {

    /**
     * Provides the injectable monotonic clock.
     */
    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock.SYSTEM

    /**
     * Provides the process-wide anti-recursion guard.
     */
    @Provides
    @Singleton
    fun provideSyntheticGestureGuard(clock: MonotonicClock): SyntheticGestureGuard =
        SyntheticGestureGuard(clock = clock)

    /**
     * Provides the hybrid recognition engine (single instance, shared filter state).
     */
    @Provides
    @Singleton
    fun provideHybridGestureEngine(
        guard: SyntheticGestureGuard,
        clock: MonotonicClock,
    ): HybridGestureEngine = HybridGestureEngine(syntheticGuard = guard, clock = clock)

    /**
     * Provides the on-device contextual prediction engine.
     */
    @Provides
    @Singleton
    fun provideAiPredictionEngine(): AiGesturePredictionEngine = AiGesturePredictionEngine()

    /**
     * Provides the trigger-specificity resolver.
     */
    @Provides
    @Singleton
    fun provideTriggerResolver(clock: MonotonicClock): AppTriggerResolver = AppTriggerResolver(clock)

    /**
     * Provides the three-tier haptic planner.
     */
    @Provides
    @Singleton
    fun provideHapticChoreographer(clock: MonotonicClock): HapticChoreographer = HapticChoreographer(clock)

    /**
     * Provides the deterministic power governor.
     */
    @Provides
    @Singleton
    fun provideAdaptivePowerGovernor(): AdaptivePowerGovernor = AdaptivePowerGovernor()
}

/**
 * Battery-safe background worker scheduled strictly during device-idle + battery-not-low windows
 * to prune rolling execution telemetry without ever waking the CPU mid-day.
 */
@HiltWorker
class TelemetryCompactionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val telemetryRepository: ExecutionTelemetryRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        telemetryRepository.pruneOlderThan(cutoffMillis = sevenDaysAgo, maxRetainedRows = 500)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "vodigesture_idle_compaction"

        /**
         * Enqueues the daily, idle+battery-constrained telemetry pruning job (KEEP policy).
         */
        fun enqueuePeriodicIdleCompaction(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()
            val request = PeriodicWorkRequestBuilder<TelemetryCompactionWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * Application entry point supplying the Hilt worker factory to WorkManager.
 */
@HiltAndroidApp
class VodiGestureApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var starterContentInstaller: StarterContentInstaller

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Seeds the three starter gestures on the first launch after setup, once.
        starterContentInstaller.installIfNeeded()
        TelemetryCompactionWorker.enqueuePeriodicIdleCompaction(this)
    }
}

/**
 * Four destinations, ordered from "what do I do now" to "how do I fine-tune it".
 */
enum class TopLevelDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    GESTURES(R.string.nav_gestures, Icons.Rounded.Gesture),
    RULES(R.string.nav_rules, Icons.Rounded.AccountTree),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings),
}

/**
 * Single-Activity Compose host: edge-to-edge insets, an adaptive M3 navigation suite (bar on
 * phones, rail on tablets/foldables), and predictive-back choreography.
 *
 * Direction and locale come from the platform: with `values-fa` present and `supportsRtl="true"`,
 * the whole layout mirrors automatically, so no screen hard-codes left or right.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataSource: UserSettingsDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            val settings by settingsDataSource.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (settings?.darkThemeMode) {
                DarkThemeMode.AlwaysDark -> true
                DarkThemeMode.AlwaysLight -> false
                else -> isSystemInDarkTheme()
            }
            VodiGestureTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicMaterialYouColor ?: true,
                forceHighContrast = settings?.forceHighContrast ?: false,
            ) {
                VodiGestureAppShell(
                    onboardingRequired = settings?.prominentDisclosureAccepted == false,
                )
            }
        }
    }
}

/**
 * Adaptive shell. A first-run user is routed straight into setup, so the very first screen they
 * see is the one that activates the product — not an empty dashboard.
 */
@Composable
fun VodiGestureAppShell(onboardingRequired: Boolean) {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var backScale by remember { mutableFloatStateOf(1f) }

    PredictiveBackHandler(enabled = currentDestination != TopLevelDestination.HOME) { progressFlow ->
        try {
            progressFlow.collect { event -> backScale = 1f - (0.08f * event.progress) }
            backScale = 1f
            currentDestination = TopLevelDestination.HOME
        } catch (_: kotlinx.coroutines.CancellationException) {
            backScale = 1f
        }
    }

    if (onboardingRequired) {
        // Setup owns the whole window on first run: no navigation bar to get lost in.
        PermissionOnboardingRoute(
            onContinueToDashboard = { currentDestination = TopLevelDestination.HOME },
            viewModel = hiltViewModel(),
        )
        return
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backScale
                    scaleY = backScale
                },
        ) {
            when (currentDestination) {
                TopLevelDestination.HOME -> DashboardRoute(
                    onNavigateToRecorder = { currentDestination = TopLevelDestination.GESTURES },
                    onNavigateToPermissions = { currentDestination = TopLevelDestination.SETTINGS },
                    viewModel = hiltViewModel(),
                )
                TopLevelDestination.GESTURES -> GestureRecorderRoute(viewModel = hiltViewModel())
                TopLevelDestination.RULES -> AppTriggersRoute(viewModel = hiltViewModel())
                TopLevelDestination.SETTINGS -> SettingsRoute(
                    viewModel = hiltViewModel(),
                    appVersion = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
