package dev.mrsad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadNumericFamily
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import kotlinx.coroutines.delay

/**
 * OBSIDIAN SPECTRUM — Design Tokens & Flagship UI Components (`Components.kt`).
 */
object MrsadTokens {
    object Spacing {
        val xxs: Dp = 4.dp
        val xs: Dp = 8.dp
        val sm: Dp = 12.dp
        val md: Dp = 16.dp
        val lg: Dp = 24.dp
        val xl: Dp = 32.dp
        val xxl: Dp = 48.dp
    }

    object Radius {
        val xs: Dp = 8.dp
        val sm: Dp = 12.dp
        val md: Dp = 16.dp
        val lg: Dp = 22.dp
        val xl: Dp = 28.dp
        val pill: Dp = 999.dp
    }

    val MinTouchTarget: Dp = 48.dp
}

/**
 * Auto-Collapsing Flagship Top Bar (`ObsidianCollapsingTopBar`).
 *
 * Automatically compacts when [collapsed] is `true` (driven by scroll offset).
 */
@Composable
fun ObsidianCollapsingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    moduleBadge: String? = null,
    collapsed: Boolean = false,
    spectralColor: Color = MrsadTheme.colors.accent,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val verticalPadding = if (collapsed) 8.dp else 12.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.96f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MrsadTokens.Spacing.md, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
        ) {
            if (onBack != null) {
                MrsadIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "بازگشت",
                    onClick = onBack,
                    tint = spectralColor,
                )
            } else if (leading != null) {
                leading()
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!collapsed && !moduleBadge.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(spectralColor),
                        )
                        Text(
                            text = moduleBadge,
                            style = type.caption.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.1.sp,
                            ),
                            color = spectralColor,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = title,
                    style = if (collapsed) {
                        type.title.copy(fontWeight = FontWeight.Bold)
                    } else {
                        type.headline.copy(fontWeight = FontWeight.Bold)
                    },
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!collapsed && !subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = type.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
                    content = trailing,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(spectralColor.copy(alpha = if (collapsed) 0.48f else 0.28f)),
        )
    }
}

/**
 * Structured Segmented Section Bar (`ObsidianSegmentedBar`).
 */
data class SegmentedBarItem(
    val id: String,
    val label: String,
    val badge: String? = null,
    val accent: Color? = null,
)

@Composable
fun ObsidianSegmentedBar(
    items: List<SegmentedBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    defaultAccent: Color = MrsadTheme.colors.accent,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val outerShape = RoundedCornerShape(MrsadTokens.Radius.lg)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(colors.backgroundElevated)
            .border(1.dp, colors.border, outerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            val itemAccent = item.accent ?: defaultAccent
            val pillShape = RoundedCornerShape(MrsadTokens.Radius.md)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(itemAccent.copy(alpha = 0.22f))
                                .border(1.dp, itemAccent.copy(alpha = 0.72f), pillShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab) { onSelect(item.id) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(itemAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = item.label,
                        style = type.caption.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.badge.isNullOrBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = item.badge,
                            style = type.caption.copy(fontSize = 11.sp),
                            color = if (selected) itemAccent else colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dimensional Obsidian Glass Surface (`MrsadSurface`).
 */
@Composable
fun MrsadSurface(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    accent: Color? = null,
    shape: Shape? = null,
    contentPadding: PaddingValues = PaddingValues(MrsadTokens.Spacing.md),
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val resolvedShape = shape ?: RoundedCornerShape(MrsadTokens.Radius.lg)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed && motion.animationsEnabled) 0.985f else 1f,
        animationSpec = motion.spatialFast(),
        label = "surface_scale",
    )

    val baseBg = if (elevated) colors.surfaceHigh else colors.surface
    val containerColor = if (accent != null) {
        lerp(baseBg, accent, if (elevated) 0.11f else 0.06f)
    } else {
        baseBg
    }
    val borderColor = if (accent != null) {
        accent.copy(alpha = if (elevated) 0.55f else 0.36f)
    } else if (elevated) {
        colors.borderStrong
    } else {
        colors.border
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = clickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(resolvedShape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = resolvedShape)
            .then(clickableModifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        content = content,
    )
}

@Composable
fun MrsadReveal(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit,
) {
    val motion = MrsadTheme.motion
    if (MrsadTestHooks.isDeterministic || !motion.animationsEnabled) {
        Box(modifier = modifier) { content() }
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        if (index > 0) delay((index.coerceAtMost(6) * 28L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = motion.effectsDefault()) +
            slideInVertically(
                animationSpec = motion.spatialDefault(),
                initialOffsetY = { fullHeight -> (fullHeight * 0.08f).toInt().coerceAtLeast(8) },
            ),
    ) {
        content()
    }
}

@Composable
fun MrsadPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && motion.animationsEnabled) 0.975f else 1f,
        animationSpec = motion.spatialFast(),
        label = "primary_btn_scale",
    )

    val resolvedAccent = accentColor ?: colors.accent
    val fillColor = if (enabled) {
        lerp(ObsidianSpectrum.ElectricBlue, resolvedAccent, 0.55f)
    } else {
        colors.disabled
    }

    Row(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fillColor)
            .border(
                width = 1.dp,
                color = if (enabled) ObsidianSpectrum.SoftWhite.copy(alpha = 0.42f) else colors.border,
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = MrsadTokens.Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (enabled) ObsidianSpectrum.SoftWhite else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        Text(
            text = text,
            style = type.label.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) ObsidianSpectrum.SoftWhite else colors.textMuted,
        )
    }
}

