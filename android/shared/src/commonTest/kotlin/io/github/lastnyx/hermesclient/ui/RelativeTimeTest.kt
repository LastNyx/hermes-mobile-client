package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val nowMs = 1_000_000_000_000L

    @Test
    fun formatsAgeInTheLargestUnit() {
        assertEquals("", relativeTime(0.0, nowMs))
        assertEquals("now", relativeTime(1_000_000_000.0 - 30, nowMs))
        assertEquals("5 min ago", relativeTime(1_000_000_000.0 - 300, nowMs))
        assertEquals("2 h ago", relativeTime(1_000_000_000.0 - 7200, nowMs))
        assertEquals("3 d ago", relativeTime(1_000_000_000.0 - 3 * 86400, nowMs))
    }
}
