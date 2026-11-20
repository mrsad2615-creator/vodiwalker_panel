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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.theme.MrsadTheme

data class MrsadBottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating five-destination navigation.
 *
 * The selected item becomes a silver "pill" with its label; the others
 * collapse to icons. The change between the two states is a *spatial*
 * movement, so it is driven by a spatial spring: the label grows out of
 * the icon instead of appearing, and the pill settles rather than
 * snapping.
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
    val shape = RoundedCornerShape(MrsadTokens.Radius.xl)

    Row(
        modifier = modifier
            .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm)
            .fillMaxWidth()
            .testTag(MrsadTestTags.NAV_BAR)
            .shadow(elevation = 16.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfaceHigh.copy(alpha = 0.98f),
                        colors.surface.copy(alpha = 0.95f),
                    )
                )
            )
            .drawBehindTopSheen()
            .border(
                Brush.verticalGradient(
                    listOf(colors.borderStrong.copy(alpha = 0.40f), colors.borderStrong.copy(alpha = 0.08f))
                ),
                shape,
            )
            .padding(horizontal = MrsadTokens.Spacing.xs, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            // The colour of the icon is an effect: it fades.
            val contentColor by animateColorAsState(
                targetValue = if (selected) colors.background else colors.textSecondary,
                animationSpec = motion.effectsDefault(),
                label = "navColor",
            )
            // The pill behind the item moves and grows, so it is spatial.
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = motion.spatialDefault(),
                label = "navBg",
            )
            // The icon lifts a little when it is chosen.
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = motion.spatialDefault(),
                label = "navIcon",
            )
            Row(
                modifier = Modifier
                    .testTag(MrsadTestTags.navItem(item.route))
                    .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
                    .background(
                        if (colors.isLight) {
                            colors.accent.copy(alpha = bgAlpha * 0.16f)
                        } else {
                            colors.metalMid.copy(alpha = bgAlpha * 0.85f)
                        }
                    )
                    // A 22dp icon is not a 22dp target: the destination
                    // keeps a full 48dp of touchable area in both directions.
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = colors.metalLight),
                        role = Role.Tab,
                        onClick = { onSelect(item.route) },
                    )
                    .padding(horizontal = if (selected) MrsadTokens.Spacing.sm else MrsadTokens.Spacing.xs, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon,
                    // The selected item shows its label as text, so the
                    // icon must stay silent; the collapsed items have no
                    // text and have to name themselves.
                    contentDescription = if (selected) null else item.label,
                    tint = if (colors.isLight && selected) colors.textPrimary else contentColor,
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
                            expandFrom = Alignment.Horizontal.Start,
                        ),
                    exit = fadeOut(animationSpec = motion.effectsFast()) +
                        shrinkHorizontally(
                            animationSpec = motion.spatialFast(),
                            shrinkTowards = Alignment.Horizontal.Start,
                        ),
                ) {
                    Text(
                        text = item.label,
                        style = type.label,
                        color = if (colors.isLight) colors.textPrimary else contentColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = MrsadTokens.Spacing.xxs),
                    )
                }
            }
        }
        // Settings lives in the bar as a secondary affordance.
        Box(
            Modifier
                .testTag(MrsadTestTags.NAV_SETTINGS)
                .size(MrsadTokens.TouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "تنظیمات", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The lit top edge of the floating bar.
 *
 * The bar hovers over the content, so the light that falls on it comes
 * from above: a short bright band along its top edge is what makes it
 * read as a separate object rather than as a strip of colour.
 */
private fun Modifier.drawBehindTopSheen(): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
            startY = 0f,
            endY = 26f,
        )
    )
}
