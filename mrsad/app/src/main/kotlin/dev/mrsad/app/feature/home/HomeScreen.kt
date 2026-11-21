package dev.mrsad.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.navigation.Routes
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.components.IconFacet
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.ModuleSculpture3D
import dev.mrsad.app.ui.components.ModuleSculptureType
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadCore
import dev.mrsad.app.ui.components.MrsadDivider
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadMarkView
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadProgressBar
import dev.mrsad.app.ui.components.MrsadReveal
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.components.SparkLine
import dev.mrsad.app.ui.components.StreakCalendarMatrix
import dev.mrsad.app.ui.components.chamferedShape
import dev.mrsad.app.ui.components.rememberRevealProgress
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadDuration
import dev.mrsad.app.ui.theme.MrsadEasing
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import dev.mrsad.core.coach.Module
import dev.mrsad.core.progression.ProgressionEngine

/**
 * MRSAD HOME — Dual-System Flagship Command Center (Master Prompt §4):
 *
 * - **OBSIDIAN (Luxury Dark 3D Spatial)**: 3D Orbital Core (`ORRERY PRIME`),
 *   smoked-glass chamfered cards, bespoke 3D module sculptures, and silver depth.
 * - **NOIR (Minimal Black & White Editorial)**: Swiss numbered index sections
 *   (`01`–`07`), 1dp hairline architecture, monochrome technical schematics,
 *   and zero-shadow editorial typography.
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
    val isNoir = colors.isNoir
    val reveal = rememberRevealProgress()
    val opening = rememberOpeningProgress()

    AmbientBackground(PageEnvironment.HOME) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val open = opening.value
                    alpha = open
                    val settle = 0.97f + 0.03f * open
                    scaleX = settle
                    scaleY = settle
                }
                .padding(horizontal = MrsadTokens.Spacing.md)
                .padding(bottom = LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            contentPadding = PaddingValues(top = MrsadTokens.Spacing.lg, bottom = MrsadTokens.Spacing.lg),
        ) {
            // 01: Header + Personalized Greeting + Level/XP Banner
            item {
                MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 0) {
                    HomeHeader(
                        state = state,
                        persianNumerals = settings.persianNumerals,
                        onOpenSettings = { onNavigate(Routes.SETTINGS) },
                    )
                }
            }

            // 02: Hero Centerpiece (3D Orrery Core in OBSIDIAN / Editorial Telemetry Slab in NOIR)
            item {
                MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 1) {
                    if (isNoir) {
                        NoirHeroTelemetrySlab(
                            state = state,
                            persianNumerals = settings.persianNumerals,
                        )
                    } else {
                        ObsidianHeroCore(
                            state = state,
                            persianNumerals = settings.persianNumerals,
                        )
                    }
                }
            }

            // 03: Daily Status & Goal Progress
            item {
                MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 2) {
                    MrsadSurface(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isNoir) "02 // وضعیت روزانه و پیشرفت هدف" else "وضعیت روزانه و هدف ۲۵ دقیقه‌ای",
                                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                            )
                            Text(
                                "${formatPercent(state.todayProgress * 100.0, settings.persianNumerals)} تکمیل",
                                style = type.caption,
                                color = colors.accent,
                            )
                        }
                        MrsadProgressBar(progress = state.todayProgress)
                        Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                            StatCell(
                                modifier = Modifier.weight(1f),
                                value = formatInt(state.todayMinutes, settings.persianNumerals),
                                label = "دقیقه امروز",
                                icon = MrsadIcons.Focus,
                            )
                            StatCell(
                                modifier = Modifier.weight(1f),
                                value = formatInt(state.todaySessions, settings.persianNumerals),
                                label = "تمرین امروز",
                                icon = MrsadIcons.Trophy,
                            )
                            StatCell(
                                modifier = Modifier.weight(1f),
                                value = formatInt(state.streakDays, settings.persianNumerals),
                                label = "روز پیوسته",
                                icon = MrsadIcons.Flame,
                            )
                        }
                    }
                }
            }

            // 04: Quick Access Carousel (Test-tagged HOME_MODULES) + 4 Flagship Pillar Cards with 3D Sculptures
            item {
                MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 3) {
                    SectionHeader(
                        title = if (isNoir) "03 // چهار ستون مهارت و ماژول‌ها" else "چهار ستون اصلی و ماژول‌ها",
                        actionLabel = "تحلیل کامل",
                        onAction = { onNavigate(Routes.ANALYTICS) },
                    )
                }
            }
            item {
                MrsadReveal(reveal, modifier = Modifier.fillMaxWidth(), index = 4) {
                    LazyRow(
                        modifier = Modifier.testTag(MrsadTestTags.HOME_MODULES),
                        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                    ) {
                        items(moduleEntries) { entry ->
                            ModuleCard(entry = entry, onClick = { onNavigate(entry.route) })
                        }
                    }
                }
            }

            // 2×2 Flagship Pillar Showcase with Bespoke 3D Objects (OBSIDIAN) / Technical Schematics (NOIR)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                        PillarSculptureCard(
                            modifier = Modifier.weight(1f),
                            indexCode = "01",
                            title = "سرعت تایپ",
                            subtitle = state.bestWpm?.let { "رکورد: ${formatInt(it, settings.persianNumerals)} کلمه/دقیقه" }
                                ?: "موتور استاندارد WPM",
                            sculptureType = ModuleSculptureType.TYPING,
                            onClick = { onNavigate(Routes.TYPE) },
                        )
                        PillarSculptureCard(
                            modifier = Modifier.weight(1f),
                            indexCode = "02",
                            title = "تندخوانی و درک",
                            subtitle = state.bestReadingWpm?.let { "رکورد: ${formatInt(it, settings.persianNumerals)} کلمه/دقیقه" }
                                ?: "خوانش هدایت‌شده و RSVP",
                            sculptureType = ModuleSculptureType.READING,
                            onClick = { onNavigate(Routes.READ) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                        PillarSculptureCard(
                            modifier = Modifier.weight(1f),
                            indexCode = "03",
                            title = "تمرین چشم",
                            subtitle = if (state.eyeSessions > 0) {
                                "${formatInt(state.eyeSessions, settings.persianNumerals)} جلسه • چابکی ${formatInt(state.avgEyeScore, settings.persianNumerals)}٪"
                            } else {
                                "ساکاد، تعقیب و دید محیطی"
                            },
                            sculptureType = ModuleSculptureType.EYE,
                            onClick = { onNavigate(Routes.EYE) },
                        )
                        PillarSculptureCard(
                            modifier = Modifier.weight(1f),
                            indexCode = "04",
                            title = "تمرین مغز",
                            subtitle = if (state.mindSessions > 0) {
                                "${formatInt(state.mindSessions, settings.persianNumerals)} جلسه • امتیاز ${formatInt(state.avgMindScore, settings.persianNumerals)}"
                            } else {
                                "حافظه، تمرکز و سرعت واکنش"
                            },
                            sculptureType = ModuleSculptureType.BRAIN,
                            onClick = { onNavigate(Routes.MIND) },
                        )
                    }
                }
            }

            // Studio / Theme Architecture Entry
            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.STUDIO) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetalIcon(MrsadIcons.Assistant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "MRSAD STUDIO — معماری دوگانهٔ تم",
                                style = type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                                color = colors.textPrimary,
                            )
                            Text(
                                if (isNoir) "تم فعال: NOIR (مینیمال سیاه‌وسفید) • تغییر آنی به OBSIDIAN" else "تم فعال: OBSIDIAN (لوکس سه‌بعدی تیره) • تغییر آنی به NOIR",
                                style = type.caption,
                                color = colors.textSecondary,
                            )
                        }
                        Text("استودیو ←", style = type.caption, color = colors.accent)
                    }
                }
            }

            // 05: Daily Missions & Smart Plan
            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = if (isNoir) "04 // مأموریت روزانه و برنامهٔ هوشمند" else "برنامهٔ امروز و مأموریت‌ها",
                        actionLabel = "مربی هوشمند",
                        onAction = { onNavigate(Routes.AI) },
                    )
                    if (state.plan.isEmpty()) {
                        Text(
                            "پس از اولین تمرین، برنامهٔ روزانه بر پایهٔ داده‌های واقعی شما ساخته می‌شود.",
                            style = type.body,
                            color = colors.textSecondary,
                        )
                    } else {
                        Text("بر اساس داده‌های واقعی تمرین شما", style = type.caption, color = colors.textMuted)
                        state.plan.forEach { item ->
                            PlanRow(
                                title = item.titleFa,
                                reason = item.reasonFa,
                                minutesLabel = "${formatInt(item.minutes, settings.persianNumerals)} دقیقه",
                                icon = iconFor(item.module),
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
                                        "${formatInt(ch.current, settings.persianNumerals)} / ${formatInt(ch.target, settings.persianNumerals)} (+${formatInt(ch.rewardXp, settings.persianNumerals)} XP)",
                                        style = type.caption,
                                        color = if (ch.completed) colors.success else colors.textMuted,
                                    )
                                }
                                MrsadProgressBar(
                                    progress = ch.progressFraction,
                                    height = 4.dp,
                                    tint = if (ch.completed) colors.success else colors.accent,
                                )
                            }
                        }
                    }
                }
            }

            // 06: Weekly Progress Chart & 28-Day Streak Matrix
            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = if (isNoir) "05 // نمودار پیشرفت هفتگی و تقویم استمرار" else "نمودار پیشرفت هفتگی و استمرار",
                        actionLabel = "جزئیات",
                        onAction = { onNavigate(Routes.ANALYTICS) },
                    )
                    SparkLine(
                        values = state.weeklyMinutesSeries,
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
                                "${formatInt(calendar.count { it.active }, settings.persianNumerals)} روز فعال",
                                style = type.caption,
                                color = colors.textPrimary,
                            )
                        }
                        StreakCalendarMatrix(days = calendar)
                    }
                }
            }

            // 07: Personal Records & Recent Stats
            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = if (isNoir) "06 // رکوردهای شخصی و آمار اخیر" else "رکوردهای شخصی")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = MrsadTokens.Spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        RecordCell(
                            value = state.bestWpm?.let { formatNumber(it.toString(), settings.persianNumerals) },
                            label = "بهترین سرعت (کلمه/دقیقه)",
                        )
                        RecordCell(
                            value = state.bestAccuracy?.let { formatPercent(it, settings.persianNumerals, decimals = 1) },
                            label = "بهترین دقت",
                        )
                        RecordCell(
                            value = formatInt(state.totalSessions, settings.persianNumerals),
                            label = "کل جلسات تایپ",
                        )
                    }
                }
            }

            // 08: Achievements & Badges Preview
            state.progression?.let { prog ->
                item {
                    MrsadSurface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigate(Routes.ARENA) },
                    ) {
                        SectionHeader(
                            title = if (isNoir) "07 // دستاوردها و نشان‌های مهارت" else "دستاوردهای مهارتی",
                            actionLabel = "${formatInt(prog.unlockedCount, settings.persianNumerals)} از ${formatInt(prog.achievements.size, settings.persianNumerals)}",
                            onAction = { onNavigate(Routes.ARENA) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                        ) {
                            prog.achievements.take(4).forEach { badge ->
                                val badgeShape = if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.sm)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(badgeShape)
                                        .background(if (badge.unlocked) colors.accentSoft else colors.surfaceHigh.copy(alpha = 0.45f))
                                        .border(
                                            1.dp,
                                            if (badge.unlocked) colors.accent else colors.border,
                                            badgeShape,
                                        )
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MetalIcon(
                                        facets = if (badge.unlocked) MrsadIcons.Trophy else MrsadIcons.Target,
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
            }

            // Onboarding Quick-Start CTA when database is empty
            if (!state.hasAnyData) {
                item {
                    MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                        Text("شروع سریع", style = type.title, color = colors.textPrimary)
                        Text(
                            "برای دیدن آمار واقعی، یک تمرین تایپ ۶۰ ثانیه‌ای انجام دهید. همهٔ اعداد این صفحه از همان جلسه ساخته می‌شوند.",
                            style = type.body,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                        MrsadPrimaryButton(
                            text = "شروع تمرین تایپ",
                            icon = Icons.Outlined.Keyboard,
                            onClick = { onNavigate(Routes.TYPE) },
                            modifier = Modifier.testTag(MrsadTestTags.HOME_PRIMARY_CTA),
                        )
                    }
                }
            }
        }
    }
}

private data class ModuleEntry(
    val code: String,
    val title: String,
    val icon: List<IconFacet>,
    val route: String,
    val subtitle: String,
)

private val moduleEntries = listOf(
    ModuleEntry("TYPE", "تایپ", MrsadIcons.Type, Routes.TYPE, "سرعت و دقت"),
    ModuleEntry("READ", "خواندن", MrsadIcons.Read, Routes.READ, "درک مطلب"),
    ModuleEntry("EYE", "چشم", MrsadIcons.Eye, Routes.EYE, "مهارت دیداری"),
    ModuleEntry("MIND", "ذهن", MrsadIcons.Mind, Routes.MIND, "تمرین شناختی"),
    ModuleEntry("FOCUS", "تمرکز", MrsadIcons.Focus, Routes.FOCUS, "زمان‌سنج عمیق"),
    ModuleEntry("AI", "دستیار", MrsadIcons.Assistant, Routes.AI, "مربی هوشمند"),
)

private fun iconFor(module: Module): List<IconFacet> = when (module) {
    Module.TYPE -> MrsadIcons.Type
    Module.READ -> MrsadIcons.Read
    Module.EYE -> MrsadIcons.Eye
    Module.MIND -> MrsadIcons.Mind
    Module.FOCUS -> MrsadIcons.Focus
}

@Composable
private fun HomeHeader(
    state: HomeState,
    persianNumerals: Boolean,
    onOpenSettings: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val isNoir = colors.isNoir
    val levelInfo = state.progression?.levelInfo

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MRSAD",
                    style = type.display.copy(letterSpacing = 4.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND),
                )
                Text(
                    if (isNoir) "NOIR // زیست‌بوم رشد شناختی و مهارت‌های ذهنی" else "زیست‌بوم رشد شناختی و مهارت‌های فردی",
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.md))
                    .background(colors.surfaceHigh)
                    .border(
                        1.dp,
                        colors.border,
                        if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.md),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MrsadMarkView(
                    size = 30.dp,
                    monochrome = isNoir,
                    modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND_MARK),
                    contentDescription = "MRSAD",
                )
            }
        }

        if (levelInfo != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MrsadChip(
                    text = "سطح ${formatInt(levelInfo.level, persianNumerals)} • ${levelInfo.titleFa}",
                    tint = colors.accent,
                )
                Text(
                    "${formatInt(levelInfo.totalXp, persianNumerals)} XP",
                    style = type.label,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ObsidianHeroCore(
    state: HomeState,
    persianNumerals: Boolean,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val shape = RoundedCornerShape(MrsadTokens.Radius.xl)
    Box(
        Modifier
            .testTag(MrsadTestTags.HOME_CORE)
            .fillMaxWidth()
            .height(312.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141A26),
                        Color(0xFF0A0D14),
                        Color(0xFF06080C),
                    ),
                ),
            )
            .border(
                width = 1.25.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        colors.metalMid.copy(alpha = 0.28f),
                        Color(0xFF080A10),
                    ),
                ),
                shape = shape,
            )
            .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm),
    ) {
        // Top HUD bar inside the 3D Core Chamber
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ORRERY PRIME // 3D CORE",
                style = type.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.2.sp,
                ),
                color = colors.metalLight,
            )
            MrsadChip(
                text = "${formatPercent(state.todayProgress * 100.0, persianNumerals)} هدف امروز",
                tint = colors.accent,
            )
        }

        // The 3D Volumetric PBR / Machined Titanium Core
        MrsadCore(
            energy = state.energy,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 22.dp),
            streakDays = state.streakDays,
            bestWpm = state.bestWpm ?: 0,
        )

        // Sleek bottom caption
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.hasAnyData) "هستهٔ سه‌بعدی فعالیت شما" else "هسته در انتظار اولین تمرین",
                style = type.caption,
                color = colors.textSecondary,
            )
            Text(
                text = "چرخش سه‌بعدی با لمس",
                style = type.caption,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun NoirHeroTelemetrySlab(
    state: HomeState,
    persianNumerals: Boolean,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = Modifier
            .testTag(MrsadTestTags.HOME_CORE)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "01 // SWISS TELEMETRY INDEX",
                style = type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
                color = colors.textSecondary,
            )
            Text(
                if (state.hasAnyData) "ACTIVE" else "STANDBY",
                style = type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                color = colors.textPrimary,
            )
        }
        MrsadDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${formatPercent(state.todayProgress * 100.0, persianNumerals)}",
                    style = type.display,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (state.hasAnyData) "نرخ تحقق هدف روزانه (۲۵ دقیقه)" else "هسته در انتظار اولین تمرین",
                    style = type.caption,
                    color = colors.textSecondary,
                )
            }
            ModuleSculpture3D(
                type = ModuleSculptureType.BRAIN,
                modifier = Modifier.size(84.dp),
            )
        }
        MrsadProgressBar(progress = state.todayProgress, height = 8.dp)
    }
}

@Composable
private fun PillarSculptureCard(
    modifier: Modifier = Modifier,
    indexCode: String,
    title: String,
    subtitle: String,
    sculptureType: ModuleSculptureType,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val isNoir = colors.isNoir
    val stageShape = RoundedCornerShape(if (isNoir) 2.dp else MrsadTokens.Radius.md)

    MrsadSurface(
        modifier = modifier,
        onClick = onClick,
        elevated = !isNoir,
    ) {
        // Dedicated 3D Object Showcase Well
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(stageShape)
                .background(
                    if (isNoir) {
                        Brush.verticalGradient(listOf(colors.background, colors.background))
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E2738),
                                Color(0xFF0B0E16),
                            ),
                        )
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (isNoir) colors.border else colors.metalMid.copy(alpha = 0.28f),
                    shape = stageShape,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                indexCode,
                style = type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.TopStart),
            )
            ModuleSculpture3D(
                type = sculptureType,
                modifier = Modifier
                    .size(76.dp)
                    .align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = type.title.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
            )
            Text(
                "←",
                style = type.label,
                color = colors.accent,
            )
        }
        Text(
            subtitle,
            style = type.caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    icon: List<IconFacet>,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val cellShape = RoundedCornerShape(if (colors.isNoir) 2.dp else MrsadTokens.Radius.md)
    Column(
        modifier = modifier
            .clip(cellShape)
            .background(
                if (colors.isNoir) {
                    colors.background
                } else {
                    Color(0xFF0B0E16)
                },
            )
            .border(
                width = 1.dp,
                color = if (colors.isNoir) colors.border else colors.metalMid.copy(alpha = 0.25f),
                shape = cellShape,
            )
            .padding(vertical = MrsadTokens.Spacing.sm, horizontal = 4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MetalIcon(icon, modifier = Modifier.size(18.dp))
        Text(
            value,
            style = type.metric.copy(fontSize = type.headline.fontSize, lineHeight = type.headline.lineHeight),
            color = colors.textPrimary,
        )
        Text(label, style = type.caption, color = colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ModuleCard(entry: ModuleEntry, onClick: () -> Unit) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = Modifier
            .testTag(MrsadTestTags.homeModule(entry.route))
            .width(148.dp),
        shape = if (colors.isNoir) RoundedCornerShape(2.dp) else chamferedShape(),
        onClick = onClick,
    ) {
        MetalIcon(entry.icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(MrsadTokens.Spacing.xs))
        Text(
            entry.code,
            style = type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            color = colors.textMuted,
        )
        Text(entry.title, style = type.title, color = colors.textPrimary)
        Text(entry.subtitle, style = type.caption, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun PlanRow(
    title: String,
    reason: String,
    minutesLabel: String,
    icon: List<IconFacet>,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (colors.isNoir) 2.dp else MrsadTokens.Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetalIcon(icon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = colors.textPrimary)
            Text(reason, style = type.caption, color = colors.textMuted)
        }
        Text(
            minutesLabel,
            style = type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.width(MrsadTokens.Spacing.xxs))
        Text(
            "شروع",
            style = type.caption,
            color = colors.accent,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
private fun RecordCell(value: String?, label: String) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        if (value == null) {
            Text("—", style = type.metric.copy(fontSize = type.headline.fontSize), color = colors.textMuted)
        } else {
            MetricValue(value = value, size = type.headline.fontSize)
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
