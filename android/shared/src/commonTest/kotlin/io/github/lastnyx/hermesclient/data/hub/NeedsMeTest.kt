package io.github.lastnyx.hermesclient.data.hub

import io.github.lastnyx.hermesclient.data.gateway.Gateway
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NeedsMeTest {
    private fun row(id: String, status: String, title: String?, at: Long?, key: String? = null) =
        JSONObject().put("id", id).put("status", status).apply {
            if (title != null) put("title", title)
            if (at != null) put("last_active", at)
            if (key != null) put("session_key", key)
        }

    @Test
    fun onlyWaitingMergedOldestFirst() {
        val laptop = Gateway("g1", "Laptop", "http://laptop")
        val work = Gateway("g2", "WorkLab", "http://work")
        val items = needsMe(
            mapOf(
                laptop to JSONArray()
                    .put(row("a", "waiting", "Deploy", 300, key = "stored-a"))
                    .put(row("b", "working", "Busy", 100))
                    .put(row("c", "waiting", null, null)),
                work to JSONArray().put(row("d", "waiting", "Backup", 200)),
            ),
        )
        assertEquals(
            listOf(
                NeedsMeItem(work, "d", "Backup", 200L),
                NeedsMeItem(laptop, "stored-a", "Deploy", 300L),
                NeedsMeItem(laptop, "c", null, null),
            ),
            items,
        )
        assertEquals(emptyList<NeedsMeItem>(), needsMe(emptyMap()))
    }
}
