package dev.mrsad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mrsad.app.navigation.Routes
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum

data class MrsadBottomBarItem(
    val route: String,
    val label: String,
    val icon: List<IconFacet>,
)

private fun spectralForRoute(route: String): Color = when (route) {
    Routes.TYPE -> ObsidianSpectrum.BrightBlue
    Routes.MIND -> ObsidianSpectrum.Pink
    Routes.ANALYTICS -> ObsidianSpectrum.Aqua
    Routes.AI -> ObsidianSpectrum.Lavender
    else -> ObsidianSpectrum.BrightBlue
}

/**
 * OBSIDIAN SPECTRUM — Integrated Dimensional Navigation Bar (Master Prompt §11).
 *
 * Unified Obsidian foundation (`#211B2C` -> `#090810`) with spectral active
 * indicator pills, smooth selection transitions, and 48dp touch targets.
 */
@Composable
fun MrsadBottomBar(
    items: List<MrsadBottomBarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    val itemShape = RoundedCornerShape(MrsadTokens.Radius.pill)

    Row(
        modifier = modifier
            .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm)
            .fillMaxWidth()
            .testTag(MrsadTestTags.NAV_BAR)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfaceHigh,
                        colors.background,
                    ),
                ),
            )
            .drawBehindTopSheen()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        colors.borderStrong.copy(alpha = 0.72f),
                        colors.border.copy(alpha = 0.45f),
                    ),
                ),
                shape = shape,
            )
            .padding(horizontal = MrsadTokens.Spacing.xs, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val routeAccent = spectralForRoute(item.route)
            val contentColor by animateColorAsState(
                targetValue = if (selected) colors.textPrimary else colors.textSecondary,
                animationSpec = motion.effectsDefault(),
                label = "navColor",
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = motion.spatialDefault(),
                label = "navBg",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = motion.spatialDefault(),
                label = "navIcon",
            )
            Row(
                modifier = Modifier
                    .testTag(MrsadTestTags.navItem(item.route))
                    .clip(itemShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                routeAccent.copy(alpha = 0.24f * bgAlpha),
                                colors.surfaceHigh.copy(alpha = 0.90f * bgAlpha),
                            ),
                        ),
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 1.dp,
                                color = routeAccent.copy(alpha = 0.65f),
                                shape = itemShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = routeAccent),
                        role = Role.Tab,
                        onClick = { onSelect(item.route) },
                    )
                    .padding(
                        horizontal = if (selected) MrsadTokens.Spacing.sm else MrsadTokens.Spacing.xs,
                        vertical = 6.dp,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetalIcon(
                    facets = item.icon,
                    contentDescription = if (selected) null else item.label,
                    dark = false,
                    accentOverride = routeAccent,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(animationSpec = motion.effectsDefault()) +
                        expandHorizontally(
                            animationSpec = motion.spatialDefault(),
                            expandFrom = Alignment.Start,
                        ),
                    exit = fadeOut(animationSpec = motion.effectsFast()) +
                        shrinkHorizontally(
                            animationSpec = motion.spatialFast(),
                            shrinkTowards = Alignment.Start,
                        ),
                ) {
                    Text(
                        text = item.label,
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = MrsadTokens.Spacing.xxs),
                    )
                }
            }
        }
        Box(
            Modifier
                .testTag(MrsadTestTags.NAV_SETTINGS)
                .size(MrsadTokens.TouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            MetalIcon(MrsadIcons.Settings, contentDescription = "تنظیمات", modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.drawBehindTopSheen(): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            startY = 0f,
            endY = 26f,
        ),
    )
}