@Composable
fun MrsadSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && motion.animationsEnabled) 0.975f else 1f,
        animationSpec = motion.spatialFast(),
        label = "secondary_btn_scale",
    )
    val borderAccent = accentColor?.copy(alpha = 0.52f) ?: colors.borderStrong

    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.surfaceHigh.copy(alpha = 0.90f))
            .border(1.dp, if (enabled) borderAccent else colors.border, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = MrsadTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (enabled) (accentColor ?: colors.textPrimary) else colors.disabled,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        Text(
            text = text,
            style = type.label,
            color = if (enabled) colors.textPrimary else colors.disabled,
        )
    }
}

@Composable
fun MrsadIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MrsadTheme.colors.textPrimary,
) {
    val colors = MrsadTheme.colors
    val shape = RoundedCornerShape(MrsadTokens.Radius.md)
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(shape)
            .background(colors.surfaceHigh.copy(alpha = 0.85f))
            .border(1.dp, tint.copy(alpha = 0.36f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun MrsadChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color = MrsadTheme.colors.accent,
    onClick: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    val bg = if (selected) tint.copy(alpha = 0.24f) else tint.copy(alpha = 0.12f)
    val border = if (selected) tint.copy(alpha = 0.80f) else tint.copy(alpha = 0.36f)
    val textColor = if (selected) colors.textPrimary else tint
    val clickMod = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .then(clickMod)
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = text,
            style = type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun MrsadProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    tint: Color = MrsadTheme.colors.accent,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = if (motion.animationsEnabled && !MrsadTestHooks.isDeterministic) {
            tween(durationMillis = motion.mediumMillis, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 0)
        },
        label = "progress",
    )
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(colors.backgroundElevated)
            .border(0.5.dp, colors.border, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(shape)
                .background(tint),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accent: Color = MrsadTheme.colors.accent,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent),
            )
            Text(
                text = title,
                style = type.title.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
        if (actionLabel != null) {
            val actionMod = if (onAction != null) {
                Modifier
                    .clip(RoundedCornerShape(MrsadTokens.Radius.xs))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = MrsadTokens.Spacing.xs, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = MrsadTokens.Spacing.xs, vertical = 4.dp)
            }
            Text(
                text = actionLabel,
                style = type.label,
                color = accent,
                modifier = actionMod,
            )
        }
    }
}

@Composable
fun MetricValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: TextUnit = 28.sp,
    accent: Color = MrsadTheme.colors.textPrimary,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = type.metric.copy(
                    fontFamily = MrsadNumericFamily,
                    fontSize = size,
                    lineHeight = size * 1.15f,
                    fontWeight = FontWeight.Bold,
                ),
                color = accent,
                maxLines = 1,
            )
            if (!unit.isNullOrBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = type.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Text(
            text = label,
            style = type.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MrsadDivider(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.border),
    )
}

@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    color: Color = ObsidianSpectrum.Aqua,
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
