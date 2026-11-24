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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.MediaPermissions
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.components.HgDeleteDialog
import dev.hgallery.app.ui.components.HgIconButton
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.core.MediaItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.hgallery.app.ui.components.MediaThumbnail
import dev.hgallery.app.ui.components.mediaDescription
import dev.hgallery.app.ui.theme.hgColors
import dev.hgallery.core.PlayerPolicy
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import dev.hgallery.app.R
import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

/** One log tag for this screen, so the harness can isolate its lines from logcat. */
private const val TAG = "HGalleryViewer"

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
    /**
     * Opens [id] in the player, because the viewer cannot play it here.
     *
     * The viewer is a pager of stills with one player page. When a still is pushed onto a
     * clip, the screen changes rather than the page: a bitmap cannot start moving. See
     * `ViewerReplace.Action.HAND_OVER`.
     */
    onOpenVideo: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current

    // Favouriting a row this app does not own needs the user's consent on Android
    // 11+, and a refused write is reported as "0 rows" rather than as an error. The
    // toggle therefore asks for the grant and retries once; without this the heart
    // simply never filled and nothing anywhere said why.
    // The value the user asked for, not merely which item: the system dialog has
    // already applied it, so retrying the toggle would undo it.
    var pendingFavourite by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val favouriteConsent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pending = pendingFavourite
        pendingFavourite = null
        Log.w(TAG, "favourite consent result=${result.resultCode} pending=$pending")
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            val (id, target) = pending
            // The dialog *is* the write: a granted favourite request has already been
            // applied by MediaProvider, so the app only has to record it. Writing
            // again was the last remaining reason the heart stayed hollow.
            container.mediaStore.recordFavourite(id, target)
        }
    }

    fun favourite(id: Long) {
        coroutine.launch {
            val direct = container.mediaStore.toggleFavorite(id)
            Log.w(TAG, "favourite id=$id direct=$direct")
            if (direct) return@launch
            val target = container.mediaStore.targetFavourite(id)
            val sender = container.mediaStore.favouriteWriteRequest(id)
            Log.w(TAG, "favourite id=$id target=$target sender=${sender != null}")
            if (sender == null) {
                // Nothing was written and the platform offers no dialog to ask for.
                // Saying so is the difference between a bug and a mystery.
                Toast.makeText(context, R.string.favourite_failed, Toast.LENGTH_SHORT).show()
            } else {
                pendingFavourite = id to target
                favouriteConsent.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }
    val viewerContext = context
    val initialIndex = remember(startId, items) {
        items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var rotateOpen by remember { mutableStateOf(false) }

    val current = items.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .testTag(HgTestTags.VIEWER)
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
                // A clip is not the reason this screen exists, but it is a real item in
                // the timeline and it gets a real page. Before this branch existed, a
                // clip opened in the viewer decoded as an image, failed, and drew a black
                // rectangle on a page that claimed to be paging through the album.
                if (item.isVideo) {
                    VideoPage(item, container, onOpenVideo)
                } else {
                    ZoomableImage(item, container)
                }
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
                    HgIconButton(
                        icon = HgIcons.Close,
                        contentDescription = stringResource(R.string.close),
                        onClick = onClose,
                        tint = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = current?.let {
                            stringResource(R.string.viewer_position, pagerState.currentPage + 1, items.size)
                        } ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    // The overflow menu is not built yet. It stays visible, because
                    // the reference has it and the header looks wrong without it, but
                    // it is disabled: a control that answers a tap with nothing is
                    // the failure this screen was audited for once already.
                    HgIconButton(
                        icon = HgIcons.More,
                        contentDescription = stringResource(R.string.more_options),
                        onClick = {},
                        enabled = false,
                        tint = Color.White.copy(alpha = 0.35f),
                    )
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

        if (rotateOpen && current != null) {
        RotateSheet(
            item = current,
            hasPermission = MediaPermissions.canWrite(viewerContext),
            onDismiss = { rotateOpen = false },
            onRotate = { degrees ->
                rotateOpen = false
                coroutine.launch {
                    val done = container.mediaStore.rotate(current.id, degrees)
                    val text = if (done) R.string.rotate_done else R.string.rotate_failed
                    android.widget.Toast.makeText(viewerContext, text, android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            },
        )
    }

    if (showDetails && current != null) {
            DetailsSheet(
                item = current,
                exifReader = container.exif,
                onDismiss = { showDetails = false },
            )
        }

        val queued = pendingDelete
        if (queued != null) {
            // The same dialog the grid uses, so the wording cannot drift between
            // the two places a delete can start.
            HgDeleteDialog(
                count = 1,
                path = container.mediaStore.deletePath(),
                onConfirm = {
                    pendingDelete = null
                    coroutine.launch {
                        // The viewer has no selection, so this is the single-item
                        // path; it is still routed through the store so the grid
                        // behind the viewer loses the row too.
                        val outcome = container.mediaStore.applyDelete(setOf(queued.id))
                        if (outcome.removed > 0) onClose()
                    }
                },
                onDismiss = { pendingDelete = null },
            )
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
                // The strip is above the action row, inside the same animated block:
                // it is chrome, and hiding the chrome must hide all of it.
                PhotoFilmstrip(
                    items = items,
                    selectedIndex = pagerState.currentPage,
                    store = container.thumbnails,
                    onSelect = { index -> coroutine.launch { pagerState.animateScrollToPage(index) } },
                )
                if (current != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Edit and Details are not implemented yet. They are
                        // disabled rather than silently doing nothing when
                        // tapped, because a control that looks usable and is not
                        // is worse than one that reads as unavailable.
                        // Sharing one item is the same code path as sharing a
                        // selection, so it is genuinely wired, not a placeholder.
                        HgIconButton(
                            icon = HgIcons.Share,
                            contentDescription = stringResource(R.string.share),
                            onClick = {
                                val intent = container.mediaStore.buildShareIntent(setOf(current.id))
                                if (intent != null) {
                                    runCatching { viewerContext.startActivity(intent) }
                                }
                            },
                            tint = Color.White,
                        )
                        HgIconButton(
                            icon = if (current.isFavorite) HgIcons.FavoriteFilled else HgIcons.Favorite,
                            contentDescription = stringResource(
                                if (current.isFavorite) R.string.remove_favourite else R.string.add_favourite,
                            ),
                            onClick = { favourite(current.id) },
                            tint = if (current.isFavorite) Color(0xFFFF6B5A) else Color.White,
                        )
                        // Real now: every value it shows is already in the row the
                        // grid loaded, so it needs no permission and no new query.
                        HgIconButton(
                            icon = HgIcons.Info,
                            contentDescription = stringResource(R.string.details),
                            onClick = { showDetails = true },
                            tint = Color.White,
                        )
                        // Rotating is not editing. The brief rules out filters and
                        // cropping, and a rotate is a change to the file's orientation
                        // that the user can undo, so it is the one item action that
                        // belongs here.
                        HgIconButton(
                            icon = HgIcons.Rotate,
                            contentDescription = stringResource(R.string.rotate),
                            onClick = { rotateOpen = true },
                            tint = Color.White,
                        )
                        HgIconButton(
                            icon = HgIcons.Delete,
                            contentDescription = stringResource(R.string.delete),
                            // Never delete on a single tap: the reference asks
                            // first, and so must this.
                            onClick = { pendingDelete = current },
                            tint = Color.White,
                        )
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

/**
 * The page that stands in for a clip inside the viewer.
 *
 * ## Why it is a poster and a play button, and not a player
 *
 * A full player per page would mean one decoder per page, and `beyondViewportPageCount`
 * keeps a page on each side alive. Three decoders for one visible clip is the cost this
 * screen cannot pay.
 *
 * Instead the page shows the clip's own poster frame from the existing thumbnail cache and
 * offers a way in. Forward is still a page turn — the video page replaces its content in
 * place by the `ViewerReplace` rule; a tap on the poster hands the clip to the player,
 * which is the screen built for it.
 *
 * This is not a disabled control: it opens the clip.
 */
@Composable
private fun VideoPage(item: MediaItem, container: AppContainer, onOpenVideo: (Long) -> Unit) {
    val play = stringResource(R.string.video_play)
    val duration = PlayerPolicy.timecode(item.durationMs)
    val label = mediaDescription(item)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HgTestTags.viewerVideoPage(item.id)),
        contentAlignment = Alignment.Center,
    ) {
        MediaThumbnail(
            item = item,
            store = container.thumbnails,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0,
            // The badge belongs on a grid tile, where it is the only sign that a cell is
            // a clip. Here the play target and the duration say it, and a corner badge
            // over a centred play button is noise.
            showVideoBadge = false,
            // The page names itself below the target; the poster must not name it twice.
            describeImage = false,
        )

        // One target, centred, with the label on it. A poster with an invisible tap area
        // would be a tap that does nothing in most of the places a thumb lands.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val palette = hgColors
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable(onClick = { onOpenVideo(item.id) })
                    // A colour alone does not say this opens a video, and a poster is
                    // not obviously a button. The name carries it for a screen reader.
                    .semantics(mergeDescendants = true) {
                        contentDescription = play
                        role = Role.Button
                    }
                    .testTag(HgTestTags.viewerVideoPlay(item.id)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HgIcons.Play,
                    contentDescription = null,
                    tint = palette.onAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = duration,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}
