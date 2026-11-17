package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.EngineTransport
import ai.dana.domain.ProviderConfig
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
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

internal class OkHttpEngineTransport(private val client: OkHttpClient = danaHttpClient()) : EngineTransport {
    override fun stream(config: ProviderConfig, key: ApiCredential?, prompt: String): Flow<String> = callbackFlow {
        val call = client.newCall(EngineProtocol.request(config, key, prompt))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e.toEngineFailure())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!response.isSuccessful) throw EngineFailure(httpProblem(response.code))
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

    private fun httpProblem(status: Int) = when (status) {
        400, 404, 422 -> EngineProblem.CONFIGURATION
        401 -> EngineProblem.AUTHENTICATION
        403 -> EngineProblem.FORBIDDEN
        408, 504 -> EngineProblem.TIMEOUT
        429 -> EngineProblem.RATE_LIMIT
        in 300..399 -> EngineProblem.PROTOCOL
        else -> EngineProblem.SERVER
    }

    private fun IOException.toEngineFailure() = EngineFailure(when (this) {
        is SSLException -> EngineProblem.TLS
        is InterruptedIOException -> EngineProblem.TIMEOUT
        is EOFException -> EngineProblem.PROTOCOL
        else -> EngineProblem.NETWORK
    })
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
