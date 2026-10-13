package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.live.LinkState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineLineTest {
    @Test
    fun neutralOfflineLineOnlyForOfflineLinkState() {
        assertTrue(showsOfflineLine(LinkState.Offline))
        assertFalse(showsOfflineLine(LinkState.Connecting))
    }
}
