package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHeaderTest {
    @Test
    fun phoneLayoutStacksAndWideLayoutDoesNot() {
        assertTrue(headerStacked(true))
        assertFalse(headerStacked(false))
    }
}
