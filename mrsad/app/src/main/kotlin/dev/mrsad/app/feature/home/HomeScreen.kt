package dev.mrsad.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.navigation.Routes
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.IconFacet
import dev.mrsad.app.ui.components.LiveDot
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadMarkView
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadReveal
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianIconBox
import dev.mrsad.app.ui.components.ObsidianSegmentedBar
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SegmentedBarItem
import dev.mrsad.app.ui.components.SparkLine
import dev.mrsad.app.ui.components.StreakCalendarMatrix
import dev.mrsad.app.ui.components.rememberRevealProgress
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.ModulePalettes
import dev.mrsad.app.ui.theme.MrsadDuration
import dev.mrsad.app.ui.theme.MrsadEasing
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import dev.mrsad.core.coach.Module
import kotlin.math.PI
import kotlin.math.sin

/**
 * OBSIDIAN SPECTRUM — Flagship Dashboard & Command Center (Master Prompt §10).
 *
 * Features:
 * 1. Pinned Auto-Collapsing Top Bar (`HomeAutoCollapsingTopBar`) that smoothly
 *    compacts when scrolling down and expands when scrolling back to the top.
 * 2. Obsidian Spectrum Daily Performance & Multi-Spectral Activity Wave Hero
 *    (`MrsadTestTags.HOME_CORE`).
 * 3. Four Main Training Modules (`TYPING` Electric Blue, `READING` Deep Violet,
 *    `EYE` Teal/Aqua, `BRAIN` Magenta/Pink) + unified module bar (`HOME_MODULES`).
 * 4. Separate Dashboard Sections organized inside a unified `ObsidianSegmentedBar`
 *    (`برنامه و مأموریت` | `نمودار و استمرار` | `رکوردها و نشان‌ها`).
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    settings: MrsadSettings,
    onNavigate: (String) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        key = "home",
        factory = HomeViewModel.Factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val reveal = rememberRevealProgress()
    val listState = rememberLazyListState()
    var selectedSectionTab by rememberSaveable { mutableStateOf("plan") }

    AmbientBackground(PageEnvironment.HOME) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MrsadTokens.Spacing.md)
                    .padding(bottom = LocalBottomBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
                contentPadding = PaddingValues(
                    top = 74.dp,
                    bottom = MrsadTokens.Spacing.lg,
                ),
            ) {
                // 01: Obsidian Spectrum Daily Performance & Multi-Spectral Activity Wave Hero
                item(key = "hero") {
                    MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 0) {
                        ObsidianSpectrumHero(
                            state = state,
                            persianNumerals = settings.persianNumerals,
                        )
                    }
                }

                // 02: Four Main Training Modules — Module-Specific Spectral Identities
                item(key = "pillars_header") {
                    MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 1) {
                        SectionHeader(
                            title = "چهار ستون اصلی آموزش",
                            actionLabel = "تحلیل کامل",
                            onAction = { onNavigate(Routes.ANALYTICS) },
                        )
                    }
                }

                item(key = "pillars_grid") {
                    MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                                PillarSculptureCard(
                                    modifier = Modifier.weight(1f),
                                    indexCode = "۰۱ · تایپ",
                                    title = "سرعت تایپ",
                                    subtitle = state.bestWpm?.let { "رکورد: ${formatInt(it, settings.persianNumerals)} کلمه/دقیقه" }
                                        ?: "موتور استاندارد WPM",
                                    sculptureType = ModuleSculptureType.TYPING,
                                    spectralAccent = ModulePalettes.Typing.luminous,
                                    onClick = { onNavigate(Routes.TYPE) },
                                )
                                PillarSculptureCard(
                                    modifier = Modifier.weight(1f),
                                    indexCode = "۰۲ · خوانش",
                                    title = "تندخوانی و درک",
                                    subtitle = state.bestReadingWpm?.let { "رکورد: ${formatInt(it, settings.persianNumerals)} کلمه/دقیقه" }
                                        ?: "خوانش کانونی و درک مطلب",
                                    sculptureType = ModuleSculptureType.READING,
                                    spectralAccent = ModulePalettes.Reading.luminous,
                                    onClick = { onNavigate(Routes.READ) },
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                                PillarSculptureCard(
                                    modifier = Modifier.weight(1f),
                                    indexCode = "۰۳ · چشم",
                                    title = "ورزش چشم",
                                    subtitle = if (state.eyeSessions > 0) {
                                        "${formatInt(state.eyeSessions, settings.persianNumerals)} جلسه • دقت ${formatInt(state.avgEyeScore, settings.persianNumerals)}٪"
                                    } else {
                                        "ساکاد، تعقیب و جدول شولته"
                                    },
                                    sculptureType = ModuleSculptureType.EYE,
                                    spectralAccent = ModulePalettes.Eye.luminous,
                                    onClick = { onNavigate(Routes.EYE) },
                                )
                                PillarSculptureCard(
                                    modifier = Modifier.weight(1f),
                                    indexCode = "۰۴ · ذهن",
                                    title = "تمرین ذهن",
                                    subtitle = if (state.mindSessions > 0) {
                                        "${formatInt(state.mindSessions, settings.persianNumerals)} جلسه • امتیاز ${formatInt(state.avgMindScore, settings.persianNumerals)}٪"
                                    } else {
                                        "حافظه، تمرکز، منطق و واکنش"
                                    },
                                    sculptureType = ModuleSculptureType.BRAIN,
                                    spectralAccent = ModulePalettes.Brain.luminous,
                                    onClick = { onNavigate(Routes.MIND) },
                                )
                            }
                        }
                    }
                }

                // 03: Integrated Quick-Access Module Bar (`HOME_MODULES`)
                item(key = "modules_bar") {
                    MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 3) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                                .background(colors.backgroundElevated)
                                .border(1.dp, colors.border, RoundedCornerShape(MrsadTokens.Radius.lg))
                                .padding(MrsadTokens.Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "نوار دسترسی سریع ابزارها",
                                    style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textSecondary,
                                )
                                Text(
                                    "۶ محیط تخصصی",
                                    style = type.caption,
                                    color = colors.textMuted,
                                )
                            }
                            LazyRow(
                                modifier = Modifier.testTag(MrsadTestTags.HOME_MODULES),
                                horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                            ) {
                                items(moduleEntries, key = { it.route }) { entry ->
                                    ModuleBarPill(entry = entry, onClick = { onNavigate(entry.route) })
                                }
                            }
                        }
                    }
                }

                // 04: Separate Dashboard Sections Inside a Unified Bar (`ObsidianSegmentedBar`)
                item(key = "sections_bar") {
                    MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 4) {
                        Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm)) {
                            ObsidianSegmentedBar(
                                items = listOf(
                                    SegmentedBarItem(
                                        id = "plan",
                                        label = "برنامه و مأموریت",
                                        accent = ObsidianSpectrum.BrightBlue,
                                        icon = MrsadIcons.Target,
                                    ),
                                    SegmentedBarItem(
                                        id = "trends",
                                        label = "نمودار و استمرار",
                                        accent = ObsidianSpectrum.Aqua,
                                        icon = MrsadIcons.Analytics,
                                    ),
                                    SegmentedBarItem(
                                        id = "records",
                                        label = "رکوردها و نشان‌ها",
                                        accent = ObsidianSpectrum.Pink,
                                        icon = MrsadIcons.Trophy,
                                    ),
                                ),
                                selectedId = selectedSectionTab,
                                onSelect = { selectedSectionTab = it },
                            )

                            AnimatedContent(
                                targetState = selectedSectionTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = motion.effectsDefault()) togetherWith
                                        fadeOut(animationSpec = motion.effectsFast())
                                },
                                label = "dashboardSectionContent",
                            ) { tab ->
                                when (tab) {
                                    "plan" -> DashboardPlanSection(
                                        state = state,
                                        persianNumerals = settings.persianNumerals,
                                        onNavigate = onNavigate,
                                    )
                                    "trends" -> DashboardTrendsSection(
                                        state = state,
                                        persianNumerals = settings.persianNumerals,
                                        onNavigate = onNavigate,
                                    )
                                    else -> DashboardRecordsSection(
                                        state = state,
                                        persianNumerals = settings.persianNumerals,
                                        onNavigate = onNavigate,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pinned Auto-Collapsing Flagship Top Bar (overlay inside Box so LazyColumn height never re-measures on scroll)
            HomeAutoCollapsingTopBar(
                state = state,
                listState = listState,
                persianNumerals = settings.persianNumerals,
                onOpenStudio = { onNavigate(Routes.STUDIO) },
                onOpenAnalytics = { onNavigate(Routes.ANALYTICS) },
                onOpenArena = { onNavigate(Routes.ARENA) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private data class ModuleEntry(
    val code: String,
    val title: String,
    val icon: List<IconFacet>,
    val route: String,
    val subtitle: String,
    val spectralAccent: Color,
)

private val moduleEntries = listOf(
    ModuleEntry("TYPE", "تایپ", MrsadIcons.Type, Routes.TYPE, "سرعت و دقت", ObsidianSpectrum.BrightBlue),
    ModuleEntry("READ", "خوانش", MrsadIcons.Read, Routes.READ, "تندخوانی و درک", ObsidianSpectrum.Lavender),
    ModuleEntry("EYE", "چشم", MrsadIcons.Eye, Routes.EYE, "مهارت دیداری", ObsidianSpectrum.Aqua),
    ModuleEntry("MIND", "ذهن", MrsadIcons.Mind, Routes.MIND, "تمرین شناختی", ObsidianSpectrum.Pink),
    ModuleEntry("FOCUS", "تمرکز", MrsadIcons.Focus, Routes.FOCUS, "زمان‌سنج عمیق", ObsidianSpectrum.Aqua),
    ModuleEntry("ANALYTICS", "آمار", MrsadIcons.Analytics, Routes.ANALYTICS, "تحلیل پیشرفت", ObsidianSpectrum.Lavender),
)

private fun iconFor(module: Module): List<IconFacet> = when (module) {
    Module.TYPE -> MrsadIcons.Type
    Module.READ -> MrsadIcons.Read
    Module.EYE -> MrsadIcons.Eye
    Module.MIND -> MrsadIcons.Mind
    Module.FOCUS -> MrsadIcons.Focus
}

private fun accentFor(module: Module): Color = when (module) {
    Module.TYPE -> ObsidianSpectrum.BrightBlue
    Module.READ -> ObsidianSpectrum.Lavender
    Module.EYE -> ObsidianSpectrum.Aqua
    Module.MIND -> ObsidianSpectrum.Pink
    Module.FOCUS -> ObsidianSpectrum.Aqua
}

/**
 * Pinned Auto-Collapsing Flagship Header on `HomeScreen`.
 *
 * Notice that `HOME_BRAND_MARK` sits in a non-clickable `Box` so Jetpack Compose
 * never merges its descendant semantics in `MrsadSmokeTest`.
 */
