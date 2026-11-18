package com.vodiwalker.gesture.feature.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.component.GlassmorphicCard
import com.vodiwalker.gesture.core.designsystem.component.SpringPressableCard
import com.vodiwalker.gesture.core.designsystem.theme.LocalExtendedColors
import com.vodiwalker.gesture.core.model.PermissionStatusSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Inspects Android 14/15 permission and AccessibilityService capability states without requiring
 * sensitive queries or deprecated `Settings.Secure` string parsing.
 */
object PermissionStateInspector {

    /**
     * Reads the live accessibility/notification/restricted-settings posture for [context].
     */
    fun inspect(context: Context, disclosureAccepted: Boolean): PermissionStatusSnapshot {
        val am = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = am
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        val ourService = enabledServices.firstOrNull { info ->
            info.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
        val canGesture = ourService != null &&
            (ourService.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Detect sideloaded installs on Android 13–15 (API 33+) where Restricted Settings applies
        val restrictedLikely = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ourService == null) {
            val source = runCatching {
                context.packageManager.getInstallSourceInfo(context.packageName)
            }.getOrNull()
            source?.packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                source?.packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        } else {
            false
        }

        return PermissionStatusSnapshot(
            accessibilityEnabled = ourService != null,
            prominentDisclosureAccepted = disclosureAccepted,
            notificationsGranted = notifGranted,
            restrictedSettingsLikelyBlocking = restrictedLikely,
            canPerformGesturesVerified = canGesture,
        )
    }

    /**
     * Intent that opens the system Accessibility settings screen.
     */
    fun buildAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Intent that opens this app's system info page (restricted-settings unlock path).
     */
    fun buildAppDetailsIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/**
 * Exposes permission posture and disclosure state as one UDF state stream.
 */
@HiltViewModel
class PermissionOnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsDataSource: UserSettingsDataSource,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0L)

    val uiState: StateFlow<PermissionStatusSnapshot> = combine(
        settingsDataSource.settingsFlow,
        refreshTick,
    ) { settings, _ ->
        PermissionStateInspector.inspect(appContext, settings.prominentDisclosureAccepted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PermissionStatusSnapshot(
            accessibilityEnabled = false,
            prominentDisclosureAccepted = false,
            notificationsGranted = false,
            restrictedSettingsLikelyBlocking = false,
            canPerformGesturesVerified = false,
        ),
    )

    /**
     * Records the prominent-disclosure acknowledgement.
     */
    fun acceptProminentDisclosure() {
        viewModelScope.launch {
            settingsDataSource.setProminentDisclosureAccepted(true)
            refreshStatus()
        }
    }

    /**
     * Withdraws consent and re-locks the enablement steps.
     */
    fun revokeProminentDisclosure() {
        viewModelScope.launch {
            settingsDataSource.setProminentDisclosureAccepted(false)
            refreshStatus()
        }
    }

    /**
     * Forces a re-read of platform permission state (on resume / after settings return).
     */
    fun refreshStatus() {
        refreshTick.value = System.nanoTime()
    }
}

/**
 * Stateful Hilt entry-point for Android 14/15 permission & Google Play Prominent Disclosure flow.
 */
@Composable
fun PermissionOnboardingRoute(
    onContinueToDashboard: () -> Unit,
    viewModel: PermissionOnboardingViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-evaluate immediately when user returns from Android System Accessibility Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshStatus() },
    )

    PermissionOnboardingScreen(
        state = state,
        onAcceptDisclosure = viewModel::acceptProminentDisclosure,
        onOpenAccessibilitySettings = {
            context.startActivity(PermissionStateInspector.buildAccessibilitySettingsIntent())
        },
        onOpenAppInfoForRestrictedSettings = {
            context.startActivity(PermissionStateInspector.buildAppDetailsIntent(context.packageName))
        },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onContinue = onContinueToDashboard,
    )
}

