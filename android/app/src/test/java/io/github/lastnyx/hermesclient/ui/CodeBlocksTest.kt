package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeBlocksTest {
    @Test
    fun labelsAndCopyText() {
        assertEquals(listOf("code", "code", "kotlin", "c++", "abcdefghijklmnopqrst"),
            listOf(codeLabel(null), codeLabel("  "), codeLabel(" Kotlin "), codeLabel("C++"), codeLabel("ABCDEFGHIJKLMNOPQRSTUVWXYZ")))
        assertEquals("a\n\n  b", codeForCopy("a\n\n  b\n\n  \n"))
        assertEquals("  x\n\ny", replyForCopy("\n \n  x\n\ny\n\n \n"))
    }
}
