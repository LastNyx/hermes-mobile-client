package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import org.junit.Assert.assertEquals
import org.junit.Test

class RewindTest {
    @Test
    fun paramsAndErrorMapping() {
        val p = rewindParams(14, "hi", false)
        assertEquals(setOf("text", "confirm_truncate", "truncate_before_row_id"), p.keys().asSequence().toSet())
        assertEquals(true, p.getBoolean("confirm_truncate"))
        assertEquals(14L, p.getLong("truncate_before_row_id"))
        assertEquals(true, rewindParams(14, "hi", true).getBoolean("confirm_empty_truncate"))

        fun map(code: Int) = rewindResultFor(RpcError(code, "x"))
        assertEquals(RewindResult.Busy, map(4009))
        assertEquals(RewindResult.Stale, map(4018))
        assertEquals(RewindResult.WouldEraseAll, map(4028))
        assertEquals(RewindResult.NoTarget, map(4004))
        assertEquals(RewindResult.Failed, map(4029))
        assertEquals(RewindResult.Failed, map(5000))
    }
}
