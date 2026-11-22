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
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.audio.AmbientSoundscape
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
import dev.mrsad.app.ui.components.ObsidianIconBox
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.core.session.FocusSession
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * OBSIDIAN SPECTRUM — Flagship Focus Space & 44.1 kHz Studio Soundscape Console.
 */
@Composable
fun FocusScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onBack: () -> Unit,
) {
    val viewModel: FocusViewModel = viewModel(
        key = "focus",
        factory = FocusViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollState.value > 28 } }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Timer and studio audio run regardless of notification permission. */ }

    val phaseAccent = when {
        state.mode == FocusTimerMode.FLOWTIME -> ObsidianSpectrum.BrightBlue
        state.phase == FocusSession.Phase.BREAK -> ObsidianSpectrum.Lavender
        state.phase == FocusSession.Phase.DONE -> ObsidianSpectrum.Teal
        else -> ObsidianSpectrum.Aqua
    }

    AmbientBackground(PageEnvironment.FOCUS) {
        Column(
            Modifier
                .testTag(MrsadTestTags.FOCUS_ROOT)
                .fillMaxSize(),
        ) {
            ObsidianCollapsingTopBar(
                title = "فضای تمرکز عمیق و استودیوی صوتی",
                subtitle = "تایمر علمی چندحالته و سنتز صوتی استریو ۴۴.۱ کیلوهرتز",
                collapsed = isCollapsed,
                spectralColor = phaseAccent,
                onBack = onBack,
                leading = {
                    ObsidianIconBox(
                        size = if (isCollapsed) 38.dp else 48.dp,
                        accent = phaseAccent,
                    ) {
                        MetalIcon(
                            MrsadIcons.Focus,
                            accentOverride = phaseAccent,
                            modifier = Modifier.size(if (isCollapsed) 20.dp else 24.dp),
                        )
                    }
                },
                trailing = {
                    MrsadChip(
                        text = if (state.isAmbientPlaying) "صدا فعال • ${state.phaseLabelFa}" else state.phaseLabelFa,
                        tint = phaseAccent,
                        compact = true,
                    )
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
                Spacer(Modifier.height(MrsadTokens.Spacing.xxs))

                // Mode Switcher (Protocol Countdown vs Open Flowtime Stopwatch)
                if (!state.isRunning) {
                    ObsidianSegmentedBar(
                        items = FocusTimerMode.entries.map { mode ->
                            SegmentedBarItem(
                                id = mode.id,
                                label = mode.titleFa,
                                accent = if (mode == FocusTimerMode.PROTOCOL) ObsidianSpectrum.Aqua else ObsidianSpectrum.BrightBlue,
                            )
                        },
                        selectedId = state.mode.id,
                        onSelect = { id ->
                            FocusTimerMode.entries.firstOrNull { it.id == id }?.let(viewModel::setMode)
                        },
                    )
                }

                // Live status feedback banner
                state.statusBannerFa?.let { banner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(phaseAccent.copy(alpha = 0.14f))
                            .border(1.dp, phaseAccent.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = banner,
                            style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = phaseAccent,
                        )
                    }
                }

                // Flagship Orbital Dial + Acoustic Resonance Visualizer
                Box(
                    Modifier
                        .testTag(MrsadTestTags.FOCUS_DIAL)
                        .fillMaxWidth()
                        .height(288.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusDial(state = state, accent = phaseAccent)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                formatClock(state.displayClockMillis, settings.persianNumerals),
                                style = type.display.copy(fontSize = 52.sp, lineHeight = 60.sp),
                                color = colors.textPrimary,
                            )
                        }
                        if (state.mode == FocusTimerMode.PROTOCOL) {
                            Text(
                                "دور ${formatInt(state.round, settings.persianNumerals)} از ${formatInt(state.rounds, settings.persianNumerals)} • ${state.preset.titleFa}",
                                style = type.label,
                                color = phaseAccent,
                            )
                            RoundDotsRow(
                                currentRound = state.round,
                                totalRounds = state.rounds,
                                accent = phaseAccent,
                            )
                        } else {
                            Text(
                                "کرنومتر آزاد فلو-تایم • ثبت خودکار پس از پایان",
                                style = type.label,
                                color = ObsidianSpectrum.BrightBlue,
                            )
                        }
                        if (state.isPaused) {
                            MrsadChip(text = "متوقف موقت", tint = ObsidianSpectrum.Pink, compact = true)
                        } else if (state.isAmbientPlaying) {
                            MrsadChip(
                                text = "♪ ${state.selectedSoundscape.titleFa}",
                                tint = ObsidianSpectrum.Teal,
                                compact = true,
                            )
                        }
                    }
                }

                // Primary Timer & Immediate Audio Controls
                if (!state.isRunning) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                    ) {
                        MrsadPrimaryButton(
                            text = "شروع تمرکز",
                            icon = Icons.Outlined.PlayArrow,
                            accentColor = phaseAccent,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.start()
                            },
                            modifier = Modifier
                                .testTag(MrsadTestTags.FOCUS_START)
                                .weight(1.25f),
                        )
                        MrsadSecondaryButton(
                            text = if (state.isAmbientPlaying) "توقف صدا" else "پخش صدا",
                            onClick = viewModel::toggleAmbientPlayback,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                    ) {
                        MrsadSecondaryButton(
                            text = if (state.isPaused) "ادامه" else "توقف موقت",
                            icon = if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            onClick = viewModel::togglePause,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.mode == FocusTimerMode.PROTOCOL) {
                            MrsadSecondaryButton(
                                text = "مرحلهٔ بعد",
                                onClick = viewModel::skipPhase,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        MrsadSecondaryButton(
                            text = if (state.mode == FocusTimerMode.FLOWTIME) "پایان و ثبت" else "پایان",
                            icon = Icons.Outlined.Stop,
                            onClick = viewModel::stop,
                            modifier = Modifier
                                .testTag(MrsadTestTags.FOCUS_STOP)
                                .weight(1f),
                        )
                    }
                }

                // =============================================================
                // 1. STUDIO SOUNDSCAPE & BRAINWAVE SYNTHESIZER CONSOLE
                // =============================================================
                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.Teal,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "استودیوی صوتی و امواج تمرکز",
                                style = type.title,
                                color = colors.textPrimary,
                            )
                            Text(
                                "تولید بلادرنگ استریو ۴۴.۱ کیلوهرتز با فیلتر گرم بدون نویز تیز • با لمس هر کارت فوراً پخش می‌شود",
                                style = type.caption,
                                color = colors.textSecondary,
                            )
                        }
                        MrsadChip(
                            text = if (state.isAmbientPlaying) "در حال پخش" else "آماده",
                            tint = if (state.isAmbientPlaying) ObsidianSpectrum.Teal else colors.textMuted,
                            compact = true,
                        )
                    }

                    // Master Volume & Acoustic Controls
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.background.copy(alpha = 0.55f))
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .padding(MrsadTokens.Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "حجم صدای استودیو",
                                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                            )
                            MrsadChip(
                                text = "${formatInt((state.ambientVolume * 100).roundToInt(), settings.persianNumerals)}٪",
                                tint = ObsidianSpectrum.Aqua,
                                compact = true,
                            )
                        }
                        Slider(
                            value = state.ambientVolume,
                            onValueChange = viewModel::setAmbientVolume,
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ObsidianSpectrum.Aqua,
                                activeTrackColor = ObsidianSpectrum.Teal,
                                inactiveTrackColor = colors.surfaceHigh,
                            ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                        ) {
                            MrsadSecondaryButton(
                                text = if (state.tickSoundEnabled) "تیک‌تاک ساعت: روشن" else "تیک‌تاک ساعت: خاموش",
                                onClick = viewModel::toggleTickSound,
                                modifier = Modifier.weight(1f),
                            )
                            MrsadSecondaryButton(
                                text = "تست زنگ هارمونیک",
                                onClick = viewModel::previewChime,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Soundscape Cards in Strict RTL Grid (48.dp Icon Right, Title/Subtitle Center, Badge Left)
                    val soundscapes = remember {
                        listOf(
                            AmbientSoundscape.BROWN_WARM,
                            AmbientSoundscape.RAIN_CANOPY,
                            AmbientSoundscape.ALPHA_FOCUS,
                            AmbientSoundscape.GAMMA_DEEP,
                            AmbientSoundscape.OCEAN_TIDE,
                            AmbientSoundscape.TIBETAN_BOWL,
                            AmbientSoundscape.OFF,
                        )
                    }
                    soundscapes.forEach { soundscape ->
                        val isSelected = state.selectedSoundscape == soundscape
                        val isPlayingThis = isSelected && state.isAmbientPlaying && soundscape != AmbientSoundscape.OFF
                        SoundscapeCardRtl(
                            soundscape = soundscape,
                            selected = isSelected,
                            playing = isPlayingThis,
                            onClick = {
                                if (isPlayingThis) {
                                    viewModel.toggleAmbientPlayback()
                                } else {
                                    viewModel.selectSoundscape(soundscape, playImmediately = true)
                                }
                            },
                        )
                    }
                }

                // =============================================================
                // 2. SCIENTIFIC FOCUS PROTOCOLS & CUSTOM DURATION CONSOLE
                // =============================================================
                if (!state.isRunning && state.mode == FocusTimerMode.PROTOCOL) {
                    MrsadSurface(
                        Modifier.fillMaxWidth(),
                        accent = ObsidianSpectrum.Aqua,
                    ) {
                        SectionHeader(
                            title = "پروتکل‌های علمی تمرکز",
                            subtitle = "انتخاب سریع چرخه‌های استاندارد یا تنظیم دستی زمان و تعداد دور",
                        )
                        val presets = remember {
                            listOf(
                                FocusProtocolPreset.POMODORO,
                                FocusProtocolPreset.DEEP_WORK,
                                FocusProtocolPreset.ULTRADIAN,
                                FocusProtocolPreset.SPRINT,
                            )
                        }
                        presets.forEach { preset ->
                            ProtocolPresetRowRtl(
                                preset = preset,
                                selected = state.preset == preset,
                                onClick = { viewModel.selectPreset(preset) },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        SectionHeader(title = "تنظیم دقیق زمان و دورها")

                        // Focus duration stepper
                        CustomStepperRow(
                            title = "مدت هر دور تمرکز",
                            valueText = "${formatInt(state.focusMinutes, settings.persianNumerals)} دقیقه",
                            minusLabel = "-۵ دقیقه",
                            plusLabel = "+۵ دقیقه",
                            accent = ObsidianSpectrum.Aqua,
                            onMinus = { viewModel.adjustFocusMinutes(-5) },
                            onPlus = { viewModel.adjustFocusMinutes(5) },
                        )

                        // Break duration stepper
                        CustomStepperRow(
                            title = "استراحت بین دورها",
                            valueText = "${formatInt(state.breakMinutes, settings.persianNumerals)} دقیقه",
                            minusLabel = "-۱ دقیقه",
                            plusLabel = "+۱ دقیقه",
                            accent = ObsidianSpectrum.Lavender,
                            onMinus = { viewModel.adjustBreakMinutes(-1) },
                            onPlus = { viewModel.adjustBreakMinutes(1) },
                        )

                        // Rounds count stepper
                        CustomStepperRow(
                            title = "تعداد دورهای متوالی",
                            valueText = "${formatInt(state.rounds, settings.persianNumerals)} دور",
                            minusLabel = "-۱ دور",
                            plusLabel = "+۱ دور",
                            accent = ObsidianSpectrum.BrightBlue,
                            onMinus = { viewModel.adjustRounds(-1) },
                            onPlus = { viewModel.adjustRounds(1) },
                        )
                    }
                }

                // =============================================================
                // 3. FOCUS ANALYTICS & BACKGROUND PERSISTENCE GUARANTEE
                // =============================================================
                MrsadSurface(
                    Modifier.fillMaxWidth(),
                    accent = ObsidianSpectrum.BrightBlue,
                ) {
                    SectionHeader(title = "کارنامهٔ تمرکز عمیق")
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
                        "تایمر و استودیوی صوتی در سطح برنامه فعال می‌مانند؛ هنگام مراجعه به سایر بخش‌های مرصاد یا خاموش شدن صفحه، زمان‌سنج و صدای محیطی بدون وقفه ادامه می‌یابند.",
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
private fun SoundscapeCardRtl(
    soundscape: AmbientSoundscape,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val accent = when (soundscape) {
        AmbientSoundscape.BROWN_WARM -> ObsidianSpectrum.Aqua
        AmbientSoundscape.RAIN_CANOPY -> ObsidianSpectrum.BrightBlue
        AmbientSoundscape.ALPHA_FOCUS -> ObsidianSpectrum.Teal
        AmbientSoundscape.GAMMA_DEEP -> ObsidianSpectrum.Violet
        AmbientSoundscape.OCEAN_TIDE -> ObsidianSpectrum.Lavender
        AmbientSoundscape.TIBETAN_BOWL -> ObsidianSpectrum.Pink
        AmbientSoundscape.OFF -> colors.textMuted
    }
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else colors.background.copy(alpha = 0.45f),
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) accent else colors.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
    ) {
        // Right side in RTL: Fixed 48.dp ObsidianIconBox
        ObsidianIconBox(
            size = 48.dp,
            accent = accent,
        ) {
            Text(
                text = when (soundscape) {
                    AmbientSoundscape.BROWN_WARM -> "≈"
                    AmbientSoundscape.RAIN_CANOPY -> "⋮⋮"
                    AmbientSoundscape.ALPHA_FOCUS -> "α"
                    AmbientSoundscape.GAMMA_DEEP -> "γ"
                    AmbientSoundscape.OCEAN_TIDE -> "∿"
                    AmbientSoundscape.TIBETAN_BOWL -> "◎"
                    AmbientSoundscape.OFF -> "×"
                },
                style = type.title.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }

        // Center in RTL: Title & full description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = soundscape.titleFa,
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = soundscape.subtitleFa,
                style = type.caption,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Left side in RTL: Status / Frequency chip
        MrsadChip(
            text = if (playing) "در حال پخش" else soundscape.badgeFa,
            tint = if (selected) accent else colors.textMuted,
            compact = true,
        )
    }
}

@Composable
private fun ProtocolPresetRowRtl(
    preset: FocusProtocolPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val accent = when (preset) {
        FocusProtocolPreset.POMODORO -> ObsidianSpectrum.Aqua
        FocusProtocolPreset.DEEP_WORK -> ObsidianSpectrum.BrightBlue
        FocusProtocolPreset.ULTRADIAN -> ObsidianSpectrum.Violet
        FocusProtocolPreset.SPRINT -> ObsidianSpectrum.Teal
        FocusProtocolPreset.CUSTOM -> ObsidianSpectrum.Lavender
    }
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else colors.background.copy(alpha = 0.45f),
            )
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                color = if (selected) accent else colors.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
    ) {
        // Right side in RTL: Fixed 48.dp ObsidianIconBox
        ObsidianIconBox(
            size = 48.dp,
            accent = accent,
        ) {
            MetalIcon(
                MrsadIcons.Focus,
                accentOverride = accent,
                modifier = Modifier.size(22.dp),
            )
        }

        // Center in RTL: Title & Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = preset.titleFa,
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = preset.subtitleFa,
                style = type.caption,
                color = colors.textSecondary,
            )
        }

        // Left side in RTL: Duration & Rounds Chip
        MrsadChip(
            text = preset.badgeFa,
            tint = if (selected) accent else colors.textMuted,
            compact = true,
        )
    }
}

