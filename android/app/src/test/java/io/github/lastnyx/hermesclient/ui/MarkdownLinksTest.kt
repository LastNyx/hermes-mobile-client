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

    @Test
    fun gatewayFilePathsAreExtractedAndEverythingElseIsNull() {
        val paths = mapOf(
            "file:///home/me/a.txt" to "/home/me/a.txt",
            "file:///C:/x/y.txt" to "C:/x/y.txt",
            "file:///home/my%20dir/a.txt" to "/home/my dir/a.txt",
            "file://localhost/a/b" to "/a/b",
            "/home/me/a.txt" to "/home/me/a.txt",
            "C:\\Users\\me\\a.txt" to "C:\\Users\\me\\a.txt",
        )
        assertEquals(paths.values.toList(), paths.keys.map { gatewayFilePath(it) })
        val none = listOf(
            "https://a.example/x", "mailto:a@b.example", "content://x/y", "javascript:alert(1)", "intent://x#Intent;end",
            "data:text/html,hi", "relative.html", "//share/x", "/a/../etc/passwd", "file://evil.example/a", "/a/", "",
            "/a\u0000b", "/a\nb",
        )
        assertEquals(none.map { null }, none.map { gatewayFilePath(it) })
    }
}
