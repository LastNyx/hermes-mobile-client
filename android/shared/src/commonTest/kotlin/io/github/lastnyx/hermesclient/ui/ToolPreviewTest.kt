package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPreviewTest {
    @Test
    fun cutsOnlyWhenOverTheLimit() {
        assertEquals("abcde", toolDetailPreview("abcde", 5))
        assertEquals("", toolDetailPreview("", 5))
        assertEquals("abc\n... 4 more characters", toolDetailPreview("abcdefg", 3))
    }
}
