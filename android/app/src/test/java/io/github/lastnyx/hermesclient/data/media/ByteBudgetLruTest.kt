package io.github.lastnyx.hermesclient.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByteBudgetLruTest {
    private fun lru() = ByteBudgetLru<String, ByteArray>(10) { it.size.toLong() }

    @Test fun evictsLeastRecentlyUsedFirst() {
        val c = lru()
        c.put("a", ByteArray(4))
        c.put("b", ByteArray(4))
        c.put("c", ByteArray(4))
        assertNull(c.get("a"))
        assertEquals(8L, c.bytes)
        assertEquals(2, c.size)
    }

    @Test fun getRefreshesRecency() {
        val c = lru()
        c.put("a", ByteArray(4))
        c.put("b", ByteArray(4))
        c.get("a")
        c.put("c", ByteArray(4))
        assertNull(c.get("b"))
        assertEquals(4, c.get("a")!!.size)
    }

    @Test fun oversizedValueIsNotStored() {
        val c = lru()
        c.put("big", ByteArray(11))
        assertEquals(0, c.size)
        assertEquals(0L, c.bytes)
    }

    @Test fun replacingKeyAdjustsBytes() {
        val c = lru()
        c.put("a", ByteArray(4))
        c.put("a", ByteArray(6))
        assertEquals(6L, c.bytes)
        assertEquals(1, c.size)
    }
}
