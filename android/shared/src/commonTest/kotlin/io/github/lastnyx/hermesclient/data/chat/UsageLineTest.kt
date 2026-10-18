package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageLineTest {
    private fun line(json: String) = usageLine(JSONObject(json))

    @Test
    fun formatsCountsAndSkipsMissingValues() {
        assertEquals("18.4k in · 612 out", line("""{"input":18400,"output":612}"""))
        assertEquals("1.2k in · 85 out", line("""{"input":1200,"output":85}"""))
        assertEquals("1k in · 999 out", line("""{"input":1000,"output":999}"""))
        assertEquals("999.5k in · 5 out", line("""{"input":999499,"output":5}"""))
        assertEquals("2.5M in · 0 out", line("""{"input":2500000,"output":0}"""))
        assertEquals("1M in · 1 out", line("""{"input":999500,"output":1}"""))
        assertNull(line("""{"input":0,"output":0}"""))
        assertNull(line("{}"))
        assertNull(usageLine(null))
        assertEquals("612 out", line("""{"output":612}"""))
        assertEquals("18.4k in", line("""{"input":18400}"""))
        assertEquals("612 out", line("""{"input":null,"output":612}"""))
        assertEquals("5 out", line("""{"input":"x","output":5}"""))
        assertEquals("5 out", line("""{"input":-3,"output":5}"""))
        assertEquals("1.2k in · 85 out", line("""{"input":1200.0,"output":85.0}"""))
    }
}
