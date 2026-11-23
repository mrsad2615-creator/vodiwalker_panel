package dev.hgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import androidx.compose.ui.res.stringResource
import dev.hgallery.app.R

/** Space reserved for the status bar, since every screen draws edge to edge. */
val statusBarPadding: androidx.compose.foundation.layout.PaddingValues
    @Composable get() = WindowInsets.statusBars.asPaddingValues()

/**
 * The large left-aligned screen title from the reference, with its optional
 * centred count line underneath ("372 photos and 19 videos").
 *
 * It scrolls away with the content rather than sitting in a bar: in the
 * reference the title belongs to the page, not to a persistent header.
 */
@Composable
fun HgScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleCentred: Boolean = false,
) {
    val palette = hgPalette
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (subtitleCentred) Modifier else Modifier.padding(start = HgMetrics.Gutter),
                    ),
                textAlign = if (subtitleCentred) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** A bare 48 dp icon button that matches the reference's optical weight. */
@Composable
fun HgIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val palette = hgPalette
    Box(
        modifier = modifier
            .size(HgMetrics.MinTouchTarget)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                // Not implemented controls are disabled at their call sites. Saying
                // so is part of the control: a greyed icon whose accessibility name
                // still promises an action is misleading to the one user who cannot
                // see that it is grey.
                this.contentDescription =
                    if (enabled) contentDescription else "$contentDescription, unavailable"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: palette.textPrimary,
            modifier = Modifier.size(HgMetrics.ToolbarIcon),
        )
    }
}

/** Top row of icon actions, as in "Photos" and "Albums". */
@Composable
fun HgToolbar(
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    viewAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(HgMetrics.MinTouchTarget),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewAction?.invoke()
        if (onSearch != null) {
            HgIconButton(HgIcons.Search, stringResource(R.string.search), onSearch)
        }
        if (onMore != null) {
            HgIconButton(HgIcons.More, stringResource(R.string.more_options), onMore)
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Small centred header with an optional trailing action, e.g. MORE ALBUMS + ADD ALBUM. */
@Composable
fun HgSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val palette = hgPalette
    // The row is as tall as the 48 dp touch target it has to contain. Forcing a
    // shorter row here clipped the descenders of the trailing action, which is
    // visible in a screenshot long before it is visible in code.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HgMetrics.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            // A section action that cannot act is drawn greyed and says so, rather
            // than in the accent colour as though it worked.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 12.dp)
                    .semantics {
                        role = Role.Button
                        if (!enabled) contentDescription = "$action, unavailable"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = if (enabled) palette.accent else palette.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Hairline divider at the reference's weight. */
@Composable
fun HgDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.6.dp).background(hgPalette.divider))
}

/** Bottom inset for a screen whose content runs under the gesture bar. */
val navigationBarPadding: androidx.compose.foundation.layout.PaddingValues
    @Composable get() = WindowInsets.navigationBars.asPaddingValues()
