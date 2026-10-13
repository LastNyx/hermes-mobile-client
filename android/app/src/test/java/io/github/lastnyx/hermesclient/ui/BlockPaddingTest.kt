package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockPaddingTest {
    @Test
    fun reasoningIs10ToolDetailIs8AndToolHeaderStays6() {
        assertEquals(10, REASONING_VERTICAL_PADDING)
        assertEquals(8, TOOL_DETAIL_VERTICAL_PADDING)
        assertEquals(6, TOOL_HEADER_VERTICAL_PADDING)
    }
}
