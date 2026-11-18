package ai.dana.data.network

import ai.dana.domain.RouterApproval
import ai.dana.domain.RouterCode
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Short-lived, one-use browser callback. Not exported, not bound to Wi-Fi/LAN, never accepts chat data. */
object RouterLoopback {
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private const val MAX_HEADER_BYTES = 12_288

    suspend fun receive(openBrowser: (String) -> Boolean): RouterCode? = withTimeoutOrNull(240_000) {
        withContext(Dispatchers.IO) {
            ServerSocket(0, 4, address).use { server ->
                server.soTimeout = 250
                val approval = RouterApproval.create()
                if (!withContext(Dispatchers.Main.immediate) { openBrowser(approval.loopbackUrl(server.localPort)) }) return@use null
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                    val code = socket.use {
                        try { readCallback(it, server.localPort, approval) } catch (_: IOException) { null }
                    }
                    if (code != null) return@use code
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }
    }

    private fun readCallback(socket: Socket, port: Int, approval: RouterApproval): RouterCode? {
        socket.soTimeout = 1_200
        val input = BufferedInputStream(socket.getInputStream())
        val head = StringBuilder()
        val started = System.nanoTime()
        // No readLine() on untrusted data. Bound bytes, idle time and total time per connection.
        while (head.length < MAX_HEADER_BYTES && System.nanoTime() - started < 1_500_000_000L) {
            val b = input.read()
            if (b != 9 && b != 10 && b != 13 && b !in 32..126) return null
            head.append(b.toChar())
            if (head.endsWith("\r\n\r\n")) break
        }
        if (!head.endsWith("\r\n\r\n")) return null
        val lines = head.split("\r\n")
        val request = lines.first().split(' ')
        val hosts = lines.drop(1).filter { it.startsWith("Host:", ignoreCase = true) }
        val code = if (request.size == 3 && request[0] == "GET" && request[2] == "HTTP/1.1" &&
            hosts.singleOrNull()?.substringAfter(':')?.trim()?.equals("localhost:$port", ignoreCase = true) == true
        ) approval.acceptCallback(request[1]) else null
        val body = (if (code != null) "اتصال تأیید شد. به برنامه مرصاد برگردید." else "این پیوند معتبر نیست. به مرصاد برگردید.").toByteArray(Charsets.UTF_8)
        // A disconnected browser must not lose a valid one-shot callback.
        runCatching {
            socket.getOutputStream().apply {
                val status = if (code != null) "200 OK" else "400 Bad Request"
                write(("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=UTF-8\r\nCache-Control: no-store\r\n" +
                    "Referrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }
        return code
    }
}
