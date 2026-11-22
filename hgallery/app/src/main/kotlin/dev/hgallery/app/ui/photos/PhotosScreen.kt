package dev.hgallery.app.ui.photos

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.ThumbnailStore
import dev.hgallery.app.ui.components.HgDeleteDialog
import dev.hgallery.app.ui.components.HgScreenTitle
import dev.hgallery.app.ui.components.HgSelectionActions
import dev.hgallery.app.ui.components.HgSelectionHeader
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.SelectionBadge
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.MediaItem
import dev.hgallery.core.Selection
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Photos timeline.
 *
 * Structure, from reference R1:
 *  - toolbar (search, overflow) at the top right, below the status bar;
 *  - a large "Photos" title with a centred count line underneath;
 *  - date sections, each introduced by a small header;
 *  - a seamless grid;
 *  - the title scrolls with the content; it is not a pinned bar.
 *
 * Behaviour, from Huawei's own Gallery manual for the Photos tab: *"zoom in or
 * out to switch between month and day view."* Pinching out enters month view
 * (more items per row, month headers); pinching in returns to day view.
 */
@Composable
fun PhotosScreen(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionAwarePhotos(container = container, onOpenItem = onOpenItem, modifier = modifier)
}

@Composable
private fun SelectionAwarePhotos(
    container: AppContainer,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val loading by container.mediaStore.loading.collectAsStateWithLifecycle()
    val error by container.mediaStore.error.collectAsStateWithLifecycle()

    val locale = remember { Locale.getDefault() }
    val daySections = remember(items, locale) { container.sections(items, locale) }
    val monthSections = remember(items, locale) { container.months(items, locale) }

    val photos = remember(items) { items.count { !it.isVideo } }
    val videos = remember(items) { items.count { it.isVideo } }

    // Counts come from the resources rather than from concatenation, so a
    // language with different plural rules reads correctly.
    val photoLabel = stringResource(R.string.photo_count, photos)
    val videoLabel = stringResource(R.string.video_count, videos)
    val summary = when {
        photos > 0 && videos > 0 -> stringResource(R.string.media_summary, photoLabel, videoLabel)
        photos > 0 -> photoLabel
        videos > 0 -> videoLabel
        else -> ""
    }

    var monthView by remember { mutableStateOf(false) }

    // Selection lives on the screen that owns the grid, so leaving the tab drops
    // it. The `prune` keeps it honest when a delete removes rows.
    var selection by remember { mutableStateOf(Selection.None) }
    var pendingDelete by remember { mutableStateOf(false) }
    val existingIds = remember(daySections, monthSections) {
        (daySections.flatMap { it.items } + monthSections.flatMap { it.items }).map { it.id }.toSet()
    }
    LaunchedEffect(existingIds) { selection = selection.prune(existingIds) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val deletePath = remember { container.mediaStore.deletePath() }

    /**
     * Applies a delete the user has already approved and reports the result.
     *
     * One function for both routes on purpose: the local toast previously had to
     * be written twice, and two copies of a message derived from an outcome is
     * exactly how a UI ends up claiming an item was trashed when it was deleted.
     */
    suspend fun finishDelete(ids: Set<Long>) {
        val outcome = container.mediaStore.applyDelete(ids)
        selection = selection.exit()
        val message = when {
            outcome.removed == 0 -> context.getString(R.string.delete_failed)
            outcome.failed > 0 -> context.getString(R.string.delete_partial, outcome.failed, outcome.total)
            outcome.trashed > 0 -> context.resources.getQuantityString(
                R.plurals.moved_to_trash, outcome.trashed, outcome.trashed,
            )
            else -> context.resources.getQuantityString(
                R.plurals.deleted_permanently, outcome.deleted, outcome.deleted,
            )
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // The system trash dialog returns through the activity result registry: the
    // app may *ask* for a delete, only the platform may perform one.
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val ids = selection.ids
        if (result.resultCode == android.app.Activity.RESULT_OK && ids.isNotEmpty()) {
            scope.launch { finishDelete(ids) }
        }
    }

    /** Confirm, then delete through whichever route this Android version offers. */
    fun performDelete() {
        val ids = selection.ids
        if (ids.isEmpty()) return
        pendingDelete = false
        val sender = container.mediaStore.buildDeleteRequest(ids)
        if (sender != null) {
            val launched = runCatching {
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }.isSuccess
            if (launched) return
            // A sender can be refused (e.g. the grant was revoked). The direct
            // path keeps the action working rather than failing silently.
        }
        scope.launch { finishDelete(ids) }
    }

    fun share() {
        val ids = selection.ids
        val intent = if (ids.isEmpty()) null else container.mediaStore.buildShareIntent(ids)
        if (intent == null) {
            android.widget.Toast.makeText(context, R.string.share_nothing, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            runCatching { context.startActivity(intent) }
        }
    }

    val columns = if (monthView) HgMetrics.MonthColumns else HgMetrics.PhotosColumns
    val thumbnails = remember { container.thumbnails }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())

        // In selection mode the title is replaced by the count, exactly as R3
        // draws it, so the grid itself does not move.
        if (selection.active) {
            HgSelectionHeader(
                selection = selection,
                onExit = { selection = selection.exit() },
            )
        } else {
        Column(Modifier.fillMaxWidth()) {
            HgToolbar(
                onSearch = {},
                onMore = {},
                viewAction = {
                    // Selection is reachable without a long press, so the feature
                    // is discoverable and can be driven by a test.
                    HgIconButton(
                        icon = HgIcons.SelectAll,
                        contentDescription = stringResource(R.string.select),
                        onClick = { selection = selection.selectAll(existingIds) },
                        modifier = Modifier.testTag(HgTestTags.SELECT_BUTTON),
                    )
                    // A pinch alone is undiscoverable, and a gesture that cannot
                    // be driven from a test is a gesture nobody checks. The
                    // switch is the primary control; pinching remains available.
                    HgIconButton(
                        icon = if (monthView) HgIcons.GridDay else HgIcons.GridMonth,
                        contentDescription = stringResource(
                            if (monthView) R.string.view_day else R.string.view_month,
                        ),
                        onClick = { monthView = !monthView },
                        modifier = Modifier.testTag(HgTestTags.VIEW_SWITCH),
                    )
                },
                modifier = Modifier.padding(end = 6.dp),
            )
            HgScreenTitle(
                title = stringResource(R.string.tab_photos),
                subtitle = if (items.isNotEmpty()) summary else null,
                subtitleCentred = true,
            )
            Spacer(Modifier.height(14.dp))
            }
        }

        // `weight(1f)` is what reserves room for the selection bar: with an
        // unweighted child calling fillMaxSize(), the bar was measured to zero
        // height and was simply not drawn — visible in the first screenshot of
        // this feature, which is why the weight is here and commented.
        Box(Modifier.weight(1f)) {
        when {
            loading && items.isEmpty() -> LoadingState()
            error != null && items.isEmpty() -> MessageState(
                stringResource(R.string.error_media_title),
                error ?: "",
            )
            items.isEmpty() -> MessageState(
                stringResource(R.string.empty_photos_title),
                stringResource(R.string.empty_photos_body),
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val pressed = down.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val start = (pressed[0].position - pressed[1].position).getDistance()
                                    var current = start
                                    // Follow both pointers until one lifts.
                                    while (true) {
                                        val move = awaitPointerEvent(
                                            androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                                        )
                                        val still = move.changes.filter { it.pressed }
                                        if (still.size < 2) break
                                        current = (still[0].position - still[1].position).getDistance()
                                    }
                                    val ratio = if (start > 0f) current / start else 1f
                                    if (ratio > 1.35f && !monthView) {
                                        monthView = true
                                    } else if (ratio < 0.7f && monthView) {
                                        monthView = false
                                    }
                                }
                            }
                        }
                    },
            ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                verticalArrangement = Arrangement.spacedBy(HgMetrics.GridGap),
                contentPadding = PaddingValues(
                    start = HgMetrics.Gutter,
                    end = HgMetrics.Gutter,
                    bottom = 24.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HgTestTags.PHOTOS_GRID),
            ) {
                if (monthView) {
                    monthSections.forEach { section ->
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(section.header, true) }
                        items(section.items, key = { it.id }) { item ->
                            ThumbnailCell(
                                item = item,
                                store = thumbnails,
                                selection = selection,
                                onClick = {
                                    if (selection.active) {
                                        selection = selection.toggle(item.id)
                                    } else {
                                        onOpenItem(item.id)
                                    }
                                },
                                onLongClick = { selection = selection.beginWith(item.id) },
                            )
                        }
                    }
                } else {
                    daySections.forEach { section ->
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(section.header, false) }
                        items(section.items, key = { it.id }) { item ->
                            ThumbnailCell(
                                item = item,
                                store = thumbnails,
                                selection = selection,
                                onClick = {
                                    if (selection.active) {
                                        selection = selection.toggle(item.id)
                                    } else {
                                        onOpenItem(item.id)
                                    }
                                },
                                onLongClick = { selection = selection.beginWith(item.id) },
                            )
                        }
                    }
                }
            }
            }
        }
        }

        if (selection.active) {
            HgSelectionActions(
                selection = selection,
                allIds = existingIds,
                onShare = { share() },
                onDelete = { pendingDelete = true },
                onToggleSelectAll = {
                    selection = if (selection.isAllSelected(existingIds)) {
                        selection.exit()
                    } else {
                        selection.selectAll(existingIds)
                    }
                },
                modifier = Modifier.testTag(HgTestTags.SELECTION_BAR),
            )
        }
    }

    if (pendingDelete) {
        HgDeleteDialog(
            count = selection.count,
            path = deletePath,
            onConfirm = { performDelete() },
            onDismiss = { pendingDelete = false },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailCell(
    item: MediaItem,
    store: ThumbnailStore,
    selection: Selection,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val selected = selection.contains(item.id)
    Box(
        modifier = Modifier
            .testTag(HgTestTags.thumbnail(item.id))
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        MediaThumbnail(
            item = item,
            store = store,
            cornerRadius = 3,
            modifier = Modifier.fillMaxSize(),
        )

        // Unselected tiles recede while a selection is running, as in R3: the
        // chosen ones have to be readable at a glance in a dense grid.
        androidx.compose.animation.AnimatedVisibility(
            visible = selection.active,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
        ) {
            if (!selected) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)))
            }
        }

        SelectionBadge(
            selected = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .testTag(HgTestTags.tick(item.id)),
        )
    }
}

@Composable
private fun SectionHeader(label: String, isMonth: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isMonth) 16.dp else 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (isMonth) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = hgPalette.textPrimary,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = hgPalette.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun MessageState(title: String, detail: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = hgPalette.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = hgPalette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
