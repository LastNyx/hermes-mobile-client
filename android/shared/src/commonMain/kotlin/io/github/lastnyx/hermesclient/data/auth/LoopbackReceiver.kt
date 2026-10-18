package io.github.lastnyx.hermesclient.data.auth

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder

/** Accepts exactly one valid /callback request on 127.0.0.1 (the server rejects `localhost`). */
class LoopbackReceiver(private val expectedState: String) : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))

    val redirectUri: String get() = "http://127.0.0.1:${server.localPort}/callback"

    /** Blocking. Returns the gateway code, or a failure carrying a user-facing reason. */
    fun awaitCode(timeoutMs: Long): Result<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return Result.failure(IllegalStateException("Sign-in timed out"))
            server.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val outcome = try {
                server.accept().use { handle(it) }
            } catch (e: SocketTimeoutException) {
                return Result.failure(IllegalStateException("Sign-in timed out"))
            } catch (e: java.io.IOException) {
                null
            }
            if (outcome != null) return outcome
        }
    }

    private fun handle(socket: java.net.Socket): Result<String>? {
        socket.soTimeout = 5000
        val line = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine() ?: return null
        val target = line.split(' ').getOrNull(1) ?: return null
        val path = target.substringBefore('?')
        val params = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        if (path != "/callback") {
            respond(socket, 404, "Not found")
            return null
        }
        if (params["state"] != expectedState) {
            respond(socket, 400, "Invalid sign-in state")
            return null
        }
        val error = params["error"]
        val code = params["code"]
        respond(socket, 200, "You can close this tab and return to Hermes")
        return when {
            error != null -> Result.failure(IllegalStateException(params["error_description"] ?: error))
            !code.isNullOrEmpty() -> Result.success(code)
            else -> Result.failure(IllegalStateException("Sign-in returned no code"))
        }
    }

    private fun respond(socket: java.net.Socket, status: Int, message: String) {
        val body = "<html><body><p>$message</p></body></html>".toByteArray()
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
    }

    override fun close() = server.close()
}
