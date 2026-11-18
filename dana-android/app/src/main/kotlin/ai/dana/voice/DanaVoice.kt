package ai.dana.voice

import ai.dana.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.UUID
import kotlin.math.min

internal data class VoiceState(
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val initializing: Boolean = false,
    val partial: String = "",
    val strength: Float = 0f,
    val status: Int? = null,
    val systemSpeech: Boolean = false,
    val checking: Boolean = false,
    val support: Int? = null,
    val canDownload: Boolean = false,
)

/** Foreground-only, on-device recognition and voices explicitly marked offline by Android. */
internal class DanaVoice(context: Context) : DefaultLifecycleObserver {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var synthesizer: TextToSpeech? = null
    private var probe: SpeechRecognizer? = null
    private var probeTicket = 0
    private val probeTimeout = Runnable { finishProbe(R.string.voice_probe_unknown) }
    private val initTimeout = Runnable {
        if (loading) {
            releaseSpeech()
            state = state.copy(initializing = false, status = R.string.voice_tts_missing)
        }
    }
    private var utterancePrefix: String? = null
    private var loading = false
    private var voiceGeneration = 0
    private var listenGeneration = 0
    private val endCapture = Runnable { if (state.listening) finishListening() }
    private val abortCapture = Runnable { if (state.listening) endListening(R.string.voice_try_again) }
    var state by mutableStateOf(VoiceState())
        private set

    fun canListen(local: Boolean): Boolean = runCatching {
        if (local) Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        else SpeechRecognizer.isRecognitionAvailable(app)
    }.getOrDefault(false)

    fun unavailable(local: Boolean) { state = state.copy(status = if (local) R.string.voice_not_supported else R.string.voice_no_system) }

    fun checkPersian() {
        cancelProbe()
        if (!canListen(local = true)) {
            state = state.copy(support = R.string.voice_not_supported)
        } else if (Build.VERSION.SDK_INT < 33) {
            state = state.copy(support = R.string.voice_probe_unknown)
        } else {
            probePersian()
        }
    }

