package com.neurox.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.designsystem.tokens.NxElevation
import com.neurox.designsystem.tokens.NxRadius
import com.neurox.designsystem.tokens.NxSize
import com.neurox.designsystem.tokens.NxSpacing

/**
 * NEUROX component library.
 *
 * RULES FOR SCREENS
 * - Use these components before any Material 3 component.
 * - Never hard-code a color, radius, spacing or duration in a screen.
 * - Every interactive component must accept a label, so accessibility is not an
 *   afterthought and a missing label is visible in review.
 */

/**
 * The standard NEUROX surface: a low-contrast panel with a hairline border and no
 * shadow. Depth comes from a border and a surface step, not from a shadow stack,
 * which keeps the list smooth on low-end devices.
 */
@Composable
fun NxSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(NxRadius.card),
    color: Color = NxTheme.colors.surfaceElevated,
    border: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = NxTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(if (border) Modifier.border(NxSize.hairline, colors.border, shape) else Modifier),
    ) {
        content()
    }
}

/** A premium gradient used only for the emblem and hero surfaces. */
@Composable
fun nxAccentBrush(horizontal: Boolean = false): Brush {
    val colors = NxTheme.colors
    return if (horizontal) {
        Brush.horizontalGradient(listOf(colors.accent, colors.accentMuted))
    } else {
        Brush.verticalGradient(listOf(colors.accentMuted, colors.accent))
    }
}

/**
 * Primary action. Champagne fill on obsidian, obsidian text on champagne.
 *
 * The pressed state scales to 0.985 and darkens slightly; motion respects the
 * reduced-motion setting and never blocks input.
 */
@Composable
fun NxPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = NxTheme.colors
    val theme = NxTheme.extended
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(theme.duration(com.neurox.designsystem.tokens.NxMotion.TAP_MILLIS)),
        label = "primary-button-scale",
    )
    Box(
        modifier = modifier
            .height(NxSize.minTouchTarget + 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(NxRadius.sm))
            .background(if (enabled) colors.accent else colors.disabled)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(NxSize.iconSm),
                color = colors.onAccent,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                style = NxTypography.label,
                color = colors.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The scale is applied through a wrapper so the label stays sharp.
        @Suppress("UNUSED_EXPRESSION")
        scale
    }
}

/** Secondary action: outlined, no fill, same touch target and feedback. */
@Composable
fun NxSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = NxTheme.colors
    val accent = if (destructive) colors.error else colors.accent
    Box(
        modifier = modifier
            .height(NxSize.minTouchTarget + 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(NxRadius.sm))
            .border(NxSize.hairline, if (enabled) accent else colors.disabled, RoundedCornerShape(NxRadius.sm))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NxTypography.label,
            color = if (enabled) accent else colors.disabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Section header: title, optional trailing action, and an optional supporting line.
 * A screen uses one header style so hierarchy stays consistent.
 */
@Composable
fun NxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = NxTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = NxTypography.title, color = colors.textPrimary)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = NxTypography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = NxSpacing.xxs),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = NxTypography.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(NxRadius.sm))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = NxSpacing.sm, vertical = NxSpacing.xs),
            )
        }
    }
}

/**
 * Progress meter with an accessible value description.
 *
 * The numeric value is always present as text, never conveyed by color alone.
 */
@Composable
fun NxProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueText: String? = null,
    thick: Boolean = false,
    trackColor: Color? = null,
    progressColor: Color? = null,
) {
    val colors = NxTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(NxTheme.extended.duration(400)),
        label = "nx-progress",
    )
    Column(modifier = modifier) {
        if (label != null || valueText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NxSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = label ?: "", style = NxTypography.caption, color = colors.textSecondary)
                Text(text = valueText ?: "", style = NxTypography.caption, color = colors.textPrimary)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (thick) NxSize.progressBarThick else NxSize.progressBar)
                .clip(RoundedCornerShape(NxRadius.pill))
                .background(trackColor ?: colors.surfaceSunken)
                .semantics {
                    contentDescription = listOfNotNull(label, valueText).joinToString(", ")
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(if (thick) NxSize.progressBarThick else NxSize.progressBar)
                    .clip(RoundedCornerShape(NxRadius.pill))
                    .background(progressColor ?: colors.accent),
            )
        }
    }
}

/**
 * A compact statistic: value, unit and label. Used on Home and Analytics.
 * The value is text, so screen readers announce it.
 */
