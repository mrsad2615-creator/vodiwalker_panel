package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.ConversationInput
import ai.dana.domain.EngineText
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.EngineTransport
import ai.dana.domain.ProviderConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Response

internal class OkHttpEngineTransport(
    private val client: OkHttpClient = danaHttpClient(),
    private val lanClient: OkHttpClient = client.privateLanClient(),
) : EngineTransport {
    override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> = callbackFlow {
        val http = if (config.network == ProviderNetwork.LOCAL_NETWORK) lanClient else client
        val call = http.newCall(EngineProtocol.request(config, key, input))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e.toEngineFailure())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        response.requireSuccess(config.protocol)
                        val body = response.body
                        val type = body.contentType()
                        if (type?.type != "text" || type.subtype != "event-stream") {
                            throw EngineFailure(EngineProblem.PROTOCOL)
                        }
                        EngineProtocol.read(body.source(), config.protocol) { chunk -> trySendBlocking(chunk).isSuccess }
                    }
                    close()
                } catch (error: EngineFailure) {
                    close(error)
                } catch (error: IOException) {
                    close(error.toEngineFailure())
                } catch (_: Exception) {
                    close(EngineFailure(EngineProblem.PROTOCOL))
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(8)

}

internal fun danaHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .connectTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .readTimeout(35, TimeUnit.SECONDS)
    .callTimeout(100, TimeUnit.SECONDS)
    .build()
