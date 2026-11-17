package ai.dana.data.network

import ai.dana.data.network.EngineProtocol.string
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ProviderProtocol
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.serialization.json.JsonObject
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody

internal fun Response.requireSuccess(protocol: ProviderProtocol) {
    if (isSuccessful) return
    val problem = when (code) {
        400, 404, 422 -> EngineProblem.CONFIGURATION
        401 -> EngineProblem.AUTHENTICATION
        403 -> EngineProblem.FORBIDDEN
        408, 504 -> EngineProblem.TIMEOUT
        429 -> EngineProblem.RATE_LIMIT
        in 300..399 -> EngineProblem.PROTOCOL
        503 -> if (protocol == ProviderProtocol.APPLE_MAC) {
            val error = runCatching {
                val root = EngineProtocol.decodeObject(body.boundedUtf8(8_192))
                (root["error"] as? JsonObject)?.get("code").string()
            }.getOrNull()
            EngineProtocol.appleProblem(error)
        } else EngineProblem.SERVER
        else -> EngineProblem.SERVER
    }
    throw EngineFailure(problem, code)
}

internal fun ResponseBody.boundedUtf8(limit: Long): String {
    val source = source()
    if (contentLength() > limit || source.request(limit + 1)) throw EngineFailure(EngineProblem.PROTOCOL)
    return source.readUtf8()
}

internal fun IOException.toEngineFailure() = EngineFailure(when (this) {
    is SSLException -> EngineProblem.TLS
    is InterruptedIOException -> EngineProblem.TIMEOUT
    is EOFException -> EngineProblem.PROTOCOL
    is OutsidePrivateNetwork -> EngineProblem.CONFIGURATION
    else -> EngineProblem.NETWORK
})

private class OutsidePrivateNetwork : UnknownHostException("Apple bridge must resolve to a private address")

/** Enforce LAN at socket resolution, not by trusting the label or the DNS name. */
internal class ApplePrivateDns(private val delegate: Dns) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = delegate.lookup(hostname)
        .filter { address ->
            val bytes = address.address
            val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || uniqueLocalV6
        }.ifEmpty { throw OutsidePrivateNetwork() }
}

internal fun OkHttpClient.privateAppleClient(): OkHttpClient = newBuilder()
    // Use a separate connection pool; do not reuse sockets or HTTP/2 routes from WAN profiles.
    .connectionPool(ConnectionPool())
    .dns(ApplePrivateDns(dns))
    .proxy(Proxy.NO_PROXY)
    .readTimeout(86, TimeUnit.SECONDS)
    .build()