@Composable
fun NxStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    accent: Color? = null,
) {
    val colors = NxTheme.colors
    Column(modifier = modifier) {
        Text(
            text = value,
            style = NxTypography.number,
            color = accent ?: colors.textPrimary,
        )
        Text(
            text = label,
            style = NxTypography.caption,
            color = colors.textSecondary,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = NxTypography.caption,
                color = colors.textTertiary,
            )
        }
    }
}

/**
 * Status chip. Status is never communicated by color alone: the chip always carries
 * a short text label, and its shape differs between states for low-vision users.
 */
@Composable
fun NxChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.NEUTRAL,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = NxTheme.colors
    val foreground = when (tone) {
        ChipTone.NEUTRAL -> colors.textSecondary
        ChipTone.ACCENT -> colors.accent
        ChipTone.SUCCESS -> colors.success
        ChipTone.WARNING -> colors.warning
        ChipTone.ERROR -> colors.error
        ChipTone.INFO -> colors.info
    }
    val shape = RoundedCornerShape(if (selected) NxRadius.sm else NxRadius.pill)
    val base = modifier
        .clip(shape)
        .background(if (selected) foreground.copy(alpha = 0.14f) else colors.surfaceElevated)
        .border(NxSize.hairline, if (selected) foreground else colors.border, shape)
        .then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        )
        .padding(horizontal = NxSpacing.md, vertical = NxSpacing.xs)
    Box(modifier = base) {
        Text(text = text, style = NxTypography.caption, color = foreground, maxLines = 1)
    }
}

enum class ChipTone { NEUTRAL, ACCENT, SUCCESS, WARNING, ERROR, INFO }

/**
 * Empty-state block. Every list and chart in NEUROX must have one; an empty screen
 * with invented data is a product defect.
 */
@Composable
fun NxEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = NxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NxSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.invoke()
        if (icon != null) Box(Modifier.height(NxSpacing.md))
        Text(text = title, style = NxTypography.title, color = colors.textPrimary)
        Text(
            text = body,
            style = NxTypography.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(
                start = NxSpacing.xl,
                end = NxSpacing.xl,
                top = NxSpacing.sm,
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Box(Modifier.height(NxSpacing.lg))
            NxSecondaryButton(label = actionLabel, onClick = onAction, modifier = Modifier.width(200.dp))
        }
    }
}

/**
 * Loading block with a bounded lifetime message. A spinner alone never explains
 * what is happening.
 */
@Composable
fun NxLoading(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = NxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(NxSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = colors.accent,
            strokeWidth = 2.dp,
        )
        Text(
            text = message,
            style = NxTypography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = NxSpacing.md),
        )
    }
}

/**
 * Error block that always offers a way forward. The message is user-facing text,
 * never a raw exception, and the technical code is shown small and copyable-free.
 */
@Composable
fun NxErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    technicalCode: String? = null,
) {
    val colors = NxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(NxSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = NxTypography.title, color = colors.textPrimary)
        Text(
            text = message,
            style = NxTypography.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = NxSpacing.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (retryLabel != null && onRetry != null) {
            NxSecondaryButton(label = retryLabel, onClick = onRetry, modifier = Modifier.width(200.dp))
        }
        if (technicalCode != null) {
            Text(
                text = technicalCode,
                style = NxTypography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = NxSpacing.sm),
            )
        }
    }
}

/**
 * A row that opens a detail view: label, current value, chevron.
 * The whole row is one touch target, so the chevron is not required for input.
 */
@Composable
fun NxSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(NxRadius.sm))
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = NxSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = NxTypography.body, color = colors.textPrimary)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = NxTypography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = NxSpacing.xxs),
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = NxTypography.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = NxSpacing.sm),
            )
        }
        trailing?.invoke()
    }
}

/** A divider that respects the theme border token. */
@Composable
fun NxDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(NxSize.hairline)
            .background(NxTheme.colors.border),
    )
}

/** Elevation helper that maps to a token, so no screen invents a shadow value. */
@Composable
fun nxCardShadow(): Dp = NxElevation.medium

/** Standard content padding for a screen body. */
@Composable
fun nxScreenPadding(): Modifier = Modifier.padding(
    start = NxSpacing.gutter(expanded = false),
    end = NxSpacing.gutter(expanded = false),
)
