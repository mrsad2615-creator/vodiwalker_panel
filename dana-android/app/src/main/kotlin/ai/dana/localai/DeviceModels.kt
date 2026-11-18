package ai.dana.localai

import ai.dana.R
import ai.dana.domain.ContextMessage
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.MessageRole
import android.content.Context
import android.os.Process
import androidx.annotation.StringRes
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class DeviceModel(val id: String, val title: String, @StringRes val blurb: Int) {
    QWEN("qwen3-0.6b", "Qwen3 0.6B", R.string.models_qwen),
    SMOL("smollm2-135m", "SmolLM2 135M", R.string.models_smol),
    ;
    val asset: String get() = "models/$id.litertlm"
}

internal sealed interface DeviceText {
    data object Preparing : DeviceText
    data object Generating : DeviceText
    data class Delta(val value: String) : DeviceText
}

internal class DeviceModelProblem(@StringRes val messageRes: Int) : Exception()

/** CPU-only runtime. It has no credentials, URLs, network transport, or tools. Only one native session at a time. */
@Singleton
internal class DeviceModels @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private val pins by lazy {
        val json = org.json.JSONObject(context.assets.open("models/catalog.json").bufferedReader().use { it.readText() })
        DeviceModel.entries.associateWith { model ->
            val pin = json.getJSONObject(model.id)
            Pin(pin.getLong("bytes"), pin.getString("sha256"))
        }
    }

    suspend fun available(): Set<DeviceModel> = withContext(Dispatchers.IO) {
        if (!Process.is64Bit()) emptySet() else DeviceModel.entries.filter { model ->
            runCatching { context.assets.openFd(model.asset).use { it.length == pins.getValue(model).bytes } }.getOrDefault(false)
        }.toSet()
    }

    fun complete(model: DeviceModel, prompt: String, previous: List<ContextMessage>): Flow<DeviceText> = flow {
        if (!Process.is64Bit()) throw DeviceModelProblem(R.string.models_device_unsupported)
        if (prompt.toByteArray(Charsets.UTF_8).size !in 1..PROMPT_BYTES) throw DeviceModelProblem(R.string.models_prompt_length)
        emit(DeviceText.Preparing)
        lock.withLock {
            val path = modelFile(model)
            // The total bounded input (including one small exchange) leaves room for the reply in the KV cache.
            val recent = previous.takeLast(2).takeIf { pair ->
                pair.size == 2 && pair[0].role == MessageRole.USER && pair[1].role == MessageRole.ASSISTANT &&
                    pair.sumOf { it.body.toByteArray(Charsets.UTF_8).size } <= 380
            }.orEmpty()
            val qwen = model == DeviceModel.QWEN
            Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
            val engine = Engine(EngineConfig(modelPath = path.path,
                backend = Backend.CPU(threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)),
                maxNumTokens = 1792, cacheDir = ":nocache"))
            engine.initialize()
            engine.use {
                currentCoroutineContext().ensureActive()
                engine.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(if (qwen) "Answer briefly in the language used by the user. If you are unsure, say so." else "Answer briefly in English."),
                    initialMessages = recent.map { if (it.role == MessageRole.USER) Message.user(it.body) else Message.model(it.body) },
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.8, temperature = 0.7, seed = 42),
                    automaticToolCalling = false,
                    maxOutputToken = 112,
                    thinkingConfig = ThinkingConfig(enableThinking = false).takeIf { qwen },
                )).use { conversation ->
                    emit(DeviceText.Generating)
                    var visible = false
                    var count = 0
                    var finished = false
                    try {
                        // The upstream callback uses trySend; don't drop deltas under UI load. Decode itself is bounded.
                        conversation.sendMessageAsync(prompt).buffer(Channel.UNLIMITED).collect { message ->
                            val text = message.contents.toString() // No reasoning channels or tool calls.
                            count += text.length
                            if (count > 8_192) throw EngineFailure(EngineProblem.OUTPUT_LIMIT)
                            visible = visible || text.isNotBlank()
                            if (text.isNotEmpty()) emit(DeviceText.Delta(text))
                        }
                        finished = true
                    } finally {
                        if (!finished) conversation.cancelProcess()
                    }
                    if (!visible) throw EngineFailure(EngineProblem.NO_TEXT)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Native files are extracted from the signed APK, not downloaded or accepted from another app. */
    private suspend fun modelFile(model: DeviceModel): File {
        val pin = pins.getValue(model)
        val dir = File(context.noBackupFilesDir, "device-models")
        // Include the revision digest so an app update cannot accidentally reuse an older model of the same size.
        val dest = File(dir, "${model.id}-${pin.sha.take(16)}.litertlm")
        if (dest.isFile && dest.length() == pin.bytes) return dest
        dir.mkdirs()
        dir.listFiles()?.filter { it.name.startsWith("${model.id}-") && it != dest }?.forEach { it.delete() }
        if (dir.usableSpace < pin.bytes + 48L * 1024 * 1024) throw DeviceModelProblem(R.string.models_no_space)
        val partial = File(dir, "${model.id}.tmp")
        try {
            val input = try { context.assets.open(model.asset) } catch (_: IOException) {
                throw DeviceModelProblem(R.string.models_missing)
            }
            val hash = MessageDigest.getInstance("SHA-256")
            var written = 0L
            input.use { source ->
                partial.outputStream().buffered(256 * 1024).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > pin.bytes) throw DeviceModelProblem(R.string.models_missing)
                        hash.update(buffer, 0, count)
                        out.write(buffer, 0, count)
                    }
                }
            }
            val expected = pin.sha.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (written != pin.bytes || !MessageDigest.isEqual(hash.digest(), expected)) throw DeviceModelProblem(R.string.models_missing)
            if (!partial.renameTo(dest)) throw DeviceModelProblem(R.string.models_storage)
            return dest
        } catch (_: IOException) {
            throw DeviceModelProblem(R.string.models_storage)
        } finally {
            partial.delete()
        }
    }

    private data class Pin(val bytes: Long, val sha: String)

    companion object { const val PROMPT_BYTES = 1050 }
}
