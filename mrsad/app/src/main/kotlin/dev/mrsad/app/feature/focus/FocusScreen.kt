package dev.mrsad.app.feature.focus

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt

/**
 * MRSAD FOCUS SPACE — a real, monotonic focus timer.
 *
 * The dial shows the *measured* remaining time from [FocusSession]; pause
 * freezes it; the phase engine moves focus → break → focus and persists the
 * completed session exactly once. Notifications are optional and permission
 * aware; the timer never depends on them.
 */
@Composable
fun FocusScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: () -> Unit,
) {
    val viewModel: FocusViewModel = viewModel(key = "focus", factory = FocusViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* The timer works regardless of the answer. */ }

    AmbientBackground(PageEnvironment.FOCUS) {
        Column(
            Modifier
                .testTag(MrsadTestTags.FOCUS_ROOT)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(MrsadTokens.TouchTarget)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "بازگشت", tint = colors.textPrimary)
                }
                Spacer(Modifier.size(MrsadTokens.Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text("FOCUS SPACE", style = type.headline.copy(letterSpacing = 1.sp), color = colors.textPrimary)
                    Text("تایمر تمرکز با زمان‌سنج دقیق", style = type.caption, color = colors.textSecondary)
                }
                MrsadChip(text = state.phaseLabelFa, tint = colors.metalMid)
            }

            // Dial
            Box(
                Modifier
                    .testTag(MrsadTestTags.FOCUS_DIAL)
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                FocusDial(state = state)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatClock(state.remainingMillis, settings.persianNumerals),
                        style = type.display.copy(fontSize = 52.sp, lineHeight = 62.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        "دور ${formatInt(state.round, settings.persianNumerals)} از ${formatInt(state.rounds, settings.persianNumerals)}",
                        style = type.label,
                        color = colors.textSecondary,
                    )
                    if (state.isPaused) {
                        Text("متوقف", style = type.caption, color = colors.warning)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                if (!state.isRunning) {
                    MrsadPrimaryButton(
                        text = "شروع",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.start()
                        },
                        modifier = Modifier
                            .testTag(MrsadTestTags.FOCUS_START)
                            .weight(1f),
                    )
                } else {
                    MrsadSecondaryButton(
                        text = if (state.isPaused) "ادامه" else "توقف موقت",
                        icon = if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        onClick = viewModel::togglePause,
                        modifier = Modifier.weight(1f),
                    )
                    MrsadSecondaryButton(
                        text = "پایان",
                        icon = Icons.Outlined.Stop,
                        onClick = viewModel::stop,
                        modifier = Modifier
                            .testTag(MrsadTestTags.FOCUS_STOP)
                            .weight(1f),
                    )
                }
            }

            if (!state.isRunning) {
                MrsadSurface(Modifier.fillMaxWidth()) {
                    SectionHeader(title = "تنظیم طول جلسه")
                    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                        listOf(25, 45, 60).forEach { minutes ->
                            PresetChip(
                                label = "${formatInt(minutes, settings.persianNumerals)} دقیقه تمرکز",
                                selected = state.focusMinutes == minutes,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.configure(minutes, if (minutes >= 45) 10 else 5, 1) },
                            )
                        }
                    }
                    Text("استراحت بین دورها: ${formatInt(state.breakMinutes, settings.persianNumerals)} دقیقه", style = type.caption, color = colors.textMuted)
                }
            }

            MrsadSurface(Modifier.fillMaxWidth()) {
                SectionHeader(title = "آمار تمرکز")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricValue(
                        value = formatInt(state.focusMinutesToday, settings.persianNumerals),
                        label = "دقیقهٔ تمرکز امروز",
                        size = type.headline.fontSize,
                    )
                    MetricValue(
                        value = formatInt(state.completedToday, settings.persianNumerals),
                        label = "جلسهٔ امروز",
                        size = type.headline.fontSize,
                    )
                    MetricValue(
                        value = formatInt(state.totalSessions, settings.persianNumerals),
                        label = "کل جلسات",
                        size = type.headline.fontSize,
                    )
                }
                Text(
                    "زمان‌سنج از ساعت یکنواخت سیستم استفاده می‌کند؛ قفل صفحه یا رفتن برنامه به پس‌زمینه باعث خطا در زمان نمی‌شود.",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun FocusDial(state: FocusUiState) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    // The running dial spins forever; a deterministic test must not depend on it.
    val deterministic = MrsadTestHooks.isDeterministic
    val rotation = if (!deterministic && motion.animationsEnabled && state.isRunning && !state.isPaused) {
        val transition = rememberInfiniteTransition(label = "dial")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
            label = "dialRotation",
        )
        value
    } else {
        0f
    }

    Canvas(Modifier.size(260.dp)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // track
        drawArc(
            color = colors.surfaceHigh,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // progress from the real session state
        val sweep = (state.progress.coerceIn(0f, 1f)) * 360f
        drawArc(
            brush = Brush.sweepGradient(listOf(colors.metalDark, colors.metalLight, colors.accent)),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // phase tick marks
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - stroke * 2.1f
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6.0) - 90.0)
            val inner = if (i % 5 == 0) radius - 8.dp.toPx() else radius - 4.dp.toPx()
            drawLine(
                color = colors.border,
                start = Offset(
                    center.x + (kotlin.math.cos(angle) * inner).toFloat(),
                    center.y + (kotlin.math.sin(angle) * inner).toFloat(),
                ),
                end = Offset(
                    center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                    center.y + (kotlin.math.sin(angle) * radius).toFloat(),
                ),
                strokeWidth = 1f,
            )
        }
        if (state.isRunning) {
            val angle = Math.toRadians(rotation - 90.0)
            drawCircle(
                color = colors.accent,
                radius = 4.dp.toPx(),
                center = Offset(
                    center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                    center.y + (kotlin.math.sin(angle) * radius).toFloat(),
                ),
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.5f))
            .border(1.dp, if (selected) colors.borderStrong else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MrsadTheme.type.caption,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun formatClock(millis: Long, persianNumerals: Boolean): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    return if (persianNumerals) dev.mrsad.core.text.PersianText.toPersianDigits(text) else text
}
