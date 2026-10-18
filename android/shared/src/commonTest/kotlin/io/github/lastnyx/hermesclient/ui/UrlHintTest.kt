package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlHintTest {
    @Test
    fun hintForEachKindOfInput() {
        assertNull(urlHint(""))
        assertNull(urlHint("   "))
        assertEquals("Connects to 100.64.0.1:9119", urlHint("http://100.64.0.1:9119"))
        assertEquals("Connects to ::1:9119", urlHint("http://[::1]:9119"))
        assertEquals("Add http:// or https://", urlHint("192.168.0.2"))
        assertEquals("That doesn't look like an address", urlHint("hello world"))
        assertEquals("Connects to example.com", urlHint("https://example.com"))
    }
}
