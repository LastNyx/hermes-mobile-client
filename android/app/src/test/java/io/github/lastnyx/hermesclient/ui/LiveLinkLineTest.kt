package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLinkLineTest {
    @Test
    fun textLineShowsOnChatOnly() {
        assertTrue(showsLiveLinkLine(onChat = true))
        assertFalse(showsLiveLinkLine(onChat = false))
    }
}
