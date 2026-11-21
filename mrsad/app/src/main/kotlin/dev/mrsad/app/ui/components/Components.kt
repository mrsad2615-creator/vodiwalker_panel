package dev.mrsad.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

    // Durations, easings and springs do not live here: they are the
    // Material 3 motion tokens in `ui/theme/Motion.kt`, and every animation
    // in the app resolves them through `MrsadTheme.motion`.
}

private val DefaultChamferedShape: Shape = createChamferedShape(
    cut = 12.dp,
    radius = MrsadTokens.Radius.md,
)

/**
 * Hybrid cosmic corner shape: two opposing corners are softly rounded and
 * the other two are chamfered at 45 degrees, mirroring cleanly in RTL so
 * the card reads as machined titanium & smoked glass rather than a generic box.
 */
fun chamferedShape(
    cut: Dp = 12.dp,
    radius: Dp = MrsadTokens.Radius.md,
): Shape = if (cut == 12.dp && radius == MrsadTokens.Radius.md) {
    DefaultChamferedShape
} else {
    createChamferedShape(cut, radius)
}

private fun createChamferedShape(
    cut: Dp,
    radius: Dp,
): Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val maxCorner = size.minDimension / 2f
        val c = with(density) { cut.toPx() }.coerceAtMost(maxCorner)
        val r = with(density) { radius.toPx() }.coerceAtMost(maxCorner)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (layoutDirection == LayoutDirection.Rtl) {
                // Top-start (top-right) rounded, top-end (top-left) chamfered,
                // bottom-start (bottom-right) chamfered, bottom-end (bottom-left) rounded.
                moveTo(c, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2f * r, 0f, w, 2f * r), 270f, 90f, false)
                lineTo(w, h - c)
                lineTo(w - c, h)
                lineTo(r, h)
                arcTo(Rect(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
                lineTo(0f, c)
                close()
            } else {
                // Top-start (top-left) rounded, top-end (top-right) chamfered,
                // bottom-end (bottom-right) rounded, bottom-start (bottom-left) chamfered.
                moveTo(r, 0f)
                lineTo(w - c, 0f)
                lineTo(w, c)
                lineTo(w, h - r)
                arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
                lineTo(c, h)
                lineTo(0f, h - c)
                lineTo(0f, r)
                arcTo(Rect(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * The core surface material: smoked glass over obsidian.
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
    shape: Shape = if (MrsadTheme.colors.isNoir) RoundedCornerShape(2.dp) else chamferedShape(),
    elevated: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val isNoir = colors.isNoir
    val surfaceColor = if (elevated) colors.surfaceHigh else colors.surface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) (if (isNoir) 0.994f else 0.985f) else 1f,
        animationSpec = motion.spatialFast(),
        label = "surfacePress",
    )
    val highlight by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "surfaceHighlight",
    )

    val effectiveShape = if (isNoir) RoundedCornerShape(2.dp) else shape

    if (isNoir) {
        Column(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(effectiveShape)
                .background(surfaceColor)
                .border(
                    width = 1.dp,
                    color = accent ?: if (elevated) colors.borderStrong else colors.border,
                    shape = effectiveShape,
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = ripple(color = colors.textPrimary),
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(MrsadTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
            content = content,
        )
        return
    }

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 6.dp,
                shape = effectiveShape,
                clip = false,
            )
            .clip(effectiveShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (elevated) Color(0xFF1D2432) else surfaceColor.copy(alpha = 0.96f),
                        Color(0xFF0B0E15),
                    ),
                ),
            )
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            colors.navy.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.6f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            (accent ?: colors.metalLight).copy(alpha = 0.20f + 0.06f * highlight),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 96f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
                        startY = size.height - 92f,
                        endY = size.height,
                    ),
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        (accent ?: colors.metalLight).copy(alpha = if (accent != null) 0.56f else 0.48f),
                        (accent ?: colors.borderStrong).copy(alpha = 0.14f),
                    ),
                ),
                shape = effectiveShape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(color = colors.metalLight),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(MrsadTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs),
        content = content,
    )
}

/**
 * The entrance of a screen.
 *
 * Content that simply appears reads as a glitch; it arrives instead. One
 * animation drives the whole screen and the pieces of it arrive one after
 * the other, so the screen assembles itself instead of blinking into
 * existence.
 *
 * The progress belongs to the *screen*, not to the pieces: scrolling a
 * piece out of the viewport and back does not replay its entrance.
 */
