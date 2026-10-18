package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayClipTest {
    @Test
    fun shortEmptyAndExactTextsComeBackAsTheSameInstance() {
        val exact = "a".repeat(DISPLAY_MAX_CHARS - 1).chunked(100).joinToString("\n").take(DISPLAY_MAX_CHARS).padEnd(DISPLAY_MAX_CHARS, 'b')
        for (t in listOf("hello\nworld", "", exact)) assertSame(t, clipForDisplay(t))
    }

    @Test
    fun aLongLineIsCutAndNeighboursStay() {
        val out = clipForDisplay("before\n" + "x".repeat(5000) + "\nafter")
        assertEquals("before\n" + "x".repeat(1000) + " ... 4000 more characters\nafter", out)
    }

    @Test
    fun manyShortLinesAreCutToMaxCharsWithAFinalMarker() {
        val text = "ab\n".repeat(10_000)
        val out = clipForDisplay(text)
        val removed = text.length - DISPLAY_MAX_CHARS
        assertEquals(text.take(DISPLAY_MAX_CHARS) + "\n... $removed more characters", out)
    }

    @Test
    fun lineRuleRunsBeforeTotalRule() {
        val out = clipForDisplay("x".repeat(30) + "\n" + "y".repeat(30) + "\n" + "z".repeat(30), 50, 10)
        val line = "x".repeat(10) + " ... 20 more characters"
        val joined = line + "\n" + "y".repeat(10) + " ... 20 more characters\n" + "z".repeat(10) + " ... 20 more characters"
        assertEquals(joined.take(50) + "\n... ${joined.length - 50} more characters", out)
        assertTrue(out.startsWith(line))
        assertTrue(out.length <= 50 + "\n... 999 more characters".length)
    }

    @Test
    fun aCutInsideAnEmojiLeavesNoLoneSurrogate() {
        val out = clipForDisplay("a" + "😀".repeat(10), maxChars = 4, maxLine = 100)
        assertEquals("a😀\n... 18 more characters", out)
        assertFalse(Character.isHighSurrogate(out.substringBefore("\n...").last()))
    }

    @Test
    fun carriageReturnsOfUntouchedLinesSurvive() {
        val out = clipForDisplay("one\r\n" + "x".repeat(20) + "\r\nthree\r\n", 1000, 10)
        assertEquals("one\r\n" + "x".repeat(10) + " ... 11 more characters\nthree\r\n", out)
    }

    @Test
    fun clippingTwiceChangesNothing() {
        for (x in listOf("x".repeat(30_000), "x".repeat(99).plus("\n").repeat(300))) {
            val once = clipForDisplay(x)
            assertEquals(once, clipForDisplay(once))
        }
    }
}
