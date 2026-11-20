package dev.mrsad.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mrsad.app.testing.MrsadTestTags
import dev.mrsad.app.ui.theme.MrsadTheme

data class MrsadBottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating five-destination navigation. The selected item becomes a silver
 * "pill" with its label; others collapse to icons — cinematic without motion
 * noise, and clear at a glance.
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

    Row(
        modifier = modifier
            .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm)
            .fillMaxWidth()
            .testTag(MrsadTestTags.NAV_BAR)
            .clip(RoundedCornerShape(MrsadTokens.Radius.xl))
            .background(Brush.verticalGradient(listOf(colors.surfaceHigh.copy(alpha = 0.97f), colors.surface.copy(alpha = 0.94f))))
            .border(1.dp, colors.border, RoundedCornerShape(MrsadTokens.Radius.xl))
            .padding(horizontal = MrsadTokens.Spacing.xs, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val contentColor by animateColorAsState(
                if (selected) colors.background else colors.textSecondary,
                tween(MrsadTokens.Motion.fast),
                label = "navColor",
            )
            val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(MrsadTokens.Motion.normal), label = "navBg")

            Row(
                modifier = Modifier
                    .testTag(MrsadTestTags.navItem(item.route))
                    .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
                    .background(
                        if (colors.isLight) colors.accent.copy(alpha = bgAlpha * 0.16f)
                        else colors.metalMid.copy(alpha = bgAlpha)
                    )
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
                    contentDescription = item.label,
                    tint = if (colors.isLight && selected) colors.textPrimary else contentColor,
                    modifier = Modifier.size(22.dp),
                )
                if (selected) {
                    Spacer(Modifier.width(MrsadTokens.Spacing.xxs))
                    Text(
                        text = item.label,
                        style = type.label,
                        color = if (colors.isLight) colors.textPrimary else contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
        // Settings lives in the bar as a secondary affordance.
        Box(
            Modifier
                .testTag(MrsadTestTags.NAV_SETTINGS)
                .size(38.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "تنظیمات", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
