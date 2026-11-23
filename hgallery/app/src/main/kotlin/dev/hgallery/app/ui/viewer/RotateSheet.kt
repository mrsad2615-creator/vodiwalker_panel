package dev.hgallery.app.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.MediaItem

/**
 * Rotate a photograph.
 *
 * ## Why only rotation
 *
 * The brief rules out filters and cropping, and it is right to: a gallery that edits is a
 * different product with a different risk of damaging the user's file. A rotation is the
 * one change that repairs a file rather than altering it, which is why it is offered.
 *
 * ## Why the write can be refused
 *
 * Changing the orientation is a write to the user's media. The platform lets an app it
 * considers a media manager do this, and refuses everyone else. The sheet therefore says
 * up front when the write is not available, instead of showing a control that fails after
 * the press.
 */
@Composable
fun RotateSheet(
    item: MediaItem,
    hasPermission: Boolean,
    onDismiss: () -> Unit,
    onRotate: (Int) -> Unit,
) {
    val palette = hgPalette

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rotate)) },
        text = {
            Column(Modifier.heightIn(min = 0.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                if (!hasPermission) {
                    // No control is shown that cannot work. The sentence explains the
                    // refusal, because "unavailable" with no reason reads as a bug.
                    Text(
                        text = stringResource(R.string.rotate_no_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger,
                    )
                } else {
                    TextButton(
                        onClick = { onRotate(90) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HgTestTags.ROTATE_90),
                    ) { Text(stringResource(R.string.rotate_90)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(HgTestTags.ROTATE_CANCEL),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
