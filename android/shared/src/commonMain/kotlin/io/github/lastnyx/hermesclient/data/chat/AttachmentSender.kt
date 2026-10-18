package io.github.lastnyx.hermesclient.data.chat

import java.util.Base64
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

sealed interface UploadResult {
    class Ok(val imagePaths: List<String>, val refTexts: List<String>) : UploadResult
    class Failed(val message: String) : UploadResult
}

class AttachmentSender(
    private val call: suspend (method: String, timeoutMs: Long, build: (JSONObject) -> JSONObject) -> JSONObject,
) {
    suspend fun upload(attachments: List<PreparedAttachment>): UploadResult {
        val paths = mutableListOf<String>()
        val refs = mutableListOf<String>()
        for (a in attachments) {
            try {
                val b64 = Base64.getEncoder().encodeToString(a.bytes)
                if (a.kind == AttachmentKind.Image) {
                    val reply = call("image.attach_bytes", AttachLimits.IMAGE_ATTACH_TIMEOUT_MS) {
                        it.put("content_base64", b64).put("filename", a.name)
                    }
                    if (!reply.isNull("path")) paths += reply.getString("path")
                } else {
                    val reply = call("file.attach", AttachLimits.FILE_ATTACH_TIMEOUT_MS) {
                        it.put("name", a.name).put("data_url", "data:${a.mime.ifBlank { "application/octet-stream" }};base64,$b64")
                    }
                    if (reply.isNull("ref_text")) throw IllegalStateException("no ref_text")
                    refs += reply.getString("ref_text")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detach(paths)
                return UploadResult.Failed(attachErrorText(a.name, e))
            }
        }
        return UploadResult.Ok(paths, refs)
    }

    suspend fun detach(paths: List<String>) {
        for (p in paths) {
            try {
                call("image.detach", AttachLimits.IMAGE_DETACH_TIMEOUT_MS) { it.put("path", p) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: a stale upload costs the gateway nothing we can fix.
            }
        }
    }
}
