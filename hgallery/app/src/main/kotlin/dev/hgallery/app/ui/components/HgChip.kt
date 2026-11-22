package dev.hgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.hgPalette

/**
 * A selectable filter chip.
 *
 * The reference's search screen uses filled pills with no outline, and marks the
 * chosen one with the accent colour rather than a tick — a row of ticks reads as a
 * checklist, which these are not. The selected colour is carried by the text and
 * a tint, so the chip's shape does not change when it is pressed.
 *
 * `selected` is exposed as a semantics property rather than only as colour:
 * colour alone is not a state a screen reader can announce.
 */
@Composable
fun HgChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Box(
        modifier = modifier
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accentMuted else palette.chipBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.accent else palette.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * A chip whose label carries a dismissal glyph, for filters that are set to a
 * value the user picked from a list (an album, a size range).
 *
 * Kept separate from [HgChip] because the affordance is different: a selected
 * boolean chip is un-pressed by pressing it again, while a chip showing "Camera"
 * needs to say that pressing it clears the album.
 */
@Composable
fun HgRemovableChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.accentMuted)
            .clickable(role = Role.Button, onClick = onRemove)
            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            maxLines = 1,
        )
        Icon(
            imageVector = HgIcons.Close,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.padding(start = 4.dp).size(14.dp),
        )
    }
}
