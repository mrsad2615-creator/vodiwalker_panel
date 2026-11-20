package dev.mrsad.app.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetricValue
import dev.mrsad.app.ui.components.MrsadCore
import dev.mrsad.app.ui.components.MrsadReveal
import dev.mrsad.app.ui.components.MrsadMarkView
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.SectionHeader
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment
import dev.mrsad.app.ui.theme.formatInt
import dev.mrsad.app.ui.theme.formatNumber
import dev.mrsad.app.ui.theme.formatPercent
import dev.mrsad.core.coach.Module

/**
 * MRSAD HOME — Obsidian Depth.
 *
 * Every number comes from local database flows via [HomeViewModel]; the core
 * object's energy is real goal progress and its orbit sparks are the real
 * current streak. With no data the screen shows an honest onboarding state.
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

    AmbientBackground(PageEnvironment.HOME) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MrsadTokens.Spacing.md)
                .padding(bottom = LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.md),
            contentPadding = PaddingValues(top = MrsadTokens.Spacing.lg, bottom = MrsadTokens.Spacing.lg),
        ) {
            item {
                MrsadReveal(modifier = Modifier.fillMaxWidth(), index = 0) { HomeHeader() }
            }

            item {
                MrsadReveal(modifier = Modifier.fillMaxWidth(), index = 1) {
                    Box(
                        Modifier
                            .testTag(MrsadTestTags.HOME_CORE)
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        MrsadCore(
                            energy = state.energy,
                            modifier = Modifier.fillMaxSize(),
                            streakDays = state.streakDays,
                            bestWpm = state.bestWpm ?: 0,
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (state.hasAnyData) "هستهٔ فعالیت شما" else "هسته در انتظار اولین تمرین",
                                style = type.caption,
                                color = colors.textMuted,
                            )
                            if (state.hasAnyData) {
                                Text(
                                    text = "${formatPercent(state.todayProgress * 100.0, settings.persianNumerals)} از هدف امروز",
                                    style = type.label,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            item {
                MrsadReveal(modifier = Modifier.fillMaxWidth(), index = 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
                        StatCell(
                            modifier = Modifier.weight(1f),
                            value = formatInt(state.todayMinutes, settings.persianNumerals),
                            label = "دقیقه امروز",
                            icon = Icons.Outlined.SelfImprovement,
                        )
                        StatCell(
                            modifier = Modifier.weight(1f),
                            value = formatInt(state.todaySessions, settings.persianNumerals),
                            label = "تمرین امروز",
                            icon = Icons.Outlined.EmojiEvents,
                        )
                        StatCell(
                            modifier = Modifier.weight(1f),
                            value = formatInt(state.streakDays, settings.persianNumerals),
                            label = "روز پیوسته",
                            icon = Icons.Outlined.LocalFireDepartment,
                        )
                    }
                }
            }

            item {
                MrsadReveal(modifier = Modifier.fillMaxWidth(), index = 3) {
                    SectionHeader(title = "ماژول‌ها")
                }
            }
            item {
                MrsadReveal(modifier = Modifier.fillMaxWidth(), index = 4) {
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

            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.STUDIO) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "MRSAD STUDIO",
                                style = type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                                color = colors.textPrimary,
                            )
                            Text("شخصی‌سازی کامل ظاهر برنامه", style = type.caption, color = colors.textSecondary)
                        }
                        Text("تنظیمات ظاهری ←", style = type.caption, color = colors.accent)
                    }
                }
            }

            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = "برنامهٔ امروز")
                    if (state.plan.isEmpty()) {
                        Text(
                            "پس از اولین تمرین، برنامهٔ روزانه بر پایهٔ داده‌های واقعی شما ساخته می‌شود.",
                            style = type.body,
                            color = colors.textSecondary,
                        )
                    } else {
                        Text("بر اساس داده‌های تمرین شما", style = type.caption, color = colors.textMuted)
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
                                        Module.MIND -> onNavigate(Routes.MIND)
                                        Module.FOCUS -> onNavigate(Routes.FOCUS)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                MrsadSurface(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = "رکوردهای شخصی")
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
                        dev.mrsad.app.ui.components.MrsadPrimaryButton(
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
    val icon: ImageVector,
    val route: String,
    val subtitle: String,
)

private val moduleEntries = listOf(
    ModuleEntry("TYPE", "تایپ", Icons.Outlined.Keyboard, Routes.TYPE, "سرعت و دقت"),
    ModuleEntry("READ", "خواندن", Icons.Outlined.MenuBook, Routes.READ, "درک مطلب"),
    ModuleEntry("MIND", "ذهن", Icons.Outlined.Psychology, Routes.MIND, "تمرین شناختی"),
    ModuleEntry("AI", "دستیار", Icons.Outlined.AutoAwesome, Routes.AI, "مربی هوشمند"),
)

private fun iconFor(module: Module): ImageVector = when (module) {
    Module.TYPE -> Icons.Outlined.Keyboard
    Module.READ -> Icons.Outlined.MenuBook
    Module.MIND -> Icons.Outlined.Psychology
    Module.FOCUS -> Icons.Outlined.SelfImprovement
}

@Composable
private fun HomeHeader() {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = MrsadTokens.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "MRSAD",
                style = type.display.copy(letterSpacing = 4.sp),
                color = colors.textPrimary,
                modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND),
            )
            Text("زیست‌بوم رشد شناختی", style = type.caption, color = colors.textSecondary)
        }
        // The real brand mark, drawn from the same geometry as the launcher
        // icon, so the first screen and the home screen are one identity.
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            MrsadMarkView(
                size = 30.dp,
                modifier = Modifier.testTag(MrsadTestTags.HOME_BRAND_MARK),
                contentDescription = "MRSAD",
            )
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.md))
            .padding(vertical = MrsadTokens.Spacing.sm, horizontal = 2.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = colors.metalMid, modifier = Modifier.size(16.dp))
        Text(
            value,
            style = type.metric.copy(fontSize = type.headline.fontSize, lineHeight = type.headline.lineHeight),
            color = colors.textPrimary,
        )
        Text(label, style = type.caption, color = colors.textMuted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ModuleCard(entry: ModuleEntry, onClick: () -> Unit) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        modifier = Modifier
            .testTag(MrsadTestTags.homeModule(entry.route))
            .width(152.dp),
        onClick = onClick,
    ) {
        Icon(entry.icon, contentDescription = null, tint = colors.metalMid, modifier = Modifier.size(26.dp))
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
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.metalMid, modifier = Modifier.size(18.dp))
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
