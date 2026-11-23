package dev.hgallery.app.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.MessageState
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.core.ByteFormatter
import dev.hgallery.core.TrashEntry
import dev.hgallery.core.TrashPolicy
import kotlinx.coroutines.launch

/**
 * The app's own trash.
 *
 * ## When this screen has anything in it
 *
 * Only below Android 11. From Android 11 the platform keeps the trash and these files are
 * not here at all; the screen then explains that the system trash holds them, and links to
 * nothing it cannot open. A screen that was simply empty above Android 11 would read as a
 * broken feature.
 *
 * ## What it promises
 *
 * An item stays for thirty days, and it can be restored or removed before then. The
 * sentence about where the copy lives is in the body text, because the copy is in app
 * storage and uninstalling the app removes it. The screen must not imply otherwise.
 */
@Composable
fun TrashScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<TrashEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }

    // `version` is the reload trigger: restoring or deleting an entry bumps it, and the
    // list is read again. A second flow would duplicate what the file system already is.
    LaunchedEffect(version) {
        entries = container.trash.entries()
        loaded = true
    }

    val now = remember(version) { System.currentTimeMillis() }
    val active = remember(entries, now) { TrashPolicy.active(entries, now) }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HgMetrics.MinTouchTarget)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HgIconButton(
                icon = HgIcons.Back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.testTag(HgTestTags.TRASH_BACK),
            )
            Text(
                text = stringResource(R.string.trash),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (active.isNotEmpty()) {
                TextButton(
                    onClick = { confirmEmpty = true },
                    modifier = Modifier.testTag(HgTestTags.TRASH_EMPTY),
                ) { Text(stringResource(R.string.trash_empty_action)) }
            }
        }

        Text(
            text = pluralStringResource(
                R.plurals.trash_summary,
                active.size,
                TrashPolicy.RETENTION_DAYS.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(
                start = HgMetrics.Gutter,
                end = HgMetrics.Gutter,
                bottom = 8.dp,
            ),
        )

        when {
            !loaded -> MessageState(
                title = stringResource(R.string.trash_loading),
                detail = "",
            )
            active.isEmpty() -> MessageState(
                title = stringResource(R.string.trash_empty_title),
                detail = stringResource(R.string.trash_empty_body),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = HgMetrics.Gutter,
                    end = HgMetrics.Gutter,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HgTestTags.TRASH_LIST),
            ) {
                items(active, key = { it.itemId }) { entry ->
                    TrashRow(
                        entry = entry,
                        daysLeft = TrashPolicy.daysLeft(entry, now),
                        onRestore = {
                            scope.launch {
                                container.trash.restore(entry)
                                // Reload either way: a failed restore must leave the row
                                // visible, so the user can try again or delete it.
                                container.mediaStore.load(force = true)
                                version++
                            }
                        },
                        onDelete = {
                            scope.launch {
                                container.trash.deleteForever(entry)
                                version++
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        scope.launch {
                            active.forEach { container.trash.deleteForever(it) }
                            version++
                        }
                    },
                    modifier = Modifier.testTag(HgTestTags.TRASH_EMPTY_CONFIRM),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * One row: name, size, days left, and the two actions.
 *
 * The actions are text buttons rather than icons. Two icons on a row with no picture
 * beside them are unlabelled shapes; the words are the label, and they stay readable in a
 * language this file has no layout for.
 */
@Composable
private fun TrashRow(
    entry: TrashEntry,
    daysLeft: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = hgPalette
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(hgShapes.chip))
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (entry.mimeType.startsWith("video/")) HgIcons.Play else HgIcons.Photos,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ByteFormatter.format(entry.sizeBytes) + " · " +
                        pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft),
                    style = CountLabel,
                    color = palette.textSecondary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onRestore,
                modifier = Modifier.testTag(HgTestTags.trashRestore(entry.itemId)),
            ) { Text(stringResource(R.string.trash_restore)) }
            TextButton(
                onClick = { confirmingDelete = true },
                modifier = Modifier.testTag(HgTestTags.trashDelete(entry.itemId)),
            ) { Text(stringResource(R.string.delete), color = palette.danger) }
        }
    }

    if (confirmingDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.trash_delete_confirm_title, entry.fileName)) },
            text = { Text(stringResource(R.string.trash_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
