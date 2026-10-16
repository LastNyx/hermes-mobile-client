package io.github.lastnyx.hermesclient.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DraftStoreTest {
    private class Fake : DraftStorage {
        val map = mutableMapOf<String, String>()
        var clears = 0
        override fun load(key: String) = map[key]
        override fun save(key: String, text: String) { map[key] = text }
        override fun clear(key: String) { clears++; map.remove(key) }
    }

    @Test
    fun draftKeysNeverCollide() {
        assertEquals(draftKey("g1", "s1"), draftKey("g1", "s1"))
        assertNotEquals(draftKey("g1", "s1"), draftKey("g1", "s2"))
        assertNotEquals(draftKey("g1", "s1"), draftKey("g2", "s1"))
        assertNotEquals(draftKey("g1", null), draftKey("g1", "s1"))
        assertNotEquals(draftKey("g1", null), draftKey("g2", null))
    }

    @Test
    fun storeKeepsDraftsPerKeyAndDropsBlankOnes() {
        val fake = Fake()
        val store = DraftStore(fake)
        assertEquals("", store.get("a"))
        store.set("a", "  hello ")
        store.set("b", "other")
        assertEquals("  hello ", store.get("a"))
        assertEquals("other", store.get("b"))
        assertEquals("  hello ", DraftStore(fake).get("a"))
        store.set("a", "")
        assertEquals("", store.get("a"))
        store.set("b", "   ")
        assertEquals("", store.get("b"))
        assertEquals(2, fake.clears)
    }
}
