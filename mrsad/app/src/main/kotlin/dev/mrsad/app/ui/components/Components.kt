package dev.mrsad.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.delay
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

    // Durations, easings and springs do not live here: they are the
    // Material 3 motion tokens in `ui/theme/Motion.kt`, and every animation
    // in the app resolves them through `MrsadTheme.motion`.
}

/**
 * The core surface material.
 *
 * A card is not a rectangle with a border, it is a lit object. The
 * material is built in five layers, from the back to the front:
 *
 *  1. a soft shadow that lifts the card off the page,
 *  2. a vertical gradient, so the card is brighter where the light falls
 *     on it and darker where it does not,
 *  3. a sheen along the top edge,
 *  4. a shade along the bottom edge, where the card rests on something,
 *  5. a hairline that fades out towards the bottom instead of ringing the
 *     card like a fence.
 *
 * The press is a *spatial* movement — the card physically moves — so it
 * uses a spatial spring, and the change of the surface brightness is an
 * *effect*, so it uses an effects spring. The two families are never
 * mixed.
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
    val motion = MrsadTheme.motion
    val surfaceColor = if (elevated) colors.surfaceHigh else colors.surface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = motion.spatialFast(),
        label = "surfacePress",
    )
    val highlight by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "surfaceHighlight",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surfaceColor.copy(alpha = if (colors.isLight) 0.99f else 0.97f),
                        surfaceColor.copy(alpha = if (colors.isLight) 0.93f else 0.84f),
                    )
                )
            )
            .drawBehind {
                // The sheen along the top edge: the light is above.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            (accent ?: colors.metalLight).copy(alpha = 0.13f + 0.05f * highlight),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 108f,
                    )
                )
                // The shade along the bottom edge: the card rests on
                // something, and that something casts back.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = if (colors.isLight) 0.05f else 0.26f)),
                        startY = size.height - 96f,
                        endY = size.height,
                    )
                )
            }
            .border(
                Brush.verticalGradient(
                    listOf(
                        (accent ?: colors.borderStrong).copy(alpha = if (accent != null) 0.5f else 0.42f),
                        (accent ?: colors.borderStrong).copy(alpha = 0.10f),
                    )
                ),
                shape,
            )
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

/**
 * The entrance of a piece of content.
 *
 * Content that simply appears reads as a glitch; it arrives instead. It
 * fades in while it moves a short distance upwards, and the pieces of a
 * screen arrive one after the other instead of all at the same instant.
 *
 * The movement is spatial, so it uses a spatial spring; the fade is an
 * effect, so it uses an effects spring. Under reduced motion — and inside
 * instrumentation — the content is simply there.
 */
@Composable
fun MrsadReveal(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit,
) {
    val motion = MrsadTheme.motion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motion.deterministic || motion.reducedMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // The stagger is short on purpose: a screen that takes a second to
        // assemble feels slow, a screen that assembles in a fifth of a
        // second feels alive.
        delay(index * REVEAL_STAGGER_MILLIS)
        progress.animateTo(1f, motion.spatialDefault())
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * REVEAL_TRAVEL.toPx()
        }
    ) {
        content()
    }
}

/** The delay between two pieces of content that arrive one after another. */
private const val REVEAL_STAGGER_MILLIS = 55L

/** How far a piece of content travels while it arrives. */
private val REVEAL_TRAVEL = 16.dp

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
    iconContentDescription: String? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The button moves, so the press is a spatial spring.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motion.spatialFast(),
        label = "btn",
    )
    // The sheen is an effect, so it uses an effects spring.
    val sheen by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "btnSheen",
    )
    // A button whose only content is an icon has to name itself, or a
    // screen reader announces an unlabelled control. When there is a
    // visible label the icon is decoration and must stay silent.
    val spokenLabel = if (text.isBlank()) iconContentDescription else null
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.verticalGradient(colors.metalGradient)
                } else {
                    Brush.verticalGradient(listOf(colors.disabled, colors.disabled))
                }
            )
            .drawBehind {
                // The lit top edge of the metal.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f + 0.18f * sheen),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 22f,
                    )
                )
            }
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
                // A clickable merges its descendants' semantics, so an
                // icon-only button names itself through the icon.
                contentDescription = spokenLabel,
                tint = if (colors.isLight) Color.White else colors.background,
                modifier = Modifier.size(18.dp),
            )
            if (text.isNotBlank()) Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = type.body.fontSize),
                color = if (enabled) (if (colors.isLight) Color.White else colors.background) else colors.textMuted,
            )
        }
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
    iconContentDescription: String? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    // A button whose only content is an icon has to name itself, or a
    // screen reader announces an unlabelled control. When there is a
    // visible label the icon is decoration and must stay silent.
    val spokenLabel = if (text.isBlank()) iconContentDescription else null
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
            // A clickable merges its descendants' semantics, so an
            // icon-only button names itself through the icon.
            Icon(
                icon,
                spokenLabel,
                tint = if (enabled) colors.textPrimary else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            if (text.isNotBlank()) Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        if (text.isNotBlank()) {
            Text(
                text,
                style = type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = type.body.fontSize),
                color = if (enabled) colors.textPrimary else colors.textMuted,
            )
        }
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
    val motion = MrsadTheme.motion
    val clamped = progress.coerceIn(0f, 1f)
    // The bar grows, so it moves in space: a spatial spring.
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = motion.spatialDefault(),
        label = "progress",
    )
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
    val motion = MrsadTheme.motion
    val animated = animateColorAsState(
        targetValue = if (colors.isLight) color else color.copy(alpha = 0.6f),
        animationSpec = motion.effectsDefault(),
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
