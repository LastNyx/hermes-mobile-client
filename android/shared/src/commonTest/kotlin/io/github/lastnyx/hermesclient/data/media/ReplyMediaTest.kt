package io.github.lastnyx.hermesclient.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReplyMediaTest {
    private fun img(path: String, name: String) = MediaRef(MediaKind.Image, path, name)

    @Test fun mediaLineIsRemovedAndBecomesImageRef() {
        val r = extractReplyMedia("Here it is\nMEDIA:/a/img.png\nDone")
        assertEquals("Here it is\nDone", r.text)
        assertEquals(listOf(img("/a/img.png", "img.png")), r.refs)
    }

    @Test fun spacedUpperCaseBacktickedAndQuotedForms() {
        assertEquals(listOf(img("/a/b.PNG", "b.PNG")), extractReplyMedia("MEDIA: /a/b.PNG").refs)
        assertEquals(listOf(img("/a/b c.png", "b c.png")), extractReplyMedia("MEDIA:`/a/b c.png`").refs)
        assertEquals(listOf(img("/a/b.jpg", "b.jpg")), extractReplyMedia("MEDIA: \"/a/b.jpg\"").refs)
    }

    @Test fun nonImageAndSvgAreFiles() {
        assertEquals(listOf(MediaRef(MediaKind.File, "/a/report.pdf", "report.pdf")), extractReplyMedia("MEDIA:/a/report.pdf").refs)
        assertEquals(MediaKind.File, extractReplyMedia("MEDIA:/a/x.svg").refs.single().kind)
    }

    @Test fun markdownImageWithPathKeepsSurroundingWords() {
        val r = extractReplyMedia("Look ![shot](/tmp/s.png) here")
        assertEquals("Look  here", r.text)
        assertEquals(listOf(img("/tmp/s.png", "s.png")), r.refs)
    }

    @Test fun fileUrlIsStrippedAndDecoded() {
        assertEquals(listOf(img("/tmp/a b.png", "a b.png")), extractReplyMedia("![x](file:///tmp/a%20b.png)").refs)
    }

    @Test fun httpAndDataTargetsStayUntouched() {
        val text = "![x](https://e.com/a.png) ![y](data:image/png;base64,AAAA)"
        assertSame(text, extractReplyMedia(text).text)
    }

    @Test fun fencedMediaLineIsUntouched() {
        val text = "```\nMEDIA:/a/img.png\n```"
        val r = extractReplyMedia(text)
        assertSame(text, r.text)
        assertEquals(emptyList<MediaRef>(), r.refs)
    }

    @Test fun duplicatePathsAppearOnceInOrder() {
        val r = extractReplyMedia("MEDIA:/a/1.png\nMEDIA:/a/2.png\nMEDIA:/a/1.png")
        assertEquals(listOf("/a/1.png", "/a/2.png"), r.refs.map { it.path })
    }

    @Test fun blankRunsCollapse() {
        assertEquals("a\n\nb", extractReplyMedia("a\n\nMEDIA:/a/1.png\n\nb").text)
    }

    @Test fun halfMediaLineStaysText() {
        val text = "MEDIA:"
        assertSame(text, extractReplyMedia(text).text)
    }

    @Test fun dataUrlDecodes() {
        assertArrayEquals(byteArrayOf(1, 2, 3), parseDataUrl("data:image/png;base64,AQID"))
        assertNull(parseDataUrl("data:image/png,AQID"))
        assertNull(parseDataUrl("data:image/png;base64,@@@"))
        assertArrayEquals(ByteArray(0), parseDataUrl("data:image/png;base64,"))
    }

    @Test fun clamps() {
        assertEquals(1f, clampScale(0.5f), 0f)
        assertEquals(6f, clampScale(9f), 0f)
        assertEquals(0f, clampOffset(50f, 1f, 1000f), 0f)
        assertEquals(1000f, clampOffset(5000f, 3f, 1000f), 0f)
        assertEquals(-1000f, clampOffset(-5000f, 3f, 1000f), 0f)
    }
}
