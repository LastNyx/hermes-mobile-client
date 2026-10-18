package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockPaddingTest {
    @Test
    fun reasoningIs6ToolDetailIs4AndToolHeaderIs4() {
        assertEquals(6, REASONING_VERTICAL_PADDING)
        assertEquals(4, TOOL_DETAIL_VERTICAL_PADDING)
        assertEquals(4, TOOL_HEADER_VERTICAL_PADDING)
    }
}
