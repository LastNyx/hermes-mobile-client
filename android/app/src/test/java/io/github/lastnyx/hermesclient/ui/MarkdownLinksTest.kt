package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLinksTest {
    @Test
    fun onlyWebAndMailLinksAreAllowed() {
        val allowed = listOf("http://a.example/x", "https://a.example", "mailto:a@b.example", "HTTPS://A.EXAMPLE", "  MailTo:a@b.example  ")
        val denied = listOf(
            "javascript:alert(1)", "intent://x#Intent;end", "file:///etc/passwd", "content://x/y", "data:text/html,hi",
            "tel:123", "/relative/path", "relative.html", "", "   ", "https://a.example/\u0000x", "https://a.example/\nx",
        )
        assertEquals(allowed.map { true }, allowed.map { isAllowedLink(it) })
        assertEquals(denied.map { false }, denied.map { isAllowedLink(it) })
    }
}
