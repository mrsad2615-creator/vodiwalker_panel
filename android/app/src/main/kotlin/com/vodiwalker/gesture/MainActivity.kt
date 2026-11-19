package com.vodiwalker.gesture

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.theme.VodiGestureTheme
import com.vodiwalker.gesture.core.model.ExecutionTelemetryRepository
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import com.vodiwalker.gesture.engine.haptics.HapticChoreographer
import com.vodiwalker.gesture.engine.power.AdaptivePowerGovernor
import com.vodiwalker.gesture.engine.prediction.AiGesturePredictionEngine
import com.vodiwalker.gesture.engine.smartaction.AppTriggerResolver
import com.vodiwalker.gesture.feature.dashboard.DashboardRoute
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        TelemetryCompactionWorker.enqueuePeriodicIdleCompaction(this)
    }
}

/**
 * Primary navigation destinations surfaced by the adaptive navigation suite.
 */
enum class TopLevelDestination(val label: String, val icon: ImageVector) {
    DASHBOARD("Studio", Icons.Rounded.Dashboard),
    RECORDER("Record", Icons.Rounded.Gesture),
    TRIGGERS("Triggers", Icons.Rounded.AccountTree),
    PERMISSIONS("Privacy", Icons.Rounded.Security),
}

/**
 * Single-Activity Jetpack Compose host enforcing Android 15 edge-to-edge display insets, adaptive
 * Material Design 3 `NavigationSuiteScaffold` (phones, foldables, tablets), and Android 14+
 * `PredictiveBackHandler` interactive scale choreography.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataSource: UserSettingsDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            val settings by settingsDataSource.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            VodiGestureTheme(
                dynamicColor = settings?.dynamicMaterialYouColor ?: true,
                forceHighContrast = settings?.forceHighContrast ?: false,
            ) {
                VodiGestureAppShell()
            }
        }
    }
}

/**
 * Adaptive M3 shell: NavigationSuiteScaffold + predictive-back scale choreography.
 */
@Composable
fun VodiGestureAppShell() {
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.DASHBOARD) }
    var backScale by remember { mutableFloatStateOf(1f) }

    // Android 14/15 Predictive Back gesture choreography returning cleanly to Dashboard
    PredictiveBackHandler(enabled = currentDestination != TopLevelDestination.DASHBOARD) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backScale = 1f - (0.08f * backEvent.progress)
            }
            backScale = 1f
            currentDestination = TopLevelDestination.DASHBOARD
        } catch (_: kotlinx.coroutines.CancellationException) {
            backScale = 1f
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                item(
                    selected = currentDestination == dest,
                    onClick = { currentDestination = dest },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
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
                TopLevelDestination.DASHBOARD -> DashboardRoute(
                    onNavigateToRecorder = { currentDestination = TopLevelDestination.RECORDER },
                    onNavigateToPermissions = { currentDestination = TopLevelDestination.PERMISSIONS },
                    viewModel = hiltViewModel(),
                )
                TopLevelDestination.RECORDER -> GestureRecorderRoute(viewModel = hiltViewModel())
                TopLevelDestination.TRIGGERS -> AppTriggersRoute(viewModel = hiltViewModel())
                TopLevelDestination.PERMISSIONS -> PermissionOnboardingRoute(
                    onContinueToDashboard = { currentDestination = TopLevelDestination.DASHBOARD },
                    viewModel = hiltViewModel(),
                )
            }
        }
    }
}
