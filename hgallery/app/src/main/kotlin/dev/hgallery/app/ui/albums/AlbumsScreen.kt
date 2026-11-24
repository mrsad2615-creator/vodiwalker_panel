package dev.hgallery.app.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgScreenTitle
import dev.hgallery.app.ui.components.HgSectionHeader
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.components.HgToolbar
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.AlbumTitle
import dev.hgallery.app.ui.theme.hgSpacing
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.Album
import dev.hgallery.core.AlbumBuilder
import dev.hgallery.core.AlbumSort
import dev.hgallery.core.MediaItem
import kotlinx.coroutines.launch

/**
 * The Albums tab, built to reference R2.
 *
 * Observed structure: a "smart" row of three synthetic albums (All photos,
 * Videos, Camera) whose covers are mini 4-cell mosaics, then "MORE ALBUMS" with
 * an "ADD ALBUM" link, then the device folders. Name and count sit *under* the
 * tile, left aligned — not overlaid on the image.
 */
@Composable
fun AlbumsScreen(
    container: AppContainer,
    onOpenAlbum: (Album) -> Unit,
    onOpenCustomAlbum: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val customAlbums by container.customAlbums.albums.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    val albums = remember(items) {
        AlbumBuilder.visible(
            AlbumBuilder.sort(AlbumBuilder.fromItems(items), AlbumSort.DATE),
        )
    }
    val smart = albums.filter { it.isSynthetic }
    val folders = albums.filterNot { it.isSynthetic }

    // Custom albums are a third kind: not a bucket, not a synthetic view, but a set the
    // user chose. Counts come from the library, so an album always shows what it opens.
    val liveIds = remember(items) { items.map { it.id }.toSet() }
    val custom = remember(customAlbums, liveIds) {
        customAlbums.map { album ->
            album.copy(itemIds = album.itemIds.filter { it in liveIds })
        }
    }

    // A private album is hidden until this session has been unlocked. The flag is memory
    // only on purpose: locking again after the app leaves the foreground is the point,
    // and a stored "unlocked" would defeat it.
    var unlocked by remember { mutableStateOf(false) }
    val visibleCustom = if (unlocked) custom else custom.filterNot { it.isPrivate }

    var addOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<dev.hgallery.core.CustomAlbum?>(null) }
    var deleting by remember { mutableStateOf<dev.hgallery.core.CustomAlbum?>(null) }
    var menuFor by remember { mutableStateOf<dev.hgallery.core.CustomAlbum?>(null) }
    var gateError by remember { mutableStateOf<String?>(null) }

    val appContext = androidx.compose.ui.platform.LocalContext.current
    val activity = appContext as? androidx.fragment.app.FragmentActivity

    if (addOpen) {
        AlbumNameDialog(
            title = stringResource(R.string.album_new_title),
            initial = "",
            existing = custom,
            editing = null,
            onDismiss = { addOpen = false },
            onConfirm = { name ->
                addOpen = false
                scope.launch { container.customAlbums.create(name, custom) }
            },
        )
    }

    renaming?.let { target ->
        AlbumNameDialog(
            title = stringResource(R.string.album_rename_title),
            initial = target.name,
            existing = custom,
            editing = target,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { container.customAlbums.rename(target, name, custom) }
            },
        )
    }

    deleting?.let { target ->
        DeleteAlbumDialog(
            album = target,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                scope.launch { container.customAlbums.delete(target) }
            },
        )
    }

    menuFor?.let { target ->
        AlbumMenuDialog(
            album = target,
            canProtect = activity != null && PrivateGate.canAuthenticate(activity),
            onRename = { menuFor = null; renaming = target },
            onTogglePrivate = {
                menuFor = null
                scope.launch { container.customAlbums.save(target.copy(isPrivate = !target.isPrivate)) }
            },
            onDelete = { menuFor = null; deleting = target },
            onDismiss = { menuFor = null },
        )
    }

    // The gate is offered before the private albums are drawn, not after a tap on one of
    // them: an app that lists nothing and waits for a tap on nothing cannot be unlocked.
    val privateCount = custom.count { it.isPrivate }

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        // Settings is behind the overflow slot. It was left empty while it had no
        // destination; it has one now, so the slot is used.
        HgToolbar(
            onSearch = onSearch,
            onMore = onOpenSettings,
            modifier = Modifier.padding(end = 6.dp),
        )
        // The title is alone on its row here, so this is the one place that adds
        // the screen gutter: HgScreenTitle does not draw its own inset.
        HgScreenTitle(
            title = stringResource(R.string.tab_albums),
            modifier = Modifier.padding(start = hgSpacing.gutter, end = hgSpacing.gutter),
        )
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(HgMetrics.AlbumColumns),
            horizontalArrangement = Arrangement.spacedBy(HgMetrics.AlbumGap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = HgMetrics.Gutter,
                end = HgMetrics.Gutter,
                bottom = 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(smart, key = { it.id }) { album ->
                AlbumTile(album, items, container) { onOpenAlbum(album) }
            }

            if (privateCount > 0 && !unlocked) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PrivateRow(
                        count = privateCount,
                        onUnlock = {
                            // No host activity means no prompt. The row is disabled in
                            // that case, so this is a guard and not a user-visible path.
                            if (activity == null) return@PrivateRow
                            PrivateGate.prompt(
                                activity = activity,
                                title = appContext.getString(R.string.private_title),
                                subtitle = appContext.getString(R.string.private_subtitle),
                                onSuccess = { unlocked = true; gateError = null },
                                onFailure = { gateError = it },
                            )
                        },
                    )
                }
            }

            if (visibleCustom.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HgSectionHeader(label = stringResource(R.string.section_my_albums))
                }
                items(visibleCustom, key = { "custom-${it.id}" }) { album ->
                    CustomAlbumTile(
                        album = album,
                        items = items,
                        container = container,
                        onOpen = { onOpenCustomAlbum(album.id) },
                        onMenu = { menuFor = album },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                HgSectionHeader(
                    label = stringResource(R.string.section_more_albums),
                    action = stringResource(R.string.action_add_album),
                    onAction = { addOpen = true },
                    enabled = true,
                )
            }
            if (folders.isNotEmpty()) {
                items(folders, key = { it.id }) { album ->
                    AlbumTile(album, items, container) { onOpenAlbum(album) }
                }
            }
        }
    }
}

