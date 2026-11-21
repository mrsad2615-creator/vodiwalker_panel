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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt

/**
 * OBSIDIAN SPECTRUM — MRSAD FOCUS SPACE (Master Prompt §12).
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
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Timer works regardless of permission. */ }

    AmbientBackground(PageEnvironment.FOCUS) {
        Column(
            Modifier
                .testTag(MrsadTestTags.FOCUS_ROOT)
                .fillMaxSize(),
        ) {
            ObsidianCollapsingTopBar(
                title = "FOCUS SPACE",
                subtitle = "تایمر تمرکز عمیق با زمان‌سنج دقیق",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Aqua,
                onBack = onBack,
                leading = {
                    MetalIcon(
                        MrsadIcons.Focus,
                        accentOverride = ObsidianSpectrum.Aqua,
                        modifier = Modifier.size(if (isCollapsed) 24.dp else 30.dp),
                    )
                },
                trailing = {
                    MrsadChip(text = state.phaseLabelFa, tint = ObsidianSpectrum.Aqua)
                },
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            ) {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))

                Box(
                    Modifier
                        .testTag(MrsadTestTags.FOCUS_DIAL)
                        .fillMaxWidth()
                        .height(290.dp),
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
                            color = ObsidianSpectrum.Aqua,
                        )
                        if (state.isPaused) {
                            Text("متوقف", style = type.caption, color = ObsidianSpectrum.Pink)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                    if (!state.isRunning) {
                        MrsadPrimaryButton(
                            text = "شروع",
                            icon = Icons.Outlined.PlayArrow,
                            accentColor = ObsidianSpectrum.Aqua,
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
                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.Aqua,
                    ) {
                        SectionHeader(title = "تنظیم طول جلسه")
                        ObsidianSegmentedBar(
                            items = listOf(25, 45, 60).map { minutes ->
                                SegmentedBarItem(
                                    id = minutes.toString(),
                                    label = "${formatInt(minutes, settings.persianNumerals)} دقیقه",
                                    accent = ObsidianSpectrum.Aqua,
                                )
                            },
                            selectedId = state.focusMinutes.toString(),
                            onSelect = { id ->
                                val minutes = id.toIntOrNull() ?: 25
                                viewModel.configure(minutes, if (minutes >= 45) 10 else 5, 1)
                            },
                        )
                        Text(
                            "استراحت بین دورها: ${formatInt(state.breakMinutes, settings.persianNumerals)} دقیقه",
                            style = type.caption,
                            color = colors.textMuted,
                        )
                    }
                }

                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.BrightBlue,
                ) {
                    SectionHeader(title = "آمار تمرکز")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricValue(
                            value = formatInt(state.focusMinutesToday, settings.persianNumerals),
                            label = "دقیقهٔ تمرکز امروز",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.Aqua,
                        )
                        MetricValue(
                            value = formatInt(state.completedToday, settings.persianNumerals),
                            label = "جلسهٔ امروز",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.BrightBlue,
                        )
                        MetricValue(
                            value = formatInt(state.totalSessions, settings.persianNumerals),
                            label = "کل جلسات",
                            size = type.headline.fontSize,
                            accent = ObsidianSpectrum.Lavender,
                        )
                    }
                    Text(
                        "زمان‌سنج از ساعت یکنواخت سیستم استفاده می‌کند؛ قفل صفحه یا رفتن برنامه به پس‌زمینه باعث خطا در زمان نمی‌شود.",
                        style = type.caption,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun FocusDial(state: FocusUiState) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
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

    Canvas(Modifier.size(256.dp)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = colors.surfaceHigh,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val sweep = (state.progress.coerceIn(0f, 1f)) * 360f
        drawArc(
            brush = Brush.linearGradient(listOf(ObsidianSpectrum.Teal, ObsidianSpectrum.Aqua)),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
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
                color = ObsidianSpectrum.Aqua,
                radius = 5.dp.toPx(),
                center = Offset(
                    center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                    center.y + (kotlin.math.sin(angle) * radius).toFloat(),
                ),
            )
        }
    }
}

private fun formatClock(millis: Long, persianNumerals: Boolean): String {
    val totalSeconds = ((millis + 999) / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    return if (persianNumerals) dev.mrsad.core.text.PersianText.toPersianDigits(text) else text
}
