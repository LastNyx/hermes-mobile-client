package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.AttachmentKind
import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachTrayTest {
    private fun file(name: String) = PreparedAttachment(name, AttachmentKind.File, ByteArray(0), "text/plain")
    private fun names(t: AttachTrayState) = t.items.map { it.name }
    private fun files(vararg n: String) = n.map(::file)
    private val limit = "Up to 6 attachments per message."

    @Test
    fun addKeepsOrderUnderTheLimit() {
        val (t, msg) = AttachTrayState().add(files("a", "b")).first.add(files("c")).let { it.first to it.second }
        assertEquals(listOf("a", "b", "c"), names(t))
        assertNull(msg)
    }

    @Test
    fun addKeepsOnlyWhatFits() {
        val full4 = AttachTrayState().add(files("1", "2", "3", "4")).first
        val (t, msg) = full4.add(files("a", "b", "c"))
        assertEquals(listOf("1", "2", "3", "4", "a", "b"), names(t))
        assertEquals(limit, msg)
        val (t2, msg2) = t.add(files("x"))
        assertEquals(6, t2.items.size)
        assertEquals(limit, msg2)
    }

    @Test
    fun removeAndClear() {
        val t = AttachTrayState(files("a", "b", "c"))
        assertEquals(listOf("a", "c"), names(t.remove(1)))
        assertEquals(listOf("a", "b"), names(t.remove(2)))
        assertEquals(listOf("a", "b", "c"), names(t.remove(3)))
        assertEquals(listOf("a", "b", "c"), names(t.remove(-1)))
        assertTrue(t.clear().isEmpty)
        assertFalse(t.isEmpty)
    }

    @Test
    fun chipLabelCutsLongNames() {
        assertEquals("a.pdf", chipLabel(file("a.pdf")))
        val n24 = "a".repeat(24)
        assertEquals(n24, chipLabel(file(n24)))
        assertEquals("IMG_2026-10-15_very_long_name.jpg".take(23) + "…", chipLabel(file("IMG_2026-10-15_very_long_name.jpg")))
    }

    @Test
    fun canSendRules() {
        val empty = AttachTrayState()
        val one = AttachTrayState(files("a"))
        assertFalse(canSend(" ", empty, false))
        assertTrue(canSend(" ", one, false))
        assertFalse(canSend(" ", one, true))
        assertTrue(canSend("hi", one, true))
        assertTrue(canSend("hi", empty, false))
    }
}
