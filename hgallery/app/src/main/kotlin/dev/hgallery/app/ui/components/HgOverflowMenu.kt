package dev.hgallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.hgColors
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.app.ui.theme.hgSpacing
import androidx.compose.ui.res.stringResource

/**
 * The home screen's overflow menu.
 *
 * ## Why the menu exists
 *
 * The redesigned title row has room for two controls beside a 32 sp title on a
 * 320 dp screen. The screen has four actions. Drawing all four squeezed the title
 * to one letter per line; moving two onto the chip row instead squeezed the chips
 * until they collided with the buttons. Both failures were visible in emulator
 * screenshots and neither failed a test.
 *
 * A menu is the honest answer: two controls stay on the title row, and the two
 * that are genuinely secondary - select-all and the month/day switch - live one
 * tap behind the overflow. Nothing is removed and nothing is fake.
 *
 * `DropdownMenu` is used only as the popup host, because positioning a popup
 * against its anchor is real work that it already does. The content is drawn
 * from this app's tokens, so it does not read as a different application.
 */
@Composable
fun HgOverflowMenu(
    onSelectAll: () -> Unit,
    onToggleView: () -> Unit,
    monthView: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val palette = hgColors

    Box(modifier) {
        HgIconButton(
            icon = HgIcons.More,
            contentDescription = stringResource(R.string.more_options),
            onClick = { open = true },
            modifier = Modifier.testTag(HgTestTags.MORE_BUTTON),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .widthIn(min = 232.dp)
                .clip(RoundedCornerShape(hgShapes.card))
                .background(palette.surfaceHigh),
        ) {
            Column(Modifier.padding(vertical = hgSpacing.floatGap)) {
                HgMenuItem(
                    label = stringResource(R.string.select_all),
                    icon = HgIcons.SelectAll,
                    onClick = {
                        open = false
                        onSelectAll()
                    },
                    modifier = Modifier.testTag(HgTestTags.MENU_SELECT_ALL),
                )
                HgMenuItem(
                    label = stringResource(
                        if (monthView) R.string.view_day else R.string.view_month,
                    ),
                    icon = if (monthView) HgIcons.GridDay else HgIcons.GridMonth,
                    onClick = {
                        open = false
                        onToggleView()
                    },
                    modifier = Modifier.testTag(HgTestTags.MENU_VIEW),
                )
                HgMenuItem(
                    label = stringResource(R.string.settings),
                    icon = HgIcons.Settings,
                    onClick = {
                        open = false
                        onOpenSettings()
                    },
                    modifier = Modifier.testTag(HgTestTags.MENU_SETTINGS),
                )
            }
        }
    }
}

/**
 * One row of the menu.
 *
 * It reserves `hgSpacing.minTouch` so every row is a legal touch target, and it
 * announces its label as the button name: a menu row built from a Row and a Text
 * is otherwise silent to a screen reader.
 */
@Composable
private fun HgMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = hgSpacing.minTouch)
            .clickable(onClick = onClick)
            .padding(horizontal = hgSpacing.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(hgSpacing.cardGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textPrimary,
        )
    }
}