    @RequiresApi(33)
    private fun probePersian() {
        val ticket = ++probeTicket
        try {
            val service = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            probe = service
            state = state.copy(checking = true, support = null, canDownload = false)
            handler.postDelayed(probeTimeout, 5_000L)
            service.checkRecognitionSupport(speechRequest(local = true), ContextCompat.getMainExecutor(app), object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    if (ticket != probeTicket) return
                    fun List<String>.hasPersian() = any { it.substringBefore('-').substringBefore('_').equals("fa", ignoreCase = true) }
                    when {
                        recognitionSupport.installedOnDeviceLanguages.hasPersian() -> finishProbe(R.string.voice_probe_ready)
                        recognitionSupport.pendingOnDeviceLanguages.hasPersian() -> finishProbe(R.string.voice_probe_pending)
                        recognitionSupport.supportedOnDeviceLanguages.hasPersian() -> finishProbe(R.string.voice_probe_download, download = true)
                        else -> finishProbe(R.string.voice_probe_not_listed)
                    }
                }
                override fun onError(error: Int) {
                    if (ticket == probeTicket) finishProbe(R.string.voice_probe_unknown)
                }
            })
        } catch (_: RuntimeException) { finishProbe(R.string.voice_probe_unknown) }
    }

    fun requestPersianDownload() {
        if (Build.VERSION.SDK_INT < 33 || !state.canDownload) return
        cancelProbe()
        try {
            val service = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            probe = service
            service.triggerModelDownload(speechRequest(local = true))
            // This asks Android to schedule a model download; it does not report completion.
            state = state.copy(support = R.string.voice_download_requested)
            handler.postDelayed({ if (probe === service) cancelProbe() }, 15_000L)
        } catch (_: RuntimeException) { finishProbe(R.string.voice_probe_unknown) }
    }

    private fun finishProbe(message: Int, download: Boolean = false) {
        cancelProbe()
        state = state.copy(support = message, canDownload = download)
    }

    private fun cancelProbe() {
        ++probeTicket
        handler.removeCallbacks(probeTimeout)
        val service = probe
        probe = null
        runCatching { service?.destroy() }
        state = state.copy(checking = false, canDownload = false)
    }

    fun permissionDenied() { state = state.copy(status = R.string.voice_denied) }

    @SuppressLint("MissingPermission") // Both entry points check permission before requesting microphone access.
    fun listen(local: Boolean, onText: (String) -> Unit) {
        stopPlayback()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied()
            return
        }
        if (!canListen(local)) {
            unavailable(local)
            return
        }
        cancelProbe()
        endListening(null)
        val ticket = ++listenGeneration
        try {
            val service = if (local && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            else if (!local) SpeechRecognizer.createSpeechRecognizer(app) else return
            recognizer = service
            service.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (ticket == listenGeneration) state = state.copy(strength = 0f)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    if (ticket == listenGeneration && rmsdB.isFinite()) state = state.copy(strength = ((rmsdB + 2f) / 13f).coerceIn(0f, 1f))
                }
                override fun onError(error: Int) {
                    if (ticket != listenGeneration) return
                    val message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_denied
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_persian_unavailable
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            if (local) R.string.voice_persian_unavailable else R.string.voice_speech_network
                        else -> R.string.voice_try_again
                    }
                    endListening(message)
                }
                override fun onResults(results: Bundle?) {
                    if (ticket != listenGeneration) return
                    val text = firstText(results).trim()
                    endListening(if (text.isEmpty()) R.string.voice_try_again else null)
                    if (text.isNotEmpty()) onText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (ticket == listenGeneration) state = state.copy(partial = firstText(partialResults))
                }
            })
            state = state.copy(listening = true, partial = "", strength = 0f, status = null, systemSpeech = !local)
            service.startListening(speechRequest(local))
            handler.postDelayed(endCapture, 28_000L)
        } catch (_: RuntimeException) {
            endListening(if (local) R.string.voice_not_supported else R.string.voice_no_system)
        }
    }

    private fun speechRequest(local: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, local)

    fun finishListening() {
        if (state.listening) {
            handler.removeCallbacks(endCapture)
            handler.removeCallbacks(abortCapture)
            try {
                recognizer?.stopListening()
                handler.postDelayed(abortCapture, 5_000L)
            } catch (_: RuntimeException) {
                endListening(R.string.voice_try_again)
            }
        }
    }

    private fun endListening(status: Int?) {
        ++listenGeneration
        handler.removeCallbacks(endCapture)
        handler.removeCallbacks(abortCapture)
        val service = recognizer
        recognizer = null
        if (service != null) {
            runCatching { service.cancel() }
            runCatching { service.destroy() }
        }
        state = state.copy(listening = false, partial = "", strength = 0f, systemSpeech = false, status = status)
    }

    fun read(text: String) {
        if (text.isBlank() || loading) return
        endListening(null)
        stopPlayback()
        synthesizer?.let { speak(it, text); return }
        val ticket = ++voiceGeneration
        loading = true
        state = state.copy(initializing = true, status = null)
        handler.postDelayed(initTimeout, 8_000L)
        try {
            synthesizer = TextToSpeech(app) { code ->
                handler.post {
                    if (ticket != voiceGeneration) return@post
                    handler.removeCallbacks(initTimeout)
                    loading = false
                    state = state.copy(initializing = false)
                    val engine = synthesizer
                    if (engine == null || code != TextToSpeech.SUCCESS) {
                        releaseSpeech()
                        state = state.copy(status = R.string.voice_tts_missing)
                    } else speak(engine, text)
                }
            }
        } catch (_: RuntimeException) {
            releaseSpeech()
            state = state.copy(initializing = false, status = R.string.voice_tts_missing)
        }
    }

    private fun speak(engine: TextToSpeech, raw: String) {
        try {
            val voice = engine.voices.orEmpty().filter {
                it.locale.language == "fa" && !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty()
            }.maxByOrNull { it.quality }
            if (voice == null || engine.setVoice(voice) != TextToSpeech.SUCCESS) {
                state = state.copy(status = R.string.voice_tts_missing)
                return
            }
            val limit = min(TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(128), 3_500)
            val chunks = splitText(raw, limit)
            if (chunks.isEmpty()) return
            val prefix = UUID.randomUUID().toString()
            utterancePrefix = prefix
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == "$prefix:${chunks.lastIndex}") handler.post { finish(prefix, null) } }
                @Deprecated("Android compatibility")
                override fun onError(utteranceId: String?) { handler.post { finish(prefix, R.string.voice_play_error) } }
            })
            state = state.copy(speaking = true, status = null)
            chunks.forEachIndexed { index, text ->
                if (engine.speak(text, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$prefix:$index") != TextToSpeech.SUCCESS) {
                    finish(prefix, R.string.voice_play_error)
                    return
                }
            }
        } catch (_: RuntimeException) {
            stopPlayback()
            state = state.copy(status = R.string.voice_play_error)
        }
    }

    private fun finish(prefix: String, problem: Int?) {
        if (utterancePrefix == prefix) {
            stopPlayback()
            state = state.copy(status = problem)
        }
    }

    fun stopPlayback() {
        if (loading) releaseSpeech()
        utterancePrefix = null
        runCatching { synthesizer?.stop() }
        state = state.copy(speaking = false, initializing = false)
    }

    private fun releaseSpeech() {
        handler.removeCallbacks(initTimeout)
        ++voiceGeneration
        loading = false
        runCatching { synthesizer?.stop() }
        runCatching { synthesizer?.shutdown() }
        synthesizer = null
        utterancePrefix = null
    }

    fun close() {
        cancelProbe()
        endListening(null)
        releaseSpeech()
        state = VoiceState()
    }

    override fun onStop(owner: LifecycleOwner) { close() }

    private fun firstText(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().take(6_000)

    private fun splitText(raw: String, limit: Int): List<String> = buildList {
        var position = 0
        while (position < raw.length) {
            val end = min(position + limit, raw.length)
            var cut = if (end == raw.length) end else raw.lastIndexOfAny(charArrayOf(' ', '.', '؟', '\n'), end - 1)
                .takeIf { it > position + limit / 2 }?.plus(1) ?: end
            if (cut in 1 until raw.length && raw[cut - 1].isHighSurrogate()) cut--
            val part = raw.substring(position, cut).trim()
            if (part.isNotEmpty()) add(part)
            position = cut
        }
    }
}
