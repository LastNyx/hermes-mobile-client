package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {
    private val mb = 1024L * 1024
    private fun image(name: String = "a.jpg") = PreparedAttachment(name, AttachmentKind.Image, byteArrayOf(1), "image/jpeg")
    private fun file(name: String = "doc.pdf") = PreparedAttachment(name, AttachmentKind.File, byteArrayOf(2), "application/pdf")

    @Test
    fun sizesAndSampling() {
        assertEquals(2048 to 1536, scaledSize(4000, 3000))
        assertEquals(1536 to 2048, scaledSize(3000, 4000))
        assertEquals(41 to 2048, scaledSize(100, 5000))
        assertEquals(800 to 600, scaledSize(800, 600))
        assertEquals(0 to 0, scaledSize(0, 0))
        assertEquals(1, sampleSizeFor(4000, 3000, 2048))
        assertEquals(2, sampleSizeFor(8000, 6000, 2048))
        assertEquals(1, sampleSizeFor(1000, 500, 2048))
        assertEquals(1, sampleSizeFor(0, 0, 2048))
        assertEquals(8, sampleSizeFor(16000, 100, 1080))
    }

    @Test
    fun namesKindsAndLimits() {
        assertEquals("IMG 1.jpg", jpegName("IMG 1.png"))
        assertEquals("pic.jpg", jpegName("pic"))
        assertEquals("image.jpg", jpegName(" "))
        assertEquals(AttachmentKind.Image, kindFor("image/webp", "x"))
        assertEquals(AttachmentKind.Image, kindFor("application/octet-stream", "X.HEIC"))
        assertEquals(AttachmentKind.Image, kindFor(null, "x.png"))
        assertEquals(AttachmentKind.File, kindFor("image/svg+xml", "x.svg"))
        assertEquals(AttachmentKind.File, kindFor("application/zip", "x.png"))
        assertEquals("a.png is larger than 25 MB.", checkSize("a.png", AttachmentKind.Image, 25 * mb + 1))
        assertNull(checkSize("a.png", AttachmentKind.Image, 25 * mb))
        assertEquals("d.pdf is larger than 10 MB.", checkSize("d.pdf", AttachmentKind.File, 11 * mb))
        assertNull(checkSize("d.pdf", AttachmentKind.File, 10 * mb))
        assertNull(checkSize("a.png", AttachmentKind.Image, 11 * mb))
        assertEquals(PickDecision(3, null), acceptPick(0, 3))
        assertEquals(PickDecision(2, "Up to 6 attachments per message."), acceptPick(4, 3))
        assertEquals(PickDecision(0, "Up to 6 attachments per message."), acceptPick(6, 1))
    }

    @Test
    fun promptAndBubbleText() {
        assertEquals("@file:a\n@file:b\n\nlook", buildPrompt(" look ", listOf("@file:a ", "@file:b"), 0))
        assertEquals("What do you see in this image?", buildPrompt("", emptyList(), 1))
        assertEquals("@file:a", buildPrompt("", listOf("@file:a"), 0))
        assertEquals("@file:a", buildPrompt("", listOf("@file:a"), 1))
        assertEquals("look", buildPrompt("look", emptyList(), 1))
        assertEquals("@file:a\n\nhi", buildPrompt("hi", listOf(" ", "@file:a", ""), 0))
        assertEquals("hi\n[image]\n📎 doc.pdf", bubbleText(" hi ", listOf(image(), file())))
        assertEquals("[image]", bubbleText("", listOf(image())))
    }

    @Test
    fun errorSentences() {
        assertEquals(CANT_REACH_GATEWAY, attachErrorText("a", IOException("secret")))
        assertEquals("a is too large for the gateway.", attachErrorText("a", RpcError(4018, "secret")))
        assertEquals("Couldn't attach a.", attachErrorText("a", RpcError(5027, "secret")))
    }

    @Test
    fun senderUploadsInOrderAndDetachesOnFailure() = runBlocking {
        val log = mutableListOf<Triple<String, Long, JSONObject>>()
        val sender = AttachmentSender { method, timeout, build ->
            val params = build(JSONObject().put("session_id", "s"))
            log += Triple(method, timeout, params)
            when {
                method == "image.attach_bytes" && params.getString("filename") == "a.jpg" -> JSONObject().put("path", "/p/a")
                method == "image.attach_bytes" -> throw RpcError(5027, "boom")
                else -> JSONObject()
            }
        }
        val failed = sender.upload(listOf(image("a.jpg"), image("b.jpg"), file()))
        assertEquals("Couldn't attach b.jpg.", (failed as UploadResult.Failed).message)
        assertEquals(listOf("image.attach_bytes", "image.attach_bytes", "image.detach"), log.map { it.first })
        assertEquals("/p/a", log[2].third.getString("path"))
        assertEquals(setOf("session_id", "content_base64", "filename"), log[0].third.keys().asSequence().toSet())
        assertEquals(90_000L, log[0].second)
    }

    @Test
    fun senderFileUploadReadsRefText() = runBlocking {
        var seen: Triple<String, Long, JSONObject>? = null
        val ok = AttachmentSender { method, timeout, build ->
            val p = build(JSONObject().put("session_id", "s"))
            seen = Triple(method, timeout, p)
            JSONObject().put("ref_text", "@file:doc")
        }.upload(listOf(file()))
        assertEquals(listOf("@file:doc"), (ok as UploadResult.Ok).refTexts)
        assertEquals(120_000L, seen!!.second)
        assertEquals(setOf("session_id", "name", "data_url"), seen!!.third.keys().asSequence().toSet())
        assertTrue(seen!!.third.getString("data_url").startsWith("data:application/pdf;base64,"))

        val nullRef = AttachmentSender { _, _, _ -> JSONObject("""{"ref_text":null}""") }.upload(listOf(file()))
        assertFalse(nullRef is UploadResult.Ok)
        assertEquals("Couldn't attach doc.pdf.", (nullRef as UploadResult.Failed).message)
    }
}
