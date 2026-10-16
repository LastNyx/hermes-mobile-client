package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.ui.toolDetailPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDetailTest {
    @Test
    fun capsLongToolOutputOnce() {
        val empty = ""
        assertSame(empty, capToolDetail(empty))
        val exact = "a".repeat(4000)
        assertSame(exact, capToolDetail(exact))

        val long = "abcdefghij".repeat(1000)
        val capped = capToolDetail(long)
        assertTrue(capped.length <= 4000)
        val kept = capped.substringBefore("\n... ")
        assertTrue(long.startsWith(kept))
        assertTrue(kept.isNotEmpty())
        assertEquals("\n... ${10000 - kept.length} more characters", capped.removePrefix(kept))
        assertTrue(capped.endsWith("more characters"))

        assertEquals(capped, capToolDetail(capped))
        assertEquals(capped, toolDetailPreview(capped, 4000))

        val emoji = "x".repeat(3959) + "😀".repeat(100)
        val cut = capToolDetail(emoji).substringBefore("\n... ")
        assertFalse(cut.last().isHighSurrogate())

        val small = capToolDetail(long, 100)
        assertTrue(small.length <= 100)
        assertTrue(small.endsWith("more characters"))
    }
}