/**
 * Stateless, testable Material Design 3 Permission & Privacy Onboarding Screen.
 */
@Composable
fun PermissionOnboardingScreen(
    state: PermissionStatusSnapshot,
    onAcceptDisclosure: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfoForRestrictedSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDisclosureDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("permission_onboarding_screen"),
    ) {
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Android 14+ Privacy-First Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Zero internet permissions. Zero keystroke logging. Automatic suspension over " +
                        "passwords, lock screens, and sensitive Android 14+ windows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PermissionStepCard(
            icon = Icons.Rounded.Security,
            title = "1. Accessibility Data Disclosure",
            description = "Review exactly what Accessibility APIs VodiGesture uses before enabling.",
            isGranted = state.prominentDisclosureAccepted,
            actionLabel = if (state.prominentDisclosureAccepted) "Reviewed" else "Review & Consent",
            testTag = "step_disclosure_card",
            onClick = { showDisclosureDialog = true },
        )

        PermissionStepCard(
            icon = Icons.Rounded.TouchApp,
            title = "2. Gesture Accessibility Service",
            description = "Enables global TYPE_ACCESSIBILITY_OVERLAY edge handle and gesture synthesis " +
                "without requiring invasive Draw-Over-Other-Apps permission.",
            isGranted = state.accessibilityEnabled && state.canPerformGesturesVerified,
            enabled = state.prominentDisclosureAccepted,
            actionLabel = if (state.accessibilityEnabled) "Active" else "Open Settings",
            testTag = "step_accessibility_card",
            onClick = onOpenAccessibilitySettings,
        )

        AnimatedVisibility(visible = state.restrictedSettingsLikelyBlocking && !state.accessibilityEnabled) {
            SpringPressableCard(
                onClick = onOpenAppInfoForRestrictedSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restricted_settings_helper"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Android 13–15 Restricted Settings Guide",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Open App Info -> top-right menu -> 'Allow restricted settings', then " +
                                "return to enable VodiGesture Accessibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onOpenAppInfoForRestrictedSettings) {
                        Text("App Info")
                    }
                }
            }
        }

        PermissionStepCard(
            icon = Icons.Rounded.Notifications,
            title = "3. Status Alerts (Optional)",
            description = "Android 13+ optional notification permission for macro diagnostic alerts.",
            isGranted = state.notificationsGranted,
            actionLabel = if (state.notificationsGranted) "Granted" else "Allow",
            testTag = "step_notifications_card",
            onClick = onRequestNotifications,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            enabled = state.isCoreAutomationReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("continue_to_studio_button"),
        ) {
            Text(
                text = if (state.isCoreAutomationReady) {
                    "Enter VodiGesture Studio"
                } else {
                    "Complete Steps 1 & 2 to Activate"
                },
            )
        }
    }

    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
            title = { Text("Accessibility Service Disclosure") },
            text = {
                Text(
                    text = "VodiGesture uses the Android AccessibilityService API exclusively to:\n" +
                        "• Display a one-handed gesture overlay (TYPE_ACCESSIBILITY_OVERLAY).\n" +
                        "• Detect foreground app package changes for app-specific triggers.\n" +
                        "• Execute your configured navigation actions and synthetic gestures.\n\n" +
                        "VodiGesture never reads passwords, ignores Android 14+ accessibilityDataSensitive " +
                        "views, stores all gesture models locally on your device, and never transmits data.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisclosureDialog = false
                        onAcceptDisclosure()
                    },
                    modifier = Modifier.testTag("accept_disclosure_button"),
                ) {
                    Text("I Understand & Agree")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) {
                    Text("Decline")
                }
            },
        )
    }
}

@Composable
private fun PermissionStepCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val extended = LocalExtendedColors.current
    SpringPressableCard(
        onClick = onClick,
        enabled = enabled && !isGranted,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) extended.statusActive else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled && !isGranted,
            ) {
                Text(actionLabel)
            }
        }
    }
}
