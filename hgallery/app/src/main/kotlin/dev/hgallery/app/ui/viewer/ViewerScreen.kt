package dev.hgallery.app.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.core.MediaItem
import kotlinx.coroutines.launch

/**
 * Full-screen viewer.
 *
 * Behaviour taken from the reference and from what a Huawei user expects:
 *  - the photo sits on black, with the status bar hidden while chrome is hidden;
 *  - a single tap toggles the bars, it never closes the viewer;
 *  - horizontal swipes move between items, vertical drags would dismiss;
 *  - pinch and double-tap zoom, with panning only once zoomed in;
 *  - the action row is Share / Favourite / Edit / Delete / More, at the bottom.
 *
 * Reference uncertainty: an exact viewer capture was not secured, so the icon
 * *order* in the bottom row is documented as an inference in
 * `KNOWN_LIMITATIONS.md` rather than presented as verified.
 */
@Composable
fun ViewerScreen(
    container: AppContainer,
    startId: Long,
    items: List<MediaItem>,
    onClose: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val initialIndex = remember(startId, items) {
        items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }

    val current = items.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // The viewer is fully opaque, so its own taps must not fall through
            // to the grid underneath.
            .pointerInput(Unit) { detectTapGestures(onTap = { chromeVisible = !chromeVisible }) },
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page)
            if (item != null) {
                ZoomableImage(item, container)
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HgIconButton(HgIcons.Close, "Close", onClose, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = current?.let { "${pagerState.currentPage + 1} / ${items.size}" } ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    HgIconButton(HgIcons.More, "More options", {}, tint = Color.White)
                }
                if (current != null) {
                    Text(
                        text = current.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding(),
            ) {
                if (current != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        HgIconButton(HgIcons.Share, "Share", {}, tint = Color.White)
                        HgIconButton(
                            icon = if (current.isFavorite) HgIcons.FavoriteFilled else HgIcons.Favorite,
                            contentDescription = if (current.isFavorite) "Remove from favourites" else "Add to favourites",
                            onClick = {
                                coroutine.launch { container.mediaStore.toggleFavorite(current.id) }
                            },
                            tint = if (current.isFavorite) Color(0xFFFF6B5A) else Color.White,
                        )
                        HgIconButton(HgIcons.Edit, "Edit", {}, tint = Color.White)
                        HgIconButton(
                            icon = HgIcons.Delete,
                            contentDescription = "Delete",
                            onClick = { coroutine.launch { container.mediaStore.delete(setOf(current.id)) } },
                            tint = Color.White,
                        )
                        HgIconButton(HgIcons.Info, "Details", {}, tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * A single zoomable page.
 *
 * The transform is applied with `graphicsLayer` so panning and zooming never
 * re-run layout or re-decode the bitmap — on this class of hardware that is the
 * difference between a smooth pinch and a janky one.
 */
@Composable
private fun ZoomableImage(item: MediaItem, container: AppContainer) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    val maxScale = 5f
    val doubleTapScale = 2.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, maxScale)
                    scale = next
                    offset = if (next <= 1.01f) {
                        Offset.Zero
                    } else {
                        // Pan is bounded by how far the enlarged image overhangs
                        // the viewport, so the photo can never be dragged offscreen.
                        val maxX = (size.width * (next - 1f)) / 2f
                        val maxY = (size.height * (next - 1f)) / 2f
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = doubleTapScale
                        }
                    },
                )
            },
    ) {
        FullResolutionImage(
            item = item,
            container = container,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