/**
 * A mosaic of up to four covers, which is how the reference fills a smart
 * album's tile. A single cover is used for ordinary folders.
 */
@Composable
private fun AlbumTile(
    album: Album,
    allItems: List<MediaItem>,
    container: AppContainer,
    onClick: () -> Unit,
) {
    val palette = hgPalette
    val mosaic = remember(album.id, allItems) {
        if (!album.isSynthetic) emptyList()
        else AlbumBuilder.itemsOf(allItems, album.id)
            .sortedByDescending { it.dateTakenMs }
            .take(4)
    }

    Column(
        modifier = Modifier
            .testTag(HgTestTags.album(album.name))
            // The tile's own name is its accessible label. Without it the tile is
            // an unlabelled clickable box and the only text inside it is a count,
            // which tells a screen-reader user nothing about which album it opens.
            .semantics(mergeDescendants = true) {
                contentDescription = "${album.name}, ${album.count}"
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(HgMetrics.AlbumCorner))
                .background(palette.chipBackground),
        ) {
            when {
                mosaic.size >= 2 -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            MosaicCell(mosaic.getOrNull(0), container, Modifier.weight(1f))
                            Spacer(Modifier.width(1.5.dp))
                            MosaicCell(mosaic.getOrNull(1), container, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(1.5.dp))
                        Row(Modifier.weight(1f)) {
                            MosaicCell(mosaic.getOrNull(2), container, Modifier.weight(1f))
                            Spacer(Modifier.width(1.5.dp))
                            MosaicCell(mosaic.getOrNull(3), container, Modifier.weight(1f))
                        }
                    }
                }
                mosaic.size == 1 -> MediaThumbnail(
                    item = mosaic.first(),
                    store = container.thumbnails,
                    modifier = Modifier.fillMaxSize(),
                )
                album.coverUri != null -> {
                    val cover = allItems.firstOrNull { it.uri == album.coverUri }
                    if (cover != null) {
                        MediaThumbnail(item = cover, store = container.thumbnails, modifier = Modifier.fillMaxSize())
                    } else {
                        EmptyFolder()
                    }
                }
                else -> EmptyFolder()
            }
        }

        Spacer(Modifier.height(8.dp))
        // The tile is 4:3 and three-up, which leaves ~100 dp of width: the
        // reference's 14/12 sp fits comfortably and keeps name and count on
        // their own two lines like the original.
        Text(
            text = album.name,
            style = AlbumTitle,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = album.count.toString(),
            style = CountLabel,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun MosaicCell(item: MediaItem?, container: AppContainer, modifier: Modifier) {
    if (item == null) {
        Box(modifier.background(hgPalette.chipBackground))
    } else {
        MediaThumbnail(item = item, store = container.thumbnails, modifier = modifier, showVideoBadge = false)
    }
}

@Composable
private fun EmptyFolder() {
    val palette = hgPalette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = HgIcons.Folder,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * The row that offers the device lock for private albums.
 *
 * It states how many albums are hidden and what the lock does *not* do. Encrypting the
 * media is not implemented, so the row must not suggest it: the files stay where they are
 * and any app with media permission can still read them.
 */
@Composable
private fun PrivateRow(count: Int, onUnlock: () -> Unit) {
    val palette = hgPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HgMetrics.MinTouchTarget)
            .clip(RoundedCornerShape(HgMetrics.AlbumCorner))
            .background(palette.chipBackground)
            .clickable(onClick = onUnlock)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Private albums, $count hidden. Unlock."
            }
            .testTag(HgTestTags.ALBUM_MENU_PRIVATE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HgIcons.Lock,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.private_row_title, count),
                style = AlbumTitle,
                color = palette.textPrimary,
            )
            Text(
                text = stringResource(R.string.private_row_body),
                style = CountLabel,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * A custom album's tile.
 *
 * It carries the same shape as a device folder so the tab reads as one list, and adds a
 * lock badge and a menu button. The menu is a button rather than a long press: a long press
 * on a tile that also opens is a gesture two controls would share, and neither would be
 * discoverable.
 */
@Composable
private fun CustomAlbumTile(
    album: dev.hgallery.core.CustomAlbum,
    items: List<MediaItem>,
    container: AppContainer,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val palette = hgPalette
    val covers = remember(album.itemIds, items) {
        album.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }.take(4)
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(HgMetrics.AlbumCorner))
                .background(palette.chipBackground)
                .testTag(HgTestTags.customAlbum(album.id))
                .semantics(mergeDescendants = true) {
                    contentDescription = "${album.name}, ${album.count}"
                }
                .clickable(onClick = onOpen),
        ) {
            if (covers.isEmpty()) {
                EmptyFolder()
            } else {
                MediaThumbnail(
                    item = covers.first(),
                    store = container.thumbnails,
                    describeImage = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (album.isPrivate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(palette.page.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HgIcons.Lock,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = AlbumTitle,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = album.count.toString(),
                    style = CountLabel,
                    color = palette.textSecondary,
                )
            }
            HgIconButton(
                icon = HgIcons.More,
                contentDescription = stringResource(R.string.album_menu),
                onClick = onMenu,
                modifier = Modifier.testTag(HgTestTags.ALBUM_MENU),
            )
        }
    }
}
