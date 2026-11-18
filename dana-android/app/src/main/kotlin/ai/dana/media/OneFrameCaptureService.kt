package ai.dana.media

import ai.dana.MainActivity
import ai.dana.R
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class OneFrameCaptureService : Service() {
    @Inject internal lateinit var inbox: CaptureInbox
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var task: Job? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var held: Image? = null
    private var display: VirtualDisplay? = null
    private var ticket = 0L
    private var frameWidth = 0
    private var frameHeight = 0
    private var closed = false
    private var started = false

    private val listener = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!closed && projection != null) finish(CaptureResult.Failed(R.string.media_capture_stopped))
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT >= 34 && !closed && display != null && width > 0 && height > 0) {
                try { fitDisplay(width, height) } catch (_: RuntimeException) { finish(CaptureResult.Failed(R.string.media_capture_error)) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launchTicket = intent?.getLongExtra(EXTRA_TICKET, 0L) ?: 0L
        val grant = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_GRANT, Intent::class.java) }
        if (started) {
            if (launchTicket != ticket) inbox.publish(launchTicket, CaptureResult.Failed(R.string.media_capture_busy))
            return START_NOT_STICKY
        }
        if (!inbox.isCurrent(launchTicket) || grant == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        ticket = launchTicket
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            val service = getSystemService(MediaProjectionManager::class.java) ?: error("Projection unavailable")
            val session = service.getMediaProjection(Activity.RESULT_OK, grant) ?: error("Projection not granted")
            projection = session
            session.registerCallback(listener, handler)
            val metrics = resources.displayMetrics
            val bounds = if (Build.VERSION.SDK_INT >= 30) getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds else null
            val (w, h) = FrameCodec.bounded(bounds?.width() ?: metrics.widthPixels, bounds?.height() ?: metrics.heightPixels)
            frameWidth = w
            frameHeight = h
            val target = makeReader(w, h)
            reader = target
            display = session.createVirtualDisplay("Dana one-shot", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, target.surface, null, handler)
                ?: error("Projection display unavailable")
            task = scope.launch { captureOnce() }
        } catch (_: RuntimeException) {
            finish(CaptureResult.Failed(R.string.media_capture_error))
        }
        return START_NOT_STICKY
    }

    private fun fitDisplay(width: Int, height: Int) {
        val (w, h) = FrameCodec.bounded(width, height)
        if (w == frameWidth && h == frameHeight) return
        val active = display ?: return
        val next = makeReader(w, h)
        val previous = reader
        try {
            active.resize(w, h, resources.displayMetrics.densityDpi)
            active.surface = next.surface
            reader = next
            frameWidth = w
            frameHeight = h
        } catch (error: RuntimeException) {
            next.close()
            throw error
        }
        held?.close()
        held = null
        previous?.close()
    }

    private suspend fun captureOnce() {
        try {
            // Give the user time to switch apps on versions that only offer the entire display.
            repeat(7) {
                delay(375)
                // Keep the latest buffer in case the source stops producing frames while it is still.
                reader?.acquireLatestImage()?.let { fresh -> held?.close(); held = fresh }
                if (!inbox.isCurrent(ticket)) {
                    finish(CaptureResult.Failed(R.string.media_capture_stopped))
                    return
                }
            }
            val bitmap = withTimeoutOrNull(4_500) {
                var frame: Bitmap? = null
                if (inbox.isCurrent(ticket)) {
                    val queued = held
                    held = null
                    if (queued != null) queued.use { frame = copyRgba(it) }
                }
                while (isActive && frame == null && inbox.isCurrent(ticket)) {
                    reader?.acquireLatestImage()?.use { image ->
                        frame = copyRgba(image)
                    }
                    if (frame == null) delay(110)
                }
                frame
            }
            if (bitmap == null) {
                finish(CaptureResult.Failed(R.string.media_capture_error))
                return
            }
            // Stop the OS projection before OCR or user review. Only the single bitmap remains.
            releaseProjection()
            val data = try { withContext(Dispatchers.Default) {
                if (emptyOrBlack(bitmap)) null else FrameCodec.compress(bitmap)
            } } finally { bitmap.recycle() }
            finish(if (data == null) CaptureResult.Failed(R.string.media_capture_blank) else CaptureResult.Ready(data))
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (_: Exception) {
            finish(CaptureResult.Failed(R.string.media_capture_error))
        }
    }

    private fun releaseProjection() {
        // Unregister before stopping: we are deliberately ending this one-shot grant.
        projection?.let { source -> runCatching { source.unregisterCallback(listener) } }
        runCatching { display?.release() }
        display = null
        runCatching { held?.close() }
        held = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
    }

    private fun finish(result: CaptureResult) {
        if (closed) return
        closed = true
        inbox.publish(ticket, result)
        releaseProjection()
        task?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!closed && inbox.isCurrent(ticket)) inbox.publish(ticket, CaptureResult.Failed(R.string.media_capture_stopped))
        closed = true
        scope.cancel()
        releaseProjection()
        super.onDestroy()
    }

    private fun copyRgba(image: Image): Bitmap {
        val w = image.width
        val h = image.height
        val plane = image.planes.first()
        require(w in 1..1_536 && h in 1..1_536 && plane.pixelStride == 4 && plane.rowStride >= w * 4)
        val rgba = ByteBuffer.allocateDirect(w * h * 4)
        val source = plane.buffer
        val limit = source.limit()
        for (y in 0 until h) {
            val offset = y * plane.rowStride
            require(offset + w * 4 <= limit)
            source.limit(limit)
            source.position(offset)
            source.limit(offset + w * 4)
            rgba.put(source)
        }
        rgba.flip()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(rgba); setHasAlpha(false) }
    }

    private fun emptyOrBlack(bitmap: Bitmap): Boolean {
        val h = bitmap.height / 23 + 1
        val w = bitmap.width / 23 + 1
        for (y in 0 until bitmap.height step h) for (x in 0 until bitmap.width step w) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.red(pixel) > 7 || Color.green(pixel) > 7 || Color.blue(pixel) > 7) return false
        }
        return true
    }

    private fun notification(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.media_capture), NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 31, Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_MEDIA).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_dana)
            .setContentTitle(getString(R.string.media_capture)).setContentText(getString(R.string.media_capture_notification))
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
    }

    internal companion object {
        const val ACTION_OPEN_MEDIA = "ai.dana.OPEN_MEDIA"
        const val EXTRA_TICKET = "capture-ticket"
        const val EXTRA_GRANT = "capture-grant"
        private const val CHANNEL = "one_frame_capture"
        private const val NOTIFICATION_ID = 533
        private fun makeReader(w: Int, h: Int) = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
    }
}
