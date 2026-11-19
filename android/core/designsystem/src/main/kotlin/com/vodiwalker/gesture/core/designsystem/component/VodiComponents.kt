package com.vodiwalker.gesture.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Layered, restrained container used for every grouped surface in the app.
 *
 * Replaces the earlier glass-everywhere treatment: a single low-contrast container tone with an
 * outline hairlined at 12% keeps hierarchy legible in both schemes while satisfying the 3:1
 * non-text contrast requirement for boundaries.
 */
@Composable
fun VodiSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            content()
        }
    }
}

/**
 * Primary action: exactly one per screen, full width on phones, 56dp tall for a comfortable
 * thumb target, and the largest type in the layout below the screen title.
 */
@Composable
fun VodiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/**
 * Secondary action: same target size, no fill, so it never competes with the primary button.
 */
@Composable
fun VodiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Status pill: colour + label + optional dot. Colour is never the only signal, so it stays
 * accessible for colour-blind users and in high-contrast mode.
 */
@Composable
fun VodiStatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = when (tone) {
            StatusTone.Ready -> MaterialTheme.colorScheme.primaryContainer
            StatusTone.Attention -> MaterialTheme.colorScheme.tertiaryContainer
            StatusTone.Paused -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "status_pill_container",
    )
    val content = when (tone) {
        StatusTone.Ready -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Attention -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.Paused -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.testTag("status_pill"),
        shape = CircleShape,
        color = container,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(content, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Semantic status tones; screens map their state once instead of picking raw colours. */
enum class StatusTone { Ready, Attention, Paused }

/**
 * Statistic block used on the home screen: a number the user cares about, a plain-language label
 * underneath, and nothing else. Numbers must already be Persian-formatted by the caller.
 */
@Composable
fun VodiStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = tint,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Empty state: one illustration slot, a short headline, one sentence of guidance, one action.
 * Shown instead of a bare blank list so a new user always knows the next step.
 */
@Composable
fun VodiEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(16.dp)
                    .size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, shape = MaterialTheme.shapes.large) {
                Text(actionLabel, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/**
 * Screen scaffold: consistent 20dp gutters, a large title, optional subtitle, and a scrolled
 * content slot. Edge-to-edge insets are consumed by the caller's scaffold.
 */
@Composable
fun VodiScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}
