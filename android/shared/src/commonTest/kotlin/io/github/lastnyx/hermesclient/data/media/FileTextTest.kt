package io.github.lastnyx.hermesclient.data.media

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTextTest {
    @Test fun parsesFullBodyAndHandlesNullsAndMissingKeys() {
        val full = parseFileText(JSONObject("""{"text":"hi","byteSize":5000000000,"truncated":true,"binary":false,"language":"kotlin","path":"/a"}"""))
        assertEquals(FileText("hi", 5000000000L, true, false, "kotlin"), full)
        val nulls = parseFileText(JSONObject("""{"text":null,"language":null,"byteSize":3}"""))
        assertNull(nulls.language)
        assertEquals("", nulls.text)
        assertEquals(FileText("", 0, false, false, null), parseFileText(JSONObject("{}")))
    }

    @Test fun numbersLines() {
        assertEquals(listOf("1 a", "2 b", "3 c"), numberLines("a\nb\nc"))
        val twelve = numberLines(('a'..'l').joinToString("\n"))
        assertEquals(" 1 a", twelve.first())
        assertEquals("12 l", twelve.last())
        assertEquals(listOf("1 a", "2 b"), numberLines("a\r\nb"))
        assertEquals(listOf("1 a"), numberLines("a\n"))
        assertTrue(numberLines("").isEmpty())
    }

    @Test fun noticesAndSizes() {
        assertEquals("This file isn't text. Save it to open it elsewhere.", viewerNotice(FileText("", 1, false, true, null)))
        assertEquals("Showing the first 512 KB of 1.5 MB.", viewerNotice(FileText("x", 1572864, true, false, null)))
        assertNull(viewerNotice(FileText("x", 1, false, false, null)))
        assertFalse(sizeText(0).isEmpty())
        assertEquals("0 B", sizeText(0))
        assertEquals("1023 B", sizeText(1023))
        assertEquals("1.5 KB", sizeText(1536))
        assertEquals("2.0 MB", sizeText(2L * 1024 * 1024))
    }
}
