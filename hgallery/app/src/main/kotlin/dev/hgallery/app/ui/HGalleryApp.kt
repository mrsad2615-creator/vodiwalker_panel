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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.hgallery.app.ui.brand.HG_IGNITION_SCALE_EXTRA
import dev.hgallery.app.ui.brand.HgIgnition
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.HgSettings
import dev.hgallery.app.ui.albums.AlbumDetailScreen
import dev.hgallery.app.ui.albums.AlbumsScreen
import dev.hgallery.app.ui.albums.CustomAlbumScreen
import dev.hgallery.app.ui.discover.DiscoverScreen
import dev.hgallery.app.ui.highlights.HighlightsScreen
import dev.hgallery.app.ui.photos.PhotosScreen
import dev.hgallery.app.ui.settings.SettingsScreen
import dev.hgallery.app.ui.trash.TrashScreen
import dev.hgallery.app.ui.search.SearchScreen
import dev.hgallery.app.ui.shell.HgBottomBar
import dev.hgallery.app.ui.shell.HgTab
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.video.VideoPlayerScreen
import dev.hgallery.app.ui.viewer.ViewerScreen
import dev.hgallery.core.Album
import dev.hgallery.core.AlbumBuilder
import dev.hgallery.core.PlayerPolicy
import dev.hgallery.core.VideoQueue

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
    // One scope for writes that outlive the composition that started them. A repeat
    // mode chosen in the player must survive the player being closed immediately after,
    // so it cannot be tied to a per-screen scope.
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(HgTab.Photos) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var viewerId by remember { mutableStateOf<Long?>(null) }
    var playerId by remember { mutableStateOf<Long?>(null) }
    // How the player was entered. A tile tap is a request to watch; a strip cell while
    // already playing is a step through the list. The audio policy reads this, so it has
    // to be decided where the path is known — here — and not inside the player.
    var playerEntry by remember { mutableStateOf(PlayerPolicy.Entry.DIRECT) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var trashOpen by remember { mutableStateOf(false) }
    // A custom album is opened by id, not by value: its membership changes while the
    // screen is up, and a captured copy would show the membership from the moment the
    // tile was tapped.
    var customAlbumId by remember { mutableStateOf<Long?>(null) }

    // HGallery Ignition. The cover is drawn over the shell and lifts off it. It is
    // `rememberSaveable` so a rotation during the sequence does not replay a launch
    // the user has already seen: the flag survives the configuration change and the
    // cover stays gone.
    var ignitionDone by rememberSaveable { mutableStateOf(false) }
    // The screenshot harness launches with this extra so a camera that starts in about
    // a second can photograph a sequence that takes 0.9. The product never sets it, and
    // the evidence file is named for the factor. Read once, at composition.
    val context = LocalContext.current
    val ignitionCaptureScale = remember(context) {
        val activity = context as? android.app.Activity
        val read = activity?.intent?.getFloatExtra(HG_IGNITION_SCALE_EXTRA, 1f) ?: 1f
        // Logged because it was silent once and cost three runs. A capture-time scale
        // that does not arrive looks exactly like a capture that is too slow, and the
        // two have different fixes.
        android.util.Log.i(
            "HGalleryIgnition",
            "captureScale=$read extras=${activity?.intent?.extras?.keySet()?.joinToString()}",
        )
        read
    }

    val items by container.mediaStore.items.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = HgSettings(),
    )
    // One queue for the whole app: the strip and the autoplay advance read the same list,
    // so the two cannot disagree about which video is next.
    val videoQueue = remember(items) { VideoQueue.build(items) }

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
    // Settings sits above the tabs and below the viewer: it is a place, not a mode.
    BackHandler(enabled = playerId == null && viewerId == null && openAlbum == null && settingsOpen) {
        settingsOpen = false
    }
    BackHandler(
        enabled = playerId == null && viewerId == null && openAlbum == null && customAlbumId != null,
    ) { customAlbumId = null }
    BackHandler(enabled = playerId == null && viewerId == null && trashOpen) { trashOpen = false }

    // One entry point for "open this item": a video goes to the player, an image
    // to the viewer. Deciding here means every grid in the app behaves the same
    // way without repeating the rule.
    val openItem: (Long) -> Unit = { id ->
        val media = items.firstOrNull { it.id == id }
        when {
            media == null -> viewerId = id
            media.isVideo -> {
                playerEntry = PlayerPolicy.Entry.DIRECT
                playerId = id
            }
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

    if (trashOpen) {
        TrashScreen(container = container, onBack = { trashOpen = false })
        return
    }

    if (settingsOpen) {
        SettingsScreen(
            container = container,
            onBack = { settingsOpen = false },
            onOpenTrash = { trashOpen = true },
        )
        return
    }

    val openSettings: () -> Unit = { settingsOpen = true }

    Box(Modifier.fillMaxSize().background(palette.page)) {
        // The shell is composed first and the cover is drawn over it, so the library
        // scan, the first thumbnails and the skeleton all start at t=0. The sequence
        // adds no delay to anything: it hides work that is already running, and it
        // lifts early if the work finishes first, because nothing waits on it.
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val album = openAlbum
                val customId = customAlbumId
                if (customId != null) {
                    CustomAlbumScreen(
                        container = container,
                        albumId = customId,
                        items = items,
                        onBack = { customAlbumId = null },
                        onOpenItem = openItem,
                    )
                } else if (album != null) {
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
                                onOpenSettings = openSettings,
                            )
                            HgTab.Albums -> AlbumsScreen(
                                container = container,
                                onOpenAlbum = { openAlbum = it },
                                onOpenCustomAlbum = { customAlbumId = it },
                                onSearch = { searchOpen = true },
                                onOpenSettings = openSettings,
                            )
                            HgTab.Highlights -> HighlightsScreen(
                                container = container,
                                onOpenItem = openItem,
                                onSearch = { searchOpen = true },
                            )
                            HgTab.Discover -> DiscoverScreen(
                                container = container,
                                onOpenItem = openItem,
                                onSearch = { searchOpen = true },
                            )
                        }
                    }
                }
            }
            HgBottomBar(
                selected = tab,
                onSelect = {
                    tab = it
                    openAlbum = null
                    customAlbumId = null
                },
            )
        }

        val playingId = playerId
        if (playingId != null) {
            val media = items.firstOrNull { it.id == playingId }
            if (media != null) {
                VideoPlayerScreen(
                    item = media,
                    queue = videoQueue,
                    filmstripEnabled = settings.filmstripVisible,
                    repeatMode = settings.repeatMode,
                    // Written through the repository, so the player's button and the
                    // Settings row are two views of one stored value rather than two
                    // pieces of state that have to be kept in step.
                    onRepeatModeChange = { mode ->
                        scope.launch { container.settings.setRepeatMode(mode) }
                    },
                    entry = playerEntry,
                    // Switching clip replaces the item, not the screen: the player stays
                    // mounted, so the surface keeps its binding and the picture follows
                    // the sound. Either way this is a move along the list rather than a
                    // fresh choice, so the clip starts muted.
                    onSelectItem = {
                        playerEntry = PlayerPolicy.Entry.SWIPE
                        playerId = it
                    },
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
                // A still pushed onto a clip cannot play it, so the screen changes. This
                // is the hand-over branch of `ViewerReplace`: the player takes the id, and
                // the entry is DIRECT because the user pushed toward this clip on purpose.
                onOpenVideo = { id ->
                    viewerId = null
                    playerEntry = PlayerPolicy.Entry.DIRECT
                    playerId = id
                },
                onClose = { viewerId = null },
            )
        }

        if (!ignitionDone) {
            HgIgnition(
                onFinished = { ignitionDone = true },
                captureTimeScale = ignitionCaptureScale,
            )
        }
    }
}
