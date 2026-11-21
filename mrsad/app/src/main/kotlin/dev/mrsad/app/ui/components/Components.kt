package dev.mrsad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum

/**
 * OBSIDIAN SPECTRUM — Design Tokens & Core Component System (Master Prompt §6 & §13).
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
        val sm = 12.dp
        val md = 16.dp
        val lg = 22.dp
        val xl = 28.dp
        val pill = 100.dp
    }

    val TouchTarget = 48.dp
}

private val DefaultGlassShape: Shape = RoundedCornerShape(MrsadTokens.Radius.lg)

fun chamferedShape(
    cut: Dp = 12.dp,
    radius: Dp = MrsadTokens.Radius.lg,
): Shape = if (radius == MrsadTokens.Radius.lg) {
    DefaultGlassShape
} else {
    RoundedCornerShape(radius)
}

/**
 * OBSIDIAN SPECTRUM — Dimensional Glass & Metallic Card Surface (Master Prompt §6).
 *
 * Unified across the entire application so every card shares the same
 * `#211B2C` / `#181522` -> `#100E18` surface architecture with module-specific
 * spectral illumination (`accent`).
 */
@Composable
fun MrsadSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultGlassShape,
    elevated: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
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

    val topSurface = if (elevated) colors.surfaceHigh else colors.surface
    val bottomSurface = colors.backgroundElevated
    val spectralGlow = accent ?: colors.accent

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(topSurface, bottomSurface),
                ),
            )
            .drawBehind {
                // Controlled spectral + glass reflection along the top edge
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            spectralGlow.copy(alpha = (if (accent != null) 0.16f else 0.09f) + 0.08f * highlight),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 92f,
                    ),
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        (accent ?: colors.borderStrong).copy(alpha = if (elevated || accent != null) 0.62f else 0.42f),
                        colors.border.copy(alpha = 0.55f),
                    ),
                ),
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(color = spectralGlow),
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
 * OBSIDIAN SPECTRUM — Auto-Collapsing Top Bar (`نوار بالای جمع‌شوندهٔ خودکار`).
 *
 * Automatically collapses its subtitle and expanded footer row when the user
 * scrolls down (`collapsed == true`), and smoothly expands back when the user
 * scrolls up (`collapsed == false`).
 */
@Composable
fun ObsidianCollapsingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsed: Boolean = false,
    spectralColor: Color = MrsadTheme.colors.accent,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    expandedFooter: (@Composable () -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion

    val collapseFraction by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = motion.spatialDefault(),
        label = "topBarCollapse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background.copy(alpha = 0.96f),
                        colors.backgroundElevated.copy(alpha = 0.88f * collapseFraction),
                    ),
                ),
            )
            .padding(
                horizontal = MrsadTokens.Spacing.md,
                vertical = if (collapsed) 6.dp else 10.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(MrsadTokens.TouchTarget)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.sm))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(MrsadTokens.Radius.sm))
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
            }

            if (leading != null) {
                leading()
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (collapsed) {
                        type.title.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    } else {
                        type.headline.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    },
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = !collapsed && subtitle != null,
                    enter = fadeIn(animationSpec = motion.effectsDefault()) +
                        expandVertically(animationSpec = motion.spatialDefault()),
                    exit = fadeOut(animationSpec = motion.effectsFast()) +
                        shrinkVertically(animationSpec = motion.spatialFast()),
                ) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = type.caption,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xxs),
                    content = trailing,
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsed && expandedFooter != null,
            enter = fadeIn(animationSpec = motion.effectsDefault()) +
                expandVertically(animationSpec = motion.spatialDefault()),
            exit = fadeOut(animationSpec = motion.effectsFast()) +
                shrinkVertically(animationSpec = motion.spatialFast()),
        ) {
            Column {
                Spacer(Modifier.height(MrsadTokens.Spacing.xs))
                expandedFooter?.invoke()
            }
        }

        // Subtle bottom spectral divider line when collapsed or pinned
        Box(
            Modifier
                .padding(top = if (collapsed) 6.dp else 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            spectralColor.copy(alpha = 0.42f),
                            colors.border.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )
    }
}

/**
 * OBSIDIAN SPECTRUM — Unified Segmented Section Bar (`نوار بخش‌های جداگانه`).
 *
 * Groups separate functional sections or options inside a single dimensional
 * Obsidian bar with an animated active spectral pill.
 */
