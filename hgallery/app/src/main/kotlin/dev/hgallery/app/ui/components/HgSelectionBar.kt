package dev.hgallery.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Selection

/**
 * The selection header, built to reference R3.
 *
 * R3 shows, left to right: a close glyph, then the count as the title
 * ("3 items selected"). The title replaces the screen title rather than sitting
 * under it, so the grid does not jump when selection starts.
 */
@Composable
fun HgSelectionHeader(
    selection: Selection,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(HgMetrics.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HgIconButton(
            icon = HgIcons.Close,
            contentDescription = stringResource(R.string.exit_selection),
            onClick = onExit,
            modifier = Modifier.testTag(HgTestTags.EXIT_SELECTION),
        )
        Text(
            text = pluralStringResource(R.plurals.items_selected, selection.count, selection.count),
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 12.dp)
                .testTag(HgTestTags.SELECTION_COUNT),
        )
    }
}

/**
 * The bottom action row for a selection.
 *
 * Order follows the reference's own batch row (R3: restore / delete / select
 * all): the destructive action is separated from the ordinary ones, and
 * "select all" stays reachable so a user can act on a whole day at once.
 */
@Composable
fun HgSelectionActions(
    selection: Selection,
    allIds: Collection<Long>,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val allSelected = selection.isAllSelected(allIds)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.pageAlt)
            .navigationBarsPadding(),
    ) {
        HgDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SelectionAction(
                icon = HgIcons.Share,
                label = stringResource(R.string.share),
                enabled = !selection.isEmpty,
                onClick = onShare,
                tint = palette.textPrimary,
                tag = HgTestTags.SELECTION_SHARE,
            )
            SelectionAction(
                icon = if (allSelected) HgIcons.Check else HgIcons.SelectAll,
                label = stringResource(if (allSelected) R.string.select_none else R.string.select_all),
                enabled = allIds.isNotEmpty(),
                onClick = onToggleSelectAll,
                tint = palette.textPrimary,
                tag = HgTestTags.SELECTION_SELECT_ALL,
            )
            SelectionAction(
                icon = HgIcons.Delete,
                label = stringResource(R.string.delete),
                enabled = !selection.isEmpty,
                onClick = onDelete,
                tint = palette.danger,
                tag = HgTestTags.SELECTION_DELETE,
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color,
    tag: String,
) {
    val palette = hgPalette
    val effective = if (enabled) tint else palette.textTertiary
    Column(
        modifier = Modifier
            .width(96.dp)
            
            .testTag(tag)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effective,
            modifier = Modifier.size(HgMetrics.ToolbarIcon),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effective,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The heart on a favourited tile.
 *
 * Drawn as a white glyph with a soft shadow rather than a coloured badge: favourites
 * are decor, not state that changes what a tap does, so the mark must not compete
 * with the selection tick that appears in the same corner. It stays visible while a
 * selection runs, because "which of these are favourites" is exactly the question a
 * batch operation raises.
 */
@Composable
fun FavouriteBadge(favourite: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = favourite,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        Icon(
            imageVector = HgIcons.FavoriteFilled,
            contentDescription = stringResource(R.string.favourite_badge),
            tint = Color.White,
            modifier = Modifier
                .size(14.dp)
                .shadow(elevation = 2.dp, shape = CircleShape),
        )
    }
}

/** The tick drawn on a selected tile, as in R3: a filled blue circle with a white check. */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = selected,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(hgPalette.accent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HgIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
