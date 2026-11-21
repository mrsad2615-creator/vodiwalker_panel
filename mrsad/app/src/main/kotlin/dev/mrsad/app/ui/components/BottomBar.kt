package dev.mrsad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mrsad.app.navigation.Routes
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum

/**
 * OBSIDIAN SPECTRUM — Bottom Navigation Dock (`MrsadBottomBar`).
 */
val LocalBottomBarClearance = compositionLocalOf { 104.dp }

data class BottomNavItem(
    val route: String,
    val labelFa: String,
    val labelEn: String,
    val icon: List<IconFacet>,
    val spectralColor: Color,
    val testTag: String? = null,
)

val PrimaryNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(
        route = Routes.HOME,
        labelFa = "خانه",
        labelEn = "HOME",
        icon = emptyList(),
        spectralColor = ObsidianSpectrum.BrightBlue,
    ),
    BottomNavItem(
        route = Routes.TYPE,
        labelFa = "تایپ",
        labelEn = "TYPE",
        icon = MrsadIcons.Type,
        spectralColor = ObsidianSpectrum.BrightBlue,
    ),
    BottomNavItem(
        route = Routes.MIND,
        labelFa = "ذهن",
        labelEn = "MIND",
        icon = MrsadIcons.Mind,
        spectralColor = ObsidianSpectrum.Pink,
    ),
    BottomNavItem(
        route = Routes.ANALYTICS,
        labelFa = "آمار",
        labelEn = "STATS",
        icon = MrsadIcons.Analytics,
        spectralColor = ObsidianSpectrum.Aqua,
    ),
    BottomNavItem(
        route = Routes.AI,
        labelFa = "دستیار",
        labelEn = "AI",
        icon = MrsadIcons.Assistant,
        spectralColor = ObsidianSpectrum.Lavender,
    ),
    BottomNavItem(
        route = Routes.SETTINGS,
        labelFa = "تنظیمات",
        labelEn = "SETTINGS",
        icon = MrsadIcons.Sliders,
        spectralColor = ObsidianSpectrum.BrightBlue,
        testTag = MrsadTestTags.NAV_SETTINGS,
    ),
)

@Composable
fun MrsadBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val dockShape = RoundedCornerShape(MrsadTokens.Radius.xl)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = MrsadTokens.Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(dockShape)
                .background(colors.surfaceHigh.copy(alpha = 0.96f))
                .border(width = 1.dp, color = colors.borderStrong, shape = dockShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryNavItems.forEach { item ->
                val isSelected = currentRoute == item.route ||
                    (currentRoute == null && item.route == Routes.HOME)
                val pillShape = RoundedCornerShape(MrsadTokens.Radius.lg)
                val interactionSource = remember { MutableInteractionSource() }
                val tagModifier = if (item.testTag != null) {
                    Modifier.testTag(item.testTag)
                } else {
                    Modifier.testTag(MrsadTestTags.bottomNav(item.route))
                }

                val pillModifier = if (isSelected) {
                    Modifier
                        .weight(1.38f)
                        .height(50.dp)
                        .clip(pillShape)
                        .background(item.spectralColor.copy(alpha = 0.22f))
                        .border(1.dp, item.spectralColor.copy(alpha = 0.68f), pillShape)
                } else {
                    Modifier
                        .weight(0.92f)
                        .height(50.dp)
                        .clip(pillShape)
                }

                Row(
                    modifier = tagModifier
                        .then(pillModifier)
                        .semantics {
                            selected = isSelected
                            contentDescription = item.labelFa
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(item.route) },
                        )
                        .padding(horizontal = if (isSelected) 10.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (item.route == Routes.HOME) {
                        HomeGridGlyph(
                            selected = isSelected,
                            size = 22.dp,
                        )
                    } else {
                        MetalIcon(
                            facets = item.icon,
                            accentOverride = if (isSelected) {
                                item.spectralColor
                            } else {
                                item.spectralColor.copy(alpha = 0.72f)
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(animationSpec = motion.effectsFast()),
                        exit = fadeOut(animationSpec = motion.effectsFast()),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.labelFa,
                                style = type.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGridGlyph(
    selected: Boolean,
    size: Dp,
) {
    val activeColors = listOf(
        ObsidianSpectrum.BrightBlue,
        ObsidianSpectrum.Lavender,
        ObsidianSpectrum.Aqua,
        ObsidianSpectrum.Pink,
    )
    Canvas(modifier = Modifier.size(size)) {
        val gap = 2.4.dp.toPx()
        val cell = (this.size.minDimension - gap) / 2f
        val corner = CornerRadius(cell * 0.30f, cell * 0.30f)
        val positions = listOf(
            Offset(0f, 0f),
            Offset(cell + gap, 0f),
            Offset(0f, cell + gap),
            Offset(cell + gap, cell + gap),
        )
        positions.forEachIndexed { idx, topLeft ->
            val tint = activeColors[idx]
            drawRoundRect(
                color = tint.copy(alpha = if (selected) 0.90f else 0.55f),
                topLeft = topLeft,
                size = Size(cell, cell),
                cornerRadius = corner,
            )
        }
    }
}