data class SegmentedBarItem(
    val id: String,
    val label: String,
    val accent: Color? = null,
    val icon: List<IconFacet>? = null,
)

@Composable
fun ObsidianSegmentedBar(
    items: List<SegmentedBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
    val barShape = RoundedCornerShape(MrsadTokens.Radius.md)
    val itemShape = RoundedCornerShape(MrsadTokens.Radius.sm)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(colors.backgroundElevated)
            .border(1.dp, colors.border, barShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            val itemAccent = item.accent ?: colors.accent
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = motion.spatialDefault(),
                label = "segBg_${item.id}",
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) colors.textPrimary else colors.textSecondary,
                animationSpec = motion.effectsDefault(),
                label = "segText_${item.id}",
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(itemShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.surfaceHigh.copy(alpha = bgAlpha),
                                colors.surface.copy(alpha = bgAlpha),
                            ),
                        ),
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        itemAccent.copy(alpha = 0.72f),
                                        colors.borderStrong.copy(alpha = 0.45f),
                                    ),
                                ),
                                shape = itemShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab) { onSelect(item.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.icon != null) {
                    MetalIcon(
                        facets = item.icon,
                        accentOverride = itemAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = item.label,
                    style = type.label.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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

private const val REVEAL_STAGGER = 0.16f
private val REVEAL_TRAVEL = 16.dp

/** Large numeric readout (tabular figures, futuristic display typography). */
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

/** Primary action button with Obsidian Spectrum luminous fill. */
@Composable
fun MrsadPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    accentColor: Color? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val motion = MrsadTheme.motion
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
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    val primaryFill = accentColor ?: colors.accent

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            colors.textPrimary,
                            primaryFill,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(listOf(colors.disabled, colors.disabled))
                },
            )
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.24f + 0.16f * sheen),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 24f,
                    ),
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

/** Secondary action button with Obsidian glass fill and precision border. */
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
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    val spokenLabel = if (text.isBlank()) iconContentDescription else null
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceHigh.copy(alpha = 0.65f))
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

/** Small status chip (metric, tag, streak) with spectral border illumination. */
@Composable
fun MrsadChip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MrsadTheme.colors
    val color = tint ?: colors.accent
    val shape = RoundedCornerShape(MrsadTokens.Radius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.42f), shape)
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
        Text(text, style = MrsadTheme.type.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
    }
}

/** Progress bar with Obsidian Spectrum horizontal gradient illumination. */
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
    val endColor = tint ?: colors.accent
    val startColor = when (endColor) {
        ObsidianSpectrum.BrightBlue -> ObsidianSpectrum.ElectricBlue
        ObsidianSpectrum.Lavender -> ObsidianSpectrum.DeepViolet
        ObsidianSpectrum.Aqua -> ObsidianSpectrum.Teal
        ObsidianSpectrum.Pink -> ObsidianSpectrum.Magenta
        else -> ObsidianSpectrum.ElectricBlue
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
                        listOf(startColor, endColor),
                    ),
                ),
        )
    }
}

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
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
        }
        Text(title, style = type.title, color = colors.textPrimary)
        Text(body, style = type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            MrsadSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    val deterministic = MrsadTestHooks.isDeterministic
    Box(modifier.fillMaxWidth().padding(MrsadTokens.Spacing.xl), contentAlignment = Alignment.Center) {
        if (deterministic) {
            Canvas(Modifier.size(28.dp)) {
                drawCircle(
                    color = colors.accent.copy(alpha = 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        } else {
            CircularProgressIndicator(
                color = colors.accent,
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

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    MrsadSurface(modifier = modifier, accent = tint) {
        CompositionLocalProvider(LocalContentColor provides MrsadTheme.colors.textPrimary) {
            MetricValue(value = value, label = label, accent = tint, size = MrsadTheme.type.headline.fontSize)
        }
    }
}

@Composable
fun MrsadDivider(modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(colors.borderStrong.copy(alpha = 0.55f), colors.border.copy(alpha = 0.25f)),
                ),
            ),
    )
}

@Composable
fun LiveDot(color: Color = MrsadTheme.colors.accent, modifier: Modifier = Modifier) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val animated = animateColorAsState(
        targetValue = if (colors.isLight) color else color.copy(alpha = 0.75f),
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
                    color = color.copy(alpha = 0.28f),
                    radius = size.minDimension * 1.6f,
                )
            }
    )
}

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
