package io.github.lastnyx.hermesclient.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareDraftTest {
    @Test
    fun subjectAboveABareLink() {
        assertEquals("Title\nhttps://a.example/x", draftFromShare("Title", "https://a.example/x"))
    }

    @Test
    fun singleInputsPassThrough() {
        assertEquals("https://a.example", draftFromShare(null, "https://a.example"))
        assertEquals("Only subject", draftFromShare("Only subject", null))
        assertEquals("hello", draftFromShare(null, "hello"))
    }

    @Test
    fun nothingOrBlankIsNull() {
        assertNull(draftFromShare(null, null))
        assertNull(draftFromShare("  ", "\n\t"))
    }

    @Test
    fun subjectEqualToTheLinkIsDropped() {
        assertEquals("https://a.example", draftFromShare("https://a.example", "https://a.example"))
    }

    @Test
    fun proseDropsTheSubject() {
        assertEquals("some words", draftFromShare("Mail", "some words"))
        assertEquals("https://a.example is cool", draftFromShare("Mail", "https://a.example is cool"))
    }

    @Test
    fun uppercaseSchemeAndSurroundingWhitespace() {
        assertEquals("T\nHTTPS://A.EXAMPLE", draftFromShare(" T ", "  HTTPS://A.EXAMPLE\n"))
    }

    @Test
    fun longTextIsCutAndNeverSplitsASurrogatePair() {
        assertEquals(100_000, draftFromShare(null, "a".repeat(150_000))!!.length)
        val cut = draftFromShare(null, "a".repeat(99_999) + "😀")!!
        assertEquals(99_999, cut.length)
    }
}
