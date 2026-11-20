package dev.mrsad.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadTheme

/**
 * MRSAD design tokens (spec §6): spacing, radii, elevation and material specs.
 * Feature code must use these — no ad-hoc dp literals for structure.
 */
object MrsadTokens {
    object Spacing {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val xxl = 48.dp
    }

    object Radius {
        val sm = 10.dp
        val md = 16.dp
        val lg = 22.dp
        val xl = 28.dp
        val pill = 100.dp
    }

    /**
     * The minimum size of a touch target.
     *
     * Compose gives a control a touch target exactly as big as the control
     * itself, so a 22dp icon is a 22dp target — far below the 48dp the
     * Material accessibility guidance asks for. Every interactive element in
     * the app reserves at least this much in both directions.
     */
    val TouchTarget = 48.dp

    object Motion {
        const val fast = 140
        const val normal = 240
        const val slow = 420
        const val cinematic = 760
    }
}

/**
 * The core surface material: brushed glass over obsidian with a 1dp silver
 * hairline and a very subtle top sheen. Used for every card/panel.
 */
@Composable
fun MrsadSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(MrsadTokens.Radius.lg),
    elevated: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MrsadTheme.colors
    val surfaceColor = if (elevated) colors.surfaceHigh else colors.surface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(MrsadTokens.Motion.fast), label = "press")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surfaceColor.copy(alpha = if (colors.isLight) 0.98f else 0.94f),
                        surfaceColor.copy(alpha = if (colors.isLight) 0.92f else 0.82f),
                    )
                )
            )
            .border(
                BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: colors.border),
                shape,
            )
            .drawBehind {
                // Top sheen: one hairline of light, the "material" signal.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf((accent ?: colors.metalLight).copy(alpha = 0.10f), Color.Transparent),
                        startY = 0f,
                        endY = 96f,
                    )
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(color = colors.metalLight),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(MrsadTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        content = content,
    )
}

/** Large numeric readout (tabular figures, metal gradient text). */
@Composable
fun MetricValue(
    value: String,
    label: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    size: androidx.compose.ui.unit.TextUnit? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = size?.let { type.metric.copy(fontSize = it) } ?: type.metric,
            color = accent ?: colors.textPrimary,
        )
        if (label != null) {
            Text(
                text = label,
                style = type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Section header with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = type.title, color = colors.textPrimary)
        if (actionLabel != null && onAction != null) {
            // The label stays small, but the tap target is a full 48dp.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
                    .clickable(onClick = onAction)
                    .heightIn(min = MrsadTokens.TouchTarget)
                    .padding(horizontal = MrsadTokens.Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, style = type.label, color = colors.accent)
            }
        }
    }
}

/** Primary action: metallic fill, obsidian label — the "commit" affordance. */
@Composable
fun MrsadPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(MrsadTokens.Motion.fast), label = "btn")

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
            .background(
                if (enabled) {
                    Brush.verticalGradient(colors.metalGradient)
                } else {
                    Brush.verticalGradient(listOf(colors.disabled, colors.disabled))
                }
            )
            .heightIn(min = MrsadTokens.TouchTarget)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = colors.background),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = MrsadTokens.Spacing.lg, vertical = MrsadTokens.Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (colors.isLight) Color.White else colors.background,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        Text(
            text = text,
            style = type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = type.body.fontSize),
            color = if (enabled) (if (colors.isLight) Color.White else colors.background) else colors.textMuted,
        )
    }
}

/** Secondary action: outlined silver hairline. */
@Composable
fun MrsadSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
            .border(1.dp, if (enabled) colors.borderStrong else colors.disabled, RoundedCornerShape(MrsadTokens.Radius.pill))
            .heightIn(min = MrsadTokens.TouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.lg, vertical = MrsadTokens.Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) colors.textPrimary else colors.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        Text(
            text,
            style = type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = type.body.fontSize),
            color = if (enabled) colors.textPrimary else colors.textMuted,
        )
    }
}

/** Small status chip (metric, tag, streak). */
@Composable
fun MrsadChip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val color = tint ?: colors.textSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(MrsadTokens.Radius.pill))
            .then(
                if (onClick != null) {
                    Modifier
                        .heightIn(min = MrsadTokens.TouchTarget)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xxs),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MrsadTheme.type.caption.copy(color = color))
    }
}

/** Progress bar with a metallic leading edge. Real values only. */
@Composable
fun MrsadProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    height: Dp = 6.dp,
) {
    val colors = MrsadTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(clamped, tween(MrsadTokens.Motion.normal), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf((tint ?: colors.metalDark), tint ?: colors.metalLight)
                    )
                )
        )
    }
}

/** Empty state — never a broken screen (spec §28). */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MrsadTokens.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Text(title, style = type.title, color = colors.textPrimary)
        Text(body, style = type.body, color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            MrsadSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    // An indeterminate spinner animates forever and would keep an instrumented
    // test from ever reaching idle, so the deterministic mode draws a static
    // ring instead.
    val deterministic = MrsadTestHooks.isDeterministic
    Box(modifier.fillMaxWidth().padding(MrsadTokens.Spacing.xl), contentAlignment = Alignment.Center) {
        if (deterministic) {
            Canvas(Modifier.size(28.dp)) {
                drawCircle(
                    color = colors.metalMid.copy(alpha = 0.35f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        } else {
            CircularProgressIndicator(
                color = colors.metalMid,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    Column(
        modifier = modifier.padding(MrsadTokens.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
    ) {
        Text(message, style = MrsadTheme.type.body, color = colors.error)
        if (onRetry != null) MrsadSecondaryButton(text = "تلاش دوباره", onClick = onRetry)
    }
}

/** Keyboard-focusable metric tile used across Home / Analytics. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    MrsadSurface(modifier = modifier) {
        CompositionLocalProvider(LocalContentColor provides MrsadTheme.colors.textPrimary) {
            MetricValue(value = value, label = label, accent = tint, size = MrsadTheme.type.headline.fontSize)
        }
    }
}

/** Thin silver divider. */
@Composable
fun MrsadDivider(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, colors.border, Color.Transparent)
                )
            )
    )
}

/** Animated equalizer-like activity marker used for "live" states. */
@Composable
fun LiveDot(color: Color = MrsadTheme.colors.accent, modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    val animated = animateColorAsState(
        targetValue = if (colors.isLight) color else color.copy(alpha = 0.6f),
        animationSpec = tween(1200),
        label = "live",
    )
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(animated.value)
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = size.minDimension * 1.6f,
                )
            }
    )
}

/** Decorative corner accent used on premium cards (kept cheap: 2 lines). */
internal fun Modifier.cornerAccent(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val len = 14.dp.toPx()
    val radius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke, stroke),
        size = Size(len, stroke),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke, stroke),
        size = Size(stroke, len),
    )
}