@Composable
private fun CustomStepperRow(
    title: String,
    valueText: String,
    minusLabel: String,
    plusLabel: String,
    accent: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background.copy(alpha = 0.45f))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = type.body, color = colors.textPrimary)
            MrsadChip(text = valueText, tint = accent, compact = true)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        ) {
            MrsadSecondaryButton(
                text = minusLabel,
                onClick = onMinus,
                modifier = Modifier.weight(1f),
            )
            MrsadSecondaryButton(
                text = plusLabel,
                onClick = onPlus,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoundDotsRow(
    currentRound: Int,
    totalRounds: Int,
    accent: Color,
) {
    val colors = MrsadTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..totalRounds.coerceIn(1, 8)) {
            val active = i <= currentRound
            Box(
                Modifier
                    .size(if (i == currentRound) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (active) accent else colors.border),
            )
        }
    }
}

@Composable
private fun FocusDial(
    state: FocusUiState,
    accent: Color,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val deterministic = MrsadTestHooks.isDeterministic
    val animateDial = !deterministic && motion.animationsEnabled &&
        ((state.isRunning && !state.isPaused) || state.isAmbientPlaying)

    val rotation = if (animateDial) {
        val transition = rememberInfiniteTransition(label = "dial")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "dialRotation",
        )
        value
    } else {
        0f
    }

    Canvas(Modifier.size(268.dp)) {
        val stroke = 11.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val center = Offset(size.width / 2, size.height / 2)

        // Outer track ring
        drawArc(
            color = colors.surfaceHigh,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Inner acoustic resonance ring when studio audio is playing
        if (state.isAmbientPlaying) {
            val wavePulse = (sin(Math.toRadians(rotation.toDouble() * 4.0)).toFloat() + 1f) * 0.5f
            val innerInset = stroke * 2.5f
            val innerSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)
            drawArc(
                color = ObsidianSpectrum.Teal.copy(alpha = 0.16f + 0.16f * wavePulse),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Primary progress arc
        val sweep = (state.progress.coerceIn(0f, 1f)) * 360f
        drawArc(
            brush = Brush.linearGradient(
                listOf(ObsidianSpectrum.Teal, accent, ObsidianSpectrum.BrightBlue),
            ),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // 60 Horological bezel ticks
        val radius = size.minDimension / 2 - stroke * 1.9f
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6.0) - 90.0)
            val isMajor = i % 5 == 0
            val inner = if (isMajor) radius - 9.dp.toPx() else radius - 4.dp.toPx()
            drawLine(
                color = if (isMajor) accent.copy(alpha = 0.55f) else colors.border,
                start = Offset(
                    center.x + (cos(angle) * inner).toFloat(),
                    center.y + (sin(angle) * inner).toFloat(),
                ),
                end = Offset(
                    center.x + (cos(angle) * radius).toFloat(),
                    center.y + (sin(angle) * radius).toFloat(),
                ),
                strokeWidth = if (isMajor) 2f else 1f,
            )
        }

        // Orbital satellite indicator
        if (state.isRunning || state.isAmbientPlaying) {
            val angle = Math.toRadians(rotation - 90.0)
            val satCenter = Offset(
                center.x + (cos(angle) * radius).toFloat(),
                center.y + (sin(angle) * radius).toFloat(),
            )
            drawCircle(
                color = accent.copy(alpha = 0.28f),
                radius = 9.dp.toPx(),
                center = satCenter,
            )
            drawCircle(
                color = accent,
                radius = 4.5.dp.toPx(),
                center = satCenter,
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
