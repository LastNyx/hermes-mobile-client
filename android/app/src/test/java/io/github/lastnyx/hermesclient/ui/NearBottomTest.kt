package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearBottomTest {
    @Test
    fun nearBottomRule() {
        val line = 48
        // empty list
        assertTrue(isNearBottom(0, null, 0, 1000, line))
        // last item's end exactly at viewport end
        assertTrue(isNearBottom(5, 4, 1000, 1000, line))
        // one line of the last item still below the viewport
        assertTrue(isNearBottom(5, 4, 1048, 1000, line))
        // far below the viewport end
        assertFalse(isNearBottom(5, 4, 1500, 1000, line))
        // last visible item is not the last one
        assertFalse(isNearBottom(5, 3, 900, 1000, line))
    }
}
