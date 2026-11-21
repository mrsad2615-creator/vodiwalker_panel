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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.theme.MrsadTheme

data class MrsadBottomBarItem(
    val route: String,
    val label: String,
    val icon: List<IconFacet>,
)

/**
 * Dual-System Five-Destination Bottom Navigation (Master Prompt §3):
 *
 * - **OBSIDIAN**: Floating smoked-glass & brushed-titanium dock with a
 *   metallic active pill, top specular sheen, and 3D faceted icons.
 * - **NOIR**: Sharp architectural monochrome bar (`#000000` with `1dp`
 *   `#27272A` hairline border, `0dp` shadow, `2dp` top white active rule,
 *   and uniform `1.5dp` geometric linear icons).
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
    val isNoir = colors.isNoir
    val shape = if (isNoir) RoundedCornerShape(14.dp) else RoundedCornerShape(MrsadTokens.Radius.pill)
    val itemShape = if (isNoir) RoundedCornerShape(10.dp) else RoundedCornerShape(MrsadTokens.Radius.pill)

    Row(
        modifier = modifier
            .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm)
            .fillMaxWidth()
            .testTag(MrsadTestTags.NAV_BAR)
            .clip(shape)
            .background(
                if (isNoir) {
                    Brush.verticalGradient(listOf(colors.surface, colors.backgroundElevated))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF18181E),
                            Color(0xFF0A0A0E),
                        ),
                    )
                },
            )
            .then(if (isNoir) Modifier else Modifier.drawBehindTopSheen())
            .border(
                width = 1.dp,
                brush = if (isNoir) {
                    Brush.verticalGradient(listOf(colors.borderStrong, colors.border))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f),
                        ),
                    )
                },
                shape = shape,
            )
            .padding(horizontal = MrsadTokens.Spacing.xs, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val contentColor by animateColorAsState(
                targetValue = if (selected) colors.background else colors.textSecondary,
                animationSpec = motion.effectsDefault(),
                label = "navColor",
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = motion.spatialDefault(),
                label = "navBg",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) (if (isNoir) 1.02f else 1.08f) else 1f,
                animationSpec = motion.spatialDefault(),
                label = "navIcon",
            )
            Row(
                modifier = Modifier
                    .testTag(MrsadTestTags.navItem(item.route))
                    .clip(itemShape)
                    .background(
                        if (isNoir) {
                            colors.textPrimary.copy(alpha = bgAlpha)
                        } else {
                            colors.metalMid.copy(alpha = bgAlpha * 0.88f)
                        },
                    )
                    .drawBehind {
                        if (isNoir && selected) {
                            drawLine(
                                color = colors.textPrimary,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = colors.metalLight),
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
                    dark = selected,
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
                        style = type.label,
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
                .clip(if (isNoir) RoundedCornerShape(2.dp) else CircleShape)
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
