package io.github.lastnyx.hermesclient.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class EventGapTest {
    @Test
    fun onlyAJumpPastTheNextSeqIsAGap() {
        assertEquals(false, seqGap(null, 5))
        assertEquals(false, seqGap(2, 3))
        assertEquals(true, seqGap(2, 4))
        assertEquals(false, seqGap(2, 2))
        assertEquals(false, seqGap(2, 1))
        assertEquals(false, seqGap(2, null))
        assertEquals(false, seqGap(null, null))
    }
}
