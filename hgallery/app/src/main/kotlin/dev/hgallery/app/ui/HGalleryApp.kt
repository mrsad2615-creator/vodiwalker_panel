package dev.hgallery.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.albums.AlbumDetailScreen
import dev.hgallery.app.ui.albums.AlbumsScreen
import dev.hgallery.app.ui.photos.PhotosScreen
import dev.hgallery.app.ui.placeholder.PlaceholderScreen
import dev.hgallery.app.ui.search.SearchScreen
import dev.hgallery.app.ui.shell.HgBottomBar
import dev.hgallery.app.ui.shell.HgTab
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.video.VideoPlayerScreen
import dev.hgallery.app.ui.viewer.ViewerScreen
import dev.hgallery.core.Album
import dev.hgallery.core.AlbumBuilder

/**
 * Application shell.
 *
 * Three layers, in the order the reference stacks them:
 *   1. the four tabs, with the bottom bar always visible;
 *   2. an album's contents, which keeps the bar and adds a back affordance;
 *   3. the full-screen viewer, which covers everything including the bar.
 *
 * A plain state machine is used rather than a navigation graph: the tabs are
 * siblings with no back stack, and the two overlays have exactly one entry point
 * each. A graph here would be indirection without a benefit.
 */
// VideoPlayerScreen uses Media3 types, which are marked UnstableApi. The opt-in
// is declared here rather than suppressed globally so the exception is visible at
// the one place the player is composed.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun HGalleryApp(container: AppContainer) {
    val palette = hgPalette
    var tab by remember { mutableStateOf(HgTab.Photos) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var viewerId by remember { mutableStateOf<Long?>(null) }
    var playerId by remember { mutableStateOf<Long?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    val items by container.mediaStore.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.mediaStore.load() }

    // Back closes the innermost layer first, which is what a user expects and
    // what the reference does.
    BackHandler(enabled = playerId != null) { playerId = null }
    // Search is a layer, not a tab: Back closes it and returns to whatever was
    // underneath, exactly like the viewer.
    BackHandler(enabled = playerId == null && viewerId == null && searchOpen) {
        searchOpen = false
    }
    BackHandler(enabled = playerId == null && viewerId != null) { viewerId = null }
    BackHandler(enabled = playerId == null && viewerId == null && openAlbum != null) { openAlbum = null }

    // One entry point for "open this item": a video goes to the player, an image
    // to the viewer. Deciding here means every grid in the app behaves the same
    // way without repeating the rule.
    val openItem: (Long) -> Unit = { id ->
        val media = items.firstOrNull { it.id == id }
        when {
            media == null -> viewerId = id
            media.isVideo -> playerId = id
            else -> viewerId = id
        }
    }

    // Search covers the tabs and the bar, as the reference does: it is a mode, and
    // leaving it must be an explicit action rather than a stray tap on a tab. It is
    // therefore drawn over the shell rather than inside it.
    if (searchOpen) {
        SearchScreen(
            container = container,
            items = items,
            albums = remember(items) { AlbumBuilder.visible(AlbumBuilder.fromItems(items)) },
            onClose = { searchOpen = false },
            onOpenItem = openItem,
        )
        return
    }

    Box(Modifier.fillMaxSize().background(palette.page)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val album = openAlbum
                if (album != null) {
                    AlbumDetailScreen(
                        container = container,
                        album = album,
                        items = items,
                        onBack = { openAlbum = null },
                        onOpenItem = openItem,
                    )
                } else {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            HgTab.Photos -> PhotosScreen(
                                container = container,
                                onOpenItem = openItem,
                                onSearch = { searchOpen = true },
                            )
                            HgTab.Albums -> AlbumsScreen(
                                container = container,
                                onOpenAlbum = { openAlbum = it },
                                onSearch = { searchOpen = true },
                            )
                            HgTab.Highlights -> PlaceholderScreen(stringResource(R.string.tab_highlights))
                            HgTab.Discover -> PlaceholderScreen(stringResource(R.string.tab_discover))
                        }
                    }
                }
            }
            HgBottomBar(selected = tab, onSelect = { tab = it; openAlbum = null })
        }

        val playingId = playerId
        if (playingId != null) {
            val media = items.firstOrNull { it.id == playingId }
            if (media != null) {
                VideoPlayerScreen(
                    item = media,
                    onClose = { playerId = null },
                )
            }
        }

        val openId = viewerId
        if (openId != null) {
            ViewerScreen(
                container = container,
                startId = openId,
                items = items,
                onClose = { viewerId = null },
            )
        }
    }
}
