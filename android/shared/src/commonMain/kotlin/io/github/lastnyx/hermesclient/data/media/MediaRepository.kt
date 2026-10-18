package io.github.lastnyx.hermesclient.data.media

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener

private const val MAX_IMAGE_BODY = 40L * 1024 * 1024

private const val MAX_TEXT_BODY = 4L * 1024 * 1024

private class Refused(message: String) : Exception(message)

/** Agent pictures and files from the gateway over REST. Blocking; call off the main thread. */
class MediaRepository(
    private val http: AuthedHttp,
    baseUrl: String,
    private val maxDownloadBytes: Long = 64L * 1024 * 1024,
) {
    private val base = baseUrl.trimEnd('/')

    private fun request(route: String, vararg params: Pair<String, String>) = Request.Builder().url(
        "$base$route".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build(),
    ).build()

    fun image(path: String): Result<ByteArray> = attempt(IMAGE_FAILED) {
        http.execute(request("/api/media", "path" to path)).use { response ->
            if (!response.isSuccessful) throw Refused(imageSentence(response.code))
            val body = readBounded(response, MAX_IMAGE_BODY) ?: throw Refused(IMAGE_TOO_LARGE)
            val json = JSONObject(String(body, Charsets.UTF_8))
            // optString on JSON null would return the text "null".
            if (json.isNull("data_url")) throw Refused(IMAGE_FAILED)
            parseDataUrl(json.getString("data_url")) ?: throw Refused(IMAGE_FAILED)
        }
    }

    fun download(path: String, sessionId: String?): Result<ByteArray> = attempt(FILE_FAILED) {
        val params = listOfNotNull("path" to path, sessionId?.takeIf { it.isNotBlank() }?.let { "session_id" to it })
        var response = http.execute(request("/api/fs/download", *params.toTypedArray()))
        if (response.code == 404 || response.code == 405) {
            response.close()
            response = http.execute(request("/api/files/download", "path" to path))
        }
        response.use {
            if (!it.isSuccessful) throw Refused(fileSentence(it.code))
            readBounded(it, maxDownloadBytes) ?: throw Refused(FILE_TOO_LARGE)
        }
    }

    fun readText(path: String): Result<FileText> = attempt(OPEN_FAILED) {
        http.execute(request("/api/fs/read-text", "path" to path)).use { response ->
            if (!response.isSuccessful) throw Refused(textSentence(response.code))
            val body = readBounded(response, MAX_TEXT_BODY) ?: throw Refused(OPEN_FAILED)
            parseFileText(JSONTokener(String(body, Charsets.UTF_8)).nextValue() as? JSONObject ?: JSONObject())
        }
    }

    private fun textSentence(code: Int) = when (code) {
        403 -> "The gateway won't show this file."
        404, 405 -> "This gateway can't show files yet."
        413 -> "This file is too large to view."
        else -> OPEN_FAILED
    }

    private fun <T> attempt(fallback: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Refused) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(Exception(CANT_REACH_GATEWAY))
    } catch (e: Exception) {
        Result.failure(Exception(fallback))
    }

    /** Null when the body is bigger than [cap]; the rest of it is never read. */
    private fun readBounded(response: Response, cap: Long): ByteArray? {
        val body = response.body ?: return ByteArray(0)
        if (body.contentLength() > cap) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val stream = body.byteStream()
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            if (out.size() + n > cap) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun imageSentence(code: Int) = when (code) {
        403, 415 -> "This picture can't be shown."
        404 -> "This picture is no longer on the gateway."
        413 -> IMAGE_TOO_LARGE
        else -> IMAGE_FAILED
    }

    private fun fileSentence(code: Int) = when (code) {
        403 -> "The gateway won't hand this file over."
        404 -> "This file is no longer on the gateway."
        413 -> FILE_TOO_LARGE
        else -> FILE_FAILED
    }

    private companion object {
        const val IMAGE_TOO_LARGE = "This picture is too large."
        const val IMAGE_FAILED = "Couldn't load this picture."
        const val FILE_TOO_LARGE = "This file is too large to download."
        const val FILE_FAILED = "Couldn't download this file."
        const val OPEN_FAILED = "Couldn't open this file."
    }
}