@Composable
private fun HomeAutoCollapsingTopBar(
    state: HomeState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    persianNumerals: Boolean,
    onOpenStudio: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenArena: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val levelInfo = state.progression?.levelInfo
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 36
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .background(colors.background.copy(alpha = 0.96f))
            .padding(
                horizontal = MrsadTokens.Spacing.md,
                vertical = if (collapsed) 4.dp else 6.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Right-most anchor in RTL: Fixed-dimension Brand Mark box (never wrapped in a clickable parent)
            Box(
                Modifier
                    .size(if (collapsed) 36.dp else 44.dp)
                    .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                    .background(colors.surfaceHigh)
                    .border(
                        1.dp,
                        ObsidianSpectrum.BrightBlue.copy(alpha = 0.45f),
                        RoundedCornerShape(MrsadTokens.Radius.md),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MrsadMarkView(
                    size = if (collapsed) 22.dp else 26.dp,
                    monochrome = false,
                    modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND_MARK),
                    contentDescription = "MRSAD",
                )
            }

            Spacer(Modifier.width(MrsadTokens.Spacing.xs))

            // Middle: Brand title and subtitle
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MRSAD",
                    style = if (collapsed) {
                        type.title.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    } else {
                        type.headline.copy(fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = 3.sp)
                    },
                    color = colors.textPrimary,
                    modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND),
                )
                if (!collapsed) {
                    Text(
                        text = "زیست‌بوم رشد شناختی • مرصاد",
                        style = type.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Left-most in RTL: Status & Quick-Access Pills
            if (levelInfo != null) {
                HeaderCompactPill(
                    text = "سطح ${formatInt(levelInfo.level, persianNumerals)}",
                    tint = ObsidianSpectrum.BrightBlue,
                    onClick = onOpenArena,
                )
                Spacer(Modifier.width(4.dp))
            }
            HeaderCompactPill(
                text = "آمار",
                tint = ObsidianSpectrum.Aqua,
                onClick = onOpenAnalytics,
            )
            if (!collapsed) {
                Spacer(Modifier.width(4.dp))
                HeaderCompactPill(
                    text = "استودیو",
                    tint = ObsidianSpectrum.Lavender,
                    onClick = onOpenStudio,
                )
            }
        }

        Box(
            Modifier
                .padding(top = 5.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(ObsidianSpectrum.BrightBlue.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun HeaderCompactPill(
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    Box(
        modifier = Modifier
            .heightIn(min = MrsadTokens.TouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.48f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MrsadTheme.type.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ObsidianSpectrumHero(
    state: HomeState,
    persianNumerals: Boolean,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = Modifier
            .testTag(MrsadTestTags.HOME_CORE)
            .fillMaxWidth(),
        elevated = true,
        accent = ObsidianSpectrum.BrightBlue,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiveDot(color = ObsidianSpectrum.Aqua)
                Text(
                    text = "عملکرد روزانه • طیف فعالیت زنده",
                    style = type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                    color = colors.textSecondary,
                )
            }
            MrsadChip(
                text = if (state.hasAnyData) "طیف فعال" else "آمادهٔ شروع",
                tint = if (state.hasAnyData) ObsidianSpectrum.Aqua else ObsidianSpectrum.BrightBlue,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = formatPercent(state.todayProgress * 100.0, persianNumerals),
                    style = type.display.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    text = "${formatInt(state.todayMinutes, persianNumerals)} از ${formatInt(25, persianNumerals)} دقیقه هدف روزانه",
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (state.hasAnyData) "توازن ۴ ستون شناختی" else "در انتظار اولین تمرین",
                    style = type.caption,
                    color = ObsidianSpectrum.Lavender,
                )
                Text(
                    text = "تایپ • خوانش • چشم • ذهن",
                    style = type.caption,
                    color = colors.textMuted,
                )
            }
        }

        MrsadProgressBar(
            progress = state.todayProgress,
            height = 5.dp,
            tint = ObsidianSpectrum.BrightBlue,
        )

        // Multi-Spectral Activity Wave
        ObsidianSpectrumWave(
            energy = state.energy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        // Unified Daily Performance Bar inside Hero
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.backgroundElevated)
                .border(1.dp, colors.border, RoundedCornerShape(MrsadTokens.Radius.md))
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell(
                modifier = Modifier.weight(1f),
                value = formatInt(state.todayMinutes, persianNumerals),
                label = "دقیقه امروز",
                icon = MrsadIcons.Focus,
                accent = ObsidianSpectrum.Aqua,
            )
            StatCell(
                modifier = Modifier.weight(1f),
                value = formatInt(state.todaySessions, persianNumerals),
                label = "تمرین امروز",
                icon = MrsadIcons.Trophy,
                accent = ObsidianSpectrum.Lavender,
            )
            StatCell(
                modifier = Modifier.weight(1f),
                value = formatInt(state.streakDays, persianNumerals),
                label = "روز پیوسته",
                icon = MrsadIcons.Flame,
                accent = ObsidianSpectrum.Pink,
            )
        }
    }
}

@Composable
private fun ObsidianSpectrumWave(
    energy: Float,
    modifier: Modifier = Modifier,
) {
    val motion = MrsadTheme.motion
    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }
    val fillPath = remember { Path() }

    val animate = !MrsadTestHooks.isDeterministic &&
        motion.animationsEnabled &&
        motion.quality != GraphicsQuality.PERFORMANCE

    val phaseState = if (animate) {
        val transition = rememberInfiniteTransition(label = "spectrum_wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wave_phase",
        )
    } else {
        null
    }

    val nodeSpectrum = remember {
        listOf(
            ObsidianSpectrum.BrightBlue,
            ObsidianSpectrum.Lavender,
            ObsidianSpectrum.Aqua,
            ObsidianSpectrum.Pink,
            ObsidianSpectrum.BrightBlue,
            ObsidianSpectrum.Lavender,
            ObsidianSpectrum.Aqua,
        )
    }

    Canvas(modifier = modifier.graphicsLayer()) {
        if (size.width <= 4f || size.height <= 4f) return@Canvas
        val phase = phaseState?.value ?: 0.95f
        val w = size.width
        val h = size.height
        val midY = h * 0.56f
        val amp = h * (0.20f + 0.14f * energy.coerceIn(0f, 1f))
        val steps = 24

        primaryPath.reset()
        secondaryPath.reset()
        fillPath.reset()

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = t * w
            val envelope = 0.55f + 0.45f * sin(t * PI.toFloat())
            val y1 = midY - sin(t * PI.toFloat() * 2.4f + phase) * amp * envelope
            val y2 = midY - sin(t * PI.toFloat() * 2.0f - phase * 0.8f + 0.9f) * (amp * 0.68f) * envelope
            if (i == 0) {
                primaryPath.moveTo(x, y1)
                secondaryPath.moveTo(x, y2)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y1)
            } else {
                primaryPath.lineTo(x, y1)
                secondaryPath.lineTo(x, y2)
                fillPath.lineTo(x, y1)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            color = ObsidianSpectrum.ElectricBlue.copy(alpha = 0.14f),
        )

        drawPath(
            path = secondaryPath,
            color = ObsidianSpectrum.Lavender.copy(alpha = 0.52f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
        )

        drawPath(
            path = primaryPath,
            color = ObsidianSpectrum.BrightBlue,
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round),
        )

        for (d in 0..6) {
            val t = (d + 0.5f) / 7f
            val x = t * w
            val y = midY - sin(t * PI.toFloat() * 2.4f + phase) * amp *
                (0.55f + 0.45f * sin(t * PI.toFloat()))
            val nodeColor = nodeSpectrum[d]
            drawCircle(
                color = nodeColor.copy(alpha = 0.36f),
                radius = 4.6.dp.toPx(),
                center = Offset(x, y),
            )
            drawCircle(
                color = ObsidianSpectrum.SoftWhite,
                radius = 2.0.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun PillarSculptureCard(
    modifier: Modifier = Modifier,
    indexCode: String,
    title: String,
    subtitle: String,
    sculptureType: ModuleSculptureType,
    spectralAccent: Color,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = modifier,
        accent = spectralAccent,
        onClick = onClick,
    ) {
        // Strict RTL Card Header: 48dp Icon Box on the Right (Start), Status Badge on the Left (End)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ObsidianIconBox(
                size = 48.dp,
                accent = spectralAccent,
                elevated = true,
            ) {
                ModuleSculpture3D(
                    type = sculptureType,
                    modifier = Modifier.size(38.dp),
                )
            }
            MrsadChip(
                text = indexCode,
                tint = spectralAccent,
                compact = true,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = type.title.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = type.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    icon: List<IconFacet>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MetalIcon(icon, accentOverride = accent, modifier = Modifier.size(18.dp))
        Text(
            value,
            style = type.metric.copy(fontSize = type.headline.fontSize, lineHeight = type.headline.lineHeight),
            color = colors.textPrimary,
        )
        Text(label, style = type.caption, color = colors.textMuted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ModuleBarPill(entry: ModuleEntry, onClick: () -> Unit) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
    Row(
        modifier = Modifier
            .testTag(MrsadTestTags.homeModule(entry.route))
            .clip(shape)
            .background(colors.surfaceHigh)
            .border(
                width = 1.dp,
                color = entry.spectralAccent.copy(alpha = 0.45f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
    ) {
        ObsidianIconBox(
            size = 38.dp,
            accent = entry.spectralAccent,
        ) {
            MetalIcon(
                facets = entry.icon,
                accentOverride = entry.spectralAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = entry.title,
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = entry.subtitle,
                style = type.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DashboardPlanSection(
    state: HomeState,
    persianNumerals: Boolean,
    onNavigate: (String) -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm)) {
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.BrightBlue,
        ) {
            SectionHeader(
                title = "برنامهٔ تمرین روزانه و مأموریت‌ها",
                actionLabel = "فضای تمرکز",
                onAction = { onNavigate(Routes.FOCUS) },
            )
            if (state.plan.isEmpty()) {
                Text(
                    "پس از اولین تمرین، برنامهٔ روزانه بر پایهٔ داده‌های واقعی شما در ۴ ستون مهارت ساخته می‌شود.",
                    style = type.body,
                    color = colors.textSecondary,
                )
            } else {
                Text("برنامهٔ پیشنهادی بر پایهٔ عملکرد ثبت‌شده در ۴ ستون مهارت", style = type.caption, color = colors.textMuted)
                state.plan.forEach { item ->
                    PlanRow(
                        title = item.titleFa,
                        reason = item.reasonFa,
                        minutesLabel = "${formatInt(item.minutes, persianNumerals)} دقیقه",
                        icon = iconFor(item.module),
                        accent = accentFor(item.module),
                        onClick = {
                            when (item.module) {
                                Module.TYPE -> onNavigate(Routes.TYPE)
                                Module.READ -> onNavigate(Routes.READ)
                                Module.EYE -> onNavigate(Routes.EYE)
                                Module.MIND -> onNavigate(Routes.MIND)
                                Module.FOCUS -> onNavigate(Routes.FOCUS)
                            }
                        },
                    )
                }
            }

            state.progression?.weeklyChallenges?.takeIf { it.isNotEmpty() }?.let { challenges ->
                MrsadDivider()
                Text("چالش‌های هفتگی", style = type.label, color = colors.textPrimary)
                challenges.forEach { ch ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(ch.titleFa, style = type.caption, color = colors.textSecondary)
                            Text(
                                "${formatInt(ch.current, persianNumerals)} / ${formatInt(ch.target, persianNumerals)} (+${formatInt(ch.rewardXp, persianNumerals)} XP)",
                                style = type.caption,
                                color = if (ch.completed) ObsidianSpectrum.Aqua else colors.textMuted,
                            )
                        }
                        MrsadProgressBar(
                            progress = ch.progressFraction,
                            height = 5.dp,
                            tint = if (ch.completed) ObsidianSpectrum.Aqua else ObsidianSpectrum.Lavender,
                        )
                    }
                }
            }
        }

        if (!state.hasAnyData) {
            MrsadSurface(
                modifier = Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Aqua,
            ) {
                Text("شروع سریع", style = type.title, color = colors.textPrimary)
                Text(
                    "برای فعال‌سازی نمودارها و آمار واقعی، یک تمرین تایپ ۶۰ ثانیه‌ای انجام دهید.",
                    style = type.body,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(MrsadTokens.Spacing.xxs))
                MrsadPrimaryButton(
                    text = "شروع تمرین تایپ",
                    icon = Icons.Outlined.Keyboard,
                    onClick = { onNavigate(Routes.TYPE) },
                    accentColor = ObsidianSpectrum.BrightBlue,
                    modifier = Modifier
                        .testTag(MrsadTestTags.HOME_PRIMARY_CTA)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DashboardTrendsSection(
    state: HomeState,
    persianNumerals: Boolean,
    onNavigate: (String) -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    MrsadSurface(
        modifier = Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Aqua,
    ) {
        SectionHeader(
            title = "نمودار پیشرفت هفتگی و تقویم استمرار",
            actionLabel = "جزئیات کامل",
            onAction = { onNavigate(Routes.ANALYTICS) },
        )
        SparkLine(
            values = state.weeklyMinutesSeries,
            tint = ObsidianSpectrum.Aqua,
            label = "دقایق تمرین در ۷ روز اخیر",
        )
        state.progression?.calendarDays?.takeIf { it.isNotEmpty() }?.let { calendar ->
            MrsadDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("تقویم استمرار ۲۸ روزه", style = type.caption, color = colors.textSecondary)
                Text(
                    "${formatInt(calendar.count { it.active }, persianNumerals)} روز فعال",
                    style = type.caption,
                    color = ObsidianSpectrum.Aqua,
                )
            }
            StreakCalendarMatrix(days = calendar)
        }
    }
}

@Composable
private fun DashboardRecordsSection(
    state: HomeState,
    persianNumerals: Boolean,
    onNavigate: (String) -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type

    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm)) {
        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Pink,
        ) {
            SectionHeader(title = "رکوردهای شخصی")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MrsadTokens.Spacing.xxs),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RecordCell(
                    value = state.bestWpm?.let { formatNumber(it.toString(), persianNumerals) },
                    label = "بهترین سرعت (WPM)",
                    accent = ObsidianSpectrum.BrightBlue,
                )
                RecordCell(
                    value = state.bestAccuracy?.let { formatPercent(it, persianNumerals, decimals = 1) },
                    label = "بهترین دقت",
                    accent = ObsidianSpectrum.Aqua,
                )
                RecordCell(
                    value = formatInt(state.totalSessions, persianNumerals),
                    label = "کل جلسات تایپ",
                    accent = ObsidianSpectrum.Pink,
                )
            }
        }

        state.progression?.let { prog ->
            MrsadSurface(
                modifier = Modifier.fillMaxWidth(),
                accent = ObsidianSpectrum.Lavender,
                onClick = { onNavigate(Routes.ARENA) },
            ) {
                SectionHeader(
                    title = "دستاوردها و نشان‌های مهارتی",
                    actionLabel = "${formatInt(prog.unlockedCount, persianNumerals)} از ${formatInt(prog.achievements.size, persianNumerals)}",
                    onAction = { onNavigate(Routes.ARENA) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                ) {
                    prog.achievements.take(4).forEach { badge ->
                        val badgeShape = RoundedCornerShape(MrsadTokens.Radius.sm)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(badgeShape)
                                .background(
                                    if (badge.unlocked) {
                                        ObsidianSpectrum.DeepViolet.copy(alpha = 0.28f)
                                    } else {
                                        colors.surfaceHigh.copy(alpha = 0.45f)
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (badge.unlocked) ObsidianSpectrum.Lavender else colors.border,
                                    badgeShape,
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MetalIcon(
                                facets = if (badge.unlocked) MrsadIcons.Trophy else MrsadIcons.Target,
                                accentOverride = if (badge.unlocked) ObsidianSpectrum.Lavender else colors.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                badge.titleFa,
                                style = type.caption,
                                color = if (badge.unlocked) colors.textPrimary else colors.textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        MrsadSurface(
            modifier = Modifier.fillMaxWidth(),
            accent = ObsidianSpectrum.Lavender,
            onClick = { onNavigate(Routes.STUDIO) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObsidianIconBox(
                    size = 48.dp,
                    accent = ObsidianSpectrum.Lavender,
                ) {
                    MetalIcon(
                        MrsadIcons.Studio,
                        accentOverride = ObsidianSpectrum.Lavender,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        "استودیو طراحی و نمایش",
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        "مدیریت هویت بصری، مقیاس تایپوگرافی و کیفیت رندر",
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
                MrsadChip(
                    text = "ورود",
                    tint = ObsidianSpectrum.Lavender,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun PlanRow(
    title: String,
    reason: String,
    minutesLabel: String,
    icon: List<IconFacet>,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .background(colors.backgroundElevated.copy(alpha = 0.65f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(MrsadTokens.Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ObsidianIconBox(
            size = 44.dp,
            accent = accent,
        ) {
            MetalIcon(icon, accentOverride = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            Text(reason, style = type.caption, color = colors.textMuted)
        }
        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        MrsadChip(
            text = minutesLabel,
            tint = accent,
            compact = true,
        )
    }
}

@Composable
private fun RecordCell(value: String?, label: String, accent: Color) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        if (value == null) {
            Text("—", style = type.metric.copy(fontSize = type.headline.fontSize), color = colors.textMuted)
        } else {
            MetricValue(value = value, size = type.headline.fontSize, accent = accent)
        }
        Text(
            label,
            style = type.caption,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private object OpeningSequence {
    var played = false
}

@Composable
private fun rememberOpeningProgress(): Animatable<Float, AnimationVector1D> {
    val motion = MrsadTheme.motion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (
            OpeningSequence.played || motion.deterministic ||
            motion.reducedMotion || !motion.animationsEnabled
        ) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        OpeningSequence.played = true
        progress.animateTo(
            1f,
            tween(
                durationMillis = MrsadDuration.long4,
                easing = MrsadEasing.emphasizedDecelerate,
            ),
        )
    }
    return progress
}
