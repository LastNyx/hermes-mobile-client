package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.settings.SessionViewStorage
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SessionGroupingTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private fun at(day: Int, h: Int, m: Int): Double =
        ZonedDateTime.of(2024, 3, day, h, m, 0, 0, zone).toEpochSecond().toDouble()

    private fun s(id: String, last: Double, started: Double = 0.0, pinned: Boolean = false) =
        Session(id, id, "", last, pinned = pinned, startedAt = started)

    @Test
    fun groupsByLocalDayOrdersAndPersists() {
        val now = at(20, 12, 0).toLong()
        val sessions = listOf(
            s("today", at(20, 0, 5)),
            s("yest", at(19, 23, 50)),
            s("week", at(16, 9, 0)),
            s("old", at(1, 9, 0)),
            s("pin", at(1, 8, 0), pinned = true),
            s("todayLater", at(20, 11, 0)),
        )
        val groups = groupSessions(sessions, now, SessionGrouping.Date, SessionOrdering.Updated, zone)
        assertEquals(listOf("Pinned", "Today", "Yesterday", "Last week", "Older"), groups.map { it.title })
        assertEquals(listOf("pin"), groups[0].sessions.map { it.id })
        assertEquals(listOf("todayLater", "today"), groups[1].sessions.map { it.id })
        assertEquals(listOf("old"), groups[4].sessions.map { it.id })

        val created = listOf(
            s("a", at(1, 1, 0), started = at(20, 1, 0)),
            s("b", at(20, 1, 0), started = at(1, 1, 0)),
            s("c", at(20, 2, 0), started = at(20, 3, 0)),
        )
        val byCreated = groupSessions(created, now, SessionGrouping.Date, SessionOrdering.Created, zone)
        assertEquals(listOf("Today", "Older"), byCreated.map { it.title })
        assertEquals(listOf("c", "a"), byCreated[0].sessions.map { it.id })

        val fake = object : SessionViewStorage {
            val map = mutableMapOf<String, String>()
            override fun load(name: String) = map[name]
            override fun save(name: String, key: String) { map[name] = key }
        }
        assertEquals(SessionOrdering.Updated, SessionViewStore(fake).ordering())
        SessionViewStore(fake).setOrdering(SessionOrdering.Created)
        assertEquals(SessionOrdering.Created, SessionViewStore(fake).ordering())
        assertEquals(SessionGrouping.Date, SessionViewStore(fake).grouping())
    }
}
