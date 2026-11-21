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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.albums.AlbumsScreen
import dev.hgallery.app.ui.photos.PhotosScreen
import dev.hgallery.app.ui.placeholder.PlaceholderScreen
import dev.hgallery.app.ui.shell.HgBottomBar
import dev.hgallery.app.ui.shell.HgTab
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.viewer.ViewerScreen

/**
 * Application shell: four destinations behind one bottom bar.
 *
 * A plain state switch is used rather than a navigation graph because the four
 * tabs are siblings with no back stack of their own. The viewer is a full-screen
 * overlay that hides the bar, which is how the reference behaves.
 */
@Composable
fun HGalleryApp(container: AppContainer) {
    val palette = hgPalette
    var tab by remember { mutableStateOf(HgTab.Photos) }
    var viewerId by remember { mutableStateOf<Long?>(null) }

    val items by container.mediaStore.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.mediaStore.load() }

    Box(Modifier.fillMaxSize().background(palette.page)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { current ->
                    when (current) {
                        HgTab.Photos -> PhotosScreen(
                            container = container,
                            onOpenItem = { viewerId = it },
                        )
                        HgTab.Albums -> AlbumsScreen(
                            container = container,
                            onOpenAlbum = { album ->
                                viewerId = items.firstOrNull { it.bucketId == album.id }?.id
                            },
                        )
                        HgTab.Highlights -> PlaceholderScreen("Highlights")
                        HgTab.Discover -> PlaceholderScreen("Discover")
                    }
                }
            }
            HgBottomBar(selected = tab, onSelect = { tab = it })
        }

        val openId = viewerId
        if (openId != null) {
            BackHandler { viewerId = null }
            ViewerScreen(
                container = container,
                startId = openId,
                items = items,
                onClose = { viewerId = null },
            )
        }
    }
}
