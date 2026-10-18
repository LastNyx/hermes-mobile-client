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

    @Test
    fun gatewayProjectsDriveTheProjectGroups() {
        fun p(id: String, label: String, ids: List<String>, home: Boolean = false) =
            Project(id, label, ids.size, 0.0, false, home, ids, null)
        val sessions = listOf(
            s("a", 50.0).copy(cwd = "/x/one"),
            s("b", 40.0).copy(cwd = "/y/two"),
            s("h", 30.0).copy(cwd = "/home/me"),
            s("loose", 20.0).copy(cwd = "/z/other"),
            s("pin", 10.0, pinned = true).copy(cwd = "/x/one"),
        )
        val projects = listOf(p("p1", "Repo", listOf("a", "b", "pin")), p("home", "Home", listOf("h"), home = true))
        val groups = groupSessions(
            sessions, 100, SessionGrouping.Project, SessionOrdering.Updated, zone, projects = projects,
        )
        assertEquals(listOf("Pinned", "Repo", "other", "No project"), groups.map { it.title })
        assertEquals(listOf("a", "b"), groups[1].sessions.map { it.id })
        assertEquals(listOf("h"), groups[3].sessions.map { it.id })

        val old = groupSessions(sessions, 100, SessionGrouping.Project, SessionOrdering.Updated, zone)
        val explicitNull = groupSessions(sessions, 100, SessionGrouping.Project, SessionOrdering.Updated, zone, projects = null)
        assertEquals(old, explicitNull)
        assertEquals(listOf("Pinned", "one", "two", "me", "other"), old.map { it.title })
    }
}