@Composable
fun rememberRevealProgress(): Animatable<Float, AnimationVector1D> {
    val motion = MrsadTheme.motion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motion.deterministic || motion.reducedMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, motion.spatialDefault())
    }
    return progress
}

/**
 * A piece of content that arrives with the screen.
 *
 * The movement is spatial, so it uses a spatial spring; the fade is an
 * effect. Under reduced motion — and inside instrumentation — the content
 * is simply there.
 *
 * @param progress the entrance of the screen, from [rememberRevealProgress]
 * @param index the place of the piece in the order of the arrival
 */
@Composable
fun MrsadReveal(
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.graphicsLayer {
            val total = 1f + index * REVEAL_STAGGER
            val local = ((progress.value * total) - index * REVEAL_STAGGER).coerceIn(0f, 1f)
            alpha = local
            translationY = (1f - local) * REVEAL_TRAVEL.toPx()
        }
    ) {
        content()
    }
}

/** How much later each piece of a screen arrives than the one before it. */
private const val REVEAL_STAGGER = 0.16f

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

/** Primary action: metallic fill in OBSIDIAN, pure white architectural slab in NOIR. */
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
    val isNoir = colors.isNoir
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motion.spatialFast(),
        label = "btn",
    )
    val sheen by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "btnSheen",
    )
    val spokenLabel = if (text.isBlank()) iconContentDescription else null
    val shape = if (isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.pill)

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isNoir) Modifier else Modifier.shadow(elevation = 8.dp, shape = shape, clip = false),
            )
            .clip(shape)
            .background(
                if (enabled) {
                    if (isNoir) {
                        Brush.verticalGradient(listOf(colors.textPrimary, colors.textPrimary))
                    } else {
                        Brush.verticalGradient(colors.metalGradient)
                    }
                } else {
                    Brush.verticalGradient(listOf(colors.disabled, colors.disabled))
                },
            )
            .drawBehind {
                if (!isNoir) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f + 0.18f * sheen),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 22f,
                        ),
                    )
                }
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
                contentDescription = spokenLabel,
                tint = colors.background,
                modifier = Modifier.size(18.dp),
            )
            if (text.isNotBlank()) Spacer(Modifier.width(MrsadTokens.Spacing.xs))
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = type.label.copy(fontWeight = FontWeight.SemiBold, fontSize = type.body.fontSize),
                color = if (enabled) colors.background else colors.textMuted,
            )
        }
    }
}

/** Secondary action: outlined silver hairline in OBSIDIAN, sharp 1dp editorial box in NOIR. */
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
    val shape = if (colors.isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.pill)
    val spokenLabel = if (text.isBlank()) iconContentDescription else null
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, if (enabled) colors.borderStrong else colors.disabled, shape)
            .heightIn(min = MrsadTokens.TouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MrsadTokens.Spacing.lg, vertical = MrsadTokens.Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
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
    val shape = if (colors.isNoir) RoundedCornerShape(2.dp) else RoundedCornerShape(MrsadTokens.Radius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (colors.isNoir) colors.backgroundElevated else color.copy(alpha = 0.10f))
            .border(1.dp, if (colors.isNoir) colors.borderStrong else color.copy(alpha = 0.28f), shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .heightIn(min = MrsadTokens.TouchTarget)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = MrsadTokens.Spacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xxs),
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MrsadTheme.type.caption.copy(color = color))
    }
}

/** Progress bar: metallic gradient in OBSIDIAN, segmented Swiss tick-bar in NOIR. */
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
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = motion.spatialDefault(),
        label = "progress",
    )

    if (colors.isNoir) {
        val segments = 12
        val activeSegments = (animated * segments).toInt().coerceIn(0, segments)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until segments) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .background(if (i < activeSegments) (tint ?: colors.textPrimary) else colors.border),
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf((tint ?: colors.metalDark), tint ?: colors.metalLight),
                    ),
                ),
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

/** Thin silver divider in OBSIDIAN, full-bleed 1dp architectural rule in NOIR. */
@Composable
fun MrsadDivider(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    if (colors.isNoir) {
        Box(
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, colors.border, Color.Transparent),
                ),
            ),
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
