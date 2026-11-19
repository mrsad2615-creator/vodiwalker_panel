package dev.mrsad.hr

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

val tracks = listOf("Minimal", "Digital", "Cinematic", "Ambient")

class Player(private val ctx: Context) {
    private var media: MediaPlayer? = null

    private val manager = requireNotNull(ctx.getSystemService(AudioManager::class.java))
    private var request: AudioFocusRequest? = null

    fun stop() {
        media?.release()
        media = null
        request?.let(manager::abandonAudioFocusRequest)
        request = null
    }

    fun play(name: String, volume: Float, end: () -> Unit = {}) {
        stop()
        val attributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val focus =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    if (change < 0) {
                        stop()
                        end()
                    }
                }
                .build()
        if (
            volume <= 0 ||
                manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            end()
            return
        }
        request = focus
        val id =
            listOf(R.raw.minimal, R.raw.digital, R.raw.cinematic, R.raw.ambient)[
                tracks.indexOf(name).coerceAtLeast(0)]
        val sound = MediaPlayer()
        media = sound
        try {
            sound.setAudioAttributes(attributes)
            ctx.resources.openRawResourceFd(id).use {
                sound.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            sound.setVolume(volume, volume)
            sound.setOnCompletionListener {
                stop()
                end()
            }
            sound.setOnErrorListener { _, _, _ ->
                stop()
                end()
                true
            }
            sound.setOnPreparedListener { if (media === it) it.start() }
            sound.prepareAsync()
        } catch (e: Exception) {
            stop()
            end()
            android.util.Log.w("MrsadHR", "Audio unavailable", e)
        }
    }
}

class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val player by lazy { Player(this) }
    private val alarm by lazy { requireNotNull(getSystemService(AlarmManager::class.java)) }
    private val wake by lazy {
        PendingIntent.getForegroundService(
            this,
            2,
            Intent(this, TimerService::class.java).setAction("expiry"),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val nm by lazy { requireNotNull(getSystemService(NotificationManager::class.java)) }

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        nm.createNotificationChannel(
            NotificationChannel("clock", "Active countdown", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel("finish", "Completed timers", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(active: Boolean, ms: Long): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Builder(this, if (active) "clock" else "finish")
            .setSmallIcon(R.drawable.mark)
            .setContentTitle(if (active) "MrsadHR · Countdown" else "MrsadHR · Time is up")
            .setContentIntent(open)
            .setOngoing(active)
            .setAutoCancel(!active)
            .setWhen(System.currentTimeMillis() + ms)
            .setUsesChronometer(active)
            .setChronometerCountDown(active)
            .setShowWhen(active)
            .build()
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val repo = (application as Studio).clocks
        val clock = repo.timer.value
        val left = clock.value(SystemClock.elapsedRealtime())
        startForeground(1, notification(true, left))
        job?.cancel()
        player.stop()
        if (clock.phase != Phase.RUNNING) {
            stopSelf()
            return START_NOT_STICKY
        }
        alarm.cancel(wake)
        if (left > 0) {
            val deadline = clock.anchor + clock.goal - clock.bank
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    deadline,
                    wake,
                )
            else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, wake)
        }
        job = scope.launch {
            delay(left)
            alarm.cancel(wake)
            repo.update(false) { it.settle(SystemClock.elapsedRealtime()) }
            if (repo.timer.value.phase == Phase.COMPLETE) {
                val p = options.data.first()
                if (p[stringPreferencesKey("notify")] != "false")
                    nm.notify(3, notification(false, 0))
                if (p[stringPreferencesKey("haptic")] != "false") {
                    val v =
                        if (Build.VERSION.SDK_INT >= 31)
                            requireNotNull(getSystemService(VibratorManager::class.java))
                                .defaultVibrator
                        else
                            @Suppress("DEPRECATION")
                            requireNotNull(getSystemService(Vibrator::class.java))
                    v.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                player.play(
                    p[stringPreferencesKey("sound")] ?: "Minimal",
                    p[stringPreferencesKey("volume")]?.toFloatOrNull() ?: 0.7f,
                )
                delay(2150)
            }
            stopSelfResult(id)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alarm.cancel(wake)
        scope.cancel()
        player.stop()
        super.onDestroy()
    }
}
