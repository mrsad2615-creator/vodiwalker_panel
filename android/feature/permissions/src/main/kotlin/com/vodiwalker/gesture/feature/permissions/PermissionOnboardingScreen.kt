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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vodiwalker.gesture.core.datastore.UserSettingsDataSource
import com.vodiwalker.gesture.core.designsystem.R
import com.vodiwalker.gesture.core.designsystem.component.VodiPrimaryButton
import com.vodiwalker.gesture.core.designsystem.component.VodiSecondaryButton
import com.vodiwalker.gesture.core.designsystem.text.PersianText
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
 * Reads the live accessibility, notification and restricted-settings posture for [context] without
 * sensitive queries or deprecated `Settings.Secure` parsing.
 */
object PermissionStateInspector {

    /**
     * Returns the current permission snapshot.
     */
    fun inspect(context: Context, disclosureAccepted: Boolean): PermissionStatusSnapshot {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabled = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        val ourService = enabled.firstOrNull { info ->
            info.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
        val canGesture = ourService != null &&
            (ourService.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Sideloaded installs on Android 13–15 may be blocked by Restricted Settings.
        val restrictedLikely =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ourService == null && runCatching {
                val source = context.packageManager.getInstallSourceInfo(context.packageName)
                source.packageSource == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                    source.packageSource ==
                    android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
            }.getOrDefault(false)

        return PermissionStatusSnapshot(
            accessibilityEnabled = ourService != null,
            prominentDisclosureAccepted = disclosureAccepted,
            notificationsGranted = notificationsGranted,
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
     * Intent that opens this app's system info page (the restricted-settings unlock path).
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
 * Exposes the permission posture and the disclosure acknowledgement as one UDF state stream.
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
     * Forces a re-read of platform permission state (on resume / after returning from settings).
     */
    fun refreshStatus() {
        refreshTick.value = System.nanoTime()
    }
}

/** The three steps a new user actually has to perform, in order. */
private enum class SetupStep { Disclosure, Accessibility, Done }

/**
 * Onboarding that a first-time user can finish without reading documentation:
 *
 * 1. one plain-language privacy sentence + a single "I agree" button,
 * 2. one button that opens the Accessibility settings, with live confirmation on return,
 * 3. a success screen with the one physical action to try.
 *
 * Optional permissions are offered as a dismissible row on the success screen, never as a gate.
 */
@Composable
fun PermissionOnboardingRoute(
    onContinueToDashboard: () -> Unit,
    viewModel: PermissionOnboardingViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read platform state the moment the user returns from system settings.
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
            runCatching {
                context.startActivity(PermissionStateInspector.buildAccessibilitySettingsIntent())
            }
        },
        onOpenAppInfoForRestrictedSettings = {
            runCatching {
                context.startActivity(
                    PermissionStateInspector.buildAppDetailsIntent(context.packageName),
                )
            }
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
 * Stateless, testable onboarding surface. One decision per screen, maximum two actions.
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
    val step = when {
        !state.prominentDisclosureAccepted -> SetupStep.Disclosure
        !state.accessibilityEnabled -> SetupStep.Accessibility
        else -> SetupStep.Done
    }
    val stepIndex = when (step) {
        SetupStep.Disclosure -> 1
        SetupStep.Accessibility -> 2
        SetupStep.Done -> 3
    }
    val progress by animateFloatAsState(
        targetValue = stepIndex / 3f,
        label = "setup_progress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("permission_onboarding_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Progress is expressed as position (۱ از ۳) as well as a bar: never colour alone.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.setup_step_of, faNumber(stepIndex), faNumber(3)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        when (step) {
            SetupStep.Disclosure -> DisclosureStep(onAcceptDisclosure)
            SetupStep.Accessibility -> AccessibilityStep(
                state = state,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenAppInfoForRestrictedSettings = onOpenAppInfoForRestrictedSettings,
            )
            SetupStep.Done -> DoneStep(
                state = state,
                onRequestNotifications = onRequestNotifications,
                onContinue = onContinue,
            )
        }
    }
}

/**
 * Step 1 — one promise, stated plainly, with exactly one button.
 */
@Composable
private fun DisclosureStep(onAccept: () -> Unit) {
    SetupHeadline(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.setup_privacy_heading),
        body = stringResource(R.string.setup_privacy_body),
    )
    PrivacyFactList()
    Spacer(Modifier.height(4.dp))
    VodiPrimaryButton(
        text = stringResource(R.string.setup_accept),
        onClick = onAccept,
        modifier = Modifier.testTag("setup_accept_disclosure"),
    )
    Text(
        text = stringResource(R.string.setup_time_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Step 2 — the only required permission, with a single primary action and live confirmation.
 */
@Composable
private fun AccessibilityStep(
    state: PermissionStatusSnapshot,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfoForRestrictedSettings: () -> Unit,
) {
    SetupHeadline(
        icon = Icons.Rounded.TouchApp,
        title = stringResource(R.string.setup_step_accessibility_title),
        body = stringResource(R.string.setup_step_accessibility_body),
    )
    VodiPrimaryButton(
        text = stringResource(R.string.setup_open_accessibility),
        onClick = onOpenAccessibilitySettings,
        modifier = Modifier.testTag("setup_open_accessibility"),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepBadge(done = state.accessibilityEnabled)
        Text(
            text = if (state.accessibilityEnabled) {
                stringResource(R.string.setup_done_title)
            } else {
                stringResource(R.string.setup_step_accessibility_waiting)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AnimatedVisibility(visible = state.restrictedSettingsLikelyBlocking) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("restricted_settings_helper"),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.setup_restricted_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Text(
                    text = stringResource(R.string.setup_restricted_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                VodiSecondaryButton(
                    text = stringResource(R.string.setup_restricted_cta),
                    onClick = onOpenAppInfoForRestrictedSettings,
                )
            }
        }
    }
}

/**
 * Step 3 — confirmation plus the single gesture the user should try right now.
 */
@Composable
private fun DoneStep(
    state: PermissionStatusSnapshot,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
) {
    SetupHeadline(
        icon = Icons.Rounded.Check,
        title = stringResource(R.string.setup_done_title),
        body = stringResource(R.string.setup_done_body),
    )
    VodiPrimaryButton(
        text = stringResource(R.string.setup_done_cta),
        onClick = onContinue,
        modifier = Modifier.testTag("setup_continue"),
    )
    if (!state.notificationsGranted) {
        OptionalRow(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.setup_step_notifications_title),
            body = stringResource(R.string.setup_step_notifications_body),
            actionLabel = stringResource(R.string.setup_allow),
            onAction = onRequestNotifications,
        )
    }
}

/** Large step illustration, headline and one explanatory paragraph. */
@Composable
private fun SetupHeadline(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(14.dp)
                    .size(30.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Two-line privacy summary: what is never read, and what never leaves the device.
 */
@Composable
private fun PrivacyFactList() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrivacyFact(stringResource(R.string.setup_privacy_bullet_never), emphasised = true)
        PrivacyFact(stringResource(R.string.setup_privacy_bullet_always), emphasised = false)
    }
}

@Composable
private fun PrivacyFact(text: String, emphasised: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(
                    color = if (emphasised) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Completion indicator: filled check when done, hollow ring when pending. */
@Composable
private fun StepBadge(done: Boolean) {
    val description = if (done) {
        stringResource(R.string.setup_done_title)
    } else {
        stringResource(R.string.status_needs_setup)
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
                if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Dismissible, clearly-optional row used for non-blocking permissions. */
@Composable
private fun OptionalRow(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.label_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Formats a step number as a Persian numeral for `آیتم از آیتم` copy.
 */
@Composable
private fun faNumber(value: Int): String {
    // Reading the layout direction keeps the call site honest about RTL rendering.
    @Suppress("UNUSED_VARIABLE")
    val direction = LocalLayoutDirection.current
    return PersianText.formatInteger(value.toLong()).let { digits ->
        if (direction == LayoutDirection.Rtl) digits else digits
    }
}
