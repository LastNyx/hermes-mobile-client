package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.settings.SessionViewStorage
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class SessionStatusTest {
    private val zone = ZoneId.of("UTC")

    private fun s(
        id: String,
        last: Double,
        active: Boolean = false,
        unread: Boolean = false,
        count: Int = 3,
        cwd: String? = null,
        pinned: Boolean = false,
    ) = Session(id, id, "", last, pinned = pinned, active = active, unread = unread, messageCount = count, cwd = cwd)

    @Test
    fun statusGroupingFilterProjectAndStorage() {
        assertEquals(SessionStatus.Working, sessionStatus(s("a", 1.0, active = true, unread = true)))
        assertEquals(SessionStatus.Unread, sessionStatus(s("b", 1.0, unread = true)))
        assertEquals(SessionStatus.Draft, sessionStatus(s("c", 1.0, count = 0)))
        assertEquals(SessionStatus.Idle, sessionStatus(s("d", 1.0)))

        val sessions = listOf(
            s("idle", 50.0),
            s("draft", 40.0, count = 0),
            s("unread", 30.0, unread = true),
            s("work", 20.0, active = true),
            s("pin", 10.0, pinned = true),
        )
        val byStatus = groupSessions(sessions, 100, SessionGrouping.Status, SessionOrdering.Updated, zone)
        assertEquals(listOf("Pinned", "Working", "Unread", "Draft", "Idle"), byStatus.map { it.title })

        val filtered = groupSessions(
            sessions, 100, SessionGrouping.Status, SessionOrdering.Updated, zone,
            statuses = setOf(SessionStatus.Unread, SessionStatus.Idle),
        )
        assertEquals(listOf("Pinned", "Unread", "Idle"), filtered.map { it.title })

        val projects = listOf(
            s("none", 90.0),
            s("x1", 10.0, cwd = "/home/me/alpha"),
            s("y1", 80.0, cwd = "/home/me/beta/"),
            s("x2", 70.0, cwd = "/srv/alpha"),
            s("pin", 5.0, cwd = "/home/me/alpha", pinned = true),
        )
        val byProject = groupSessions(projects, 100, SessionGrouping.Project, SessionOrdering.Updated, zone)
        assertEquals(listOf("Pinned", "beta", "alpha", "No project"), byProject.map { it.title })
        assertEquals(listOf("y1"), byProject[1].sessions.map { it.id })
        assertEquals(listOf("x2", "x1"), byProject[2].sessions.map { it.id })

        val fake = object : SessionViewStorage {
            val map = mutableMapOf<String, String>()
            override fun load(name: String) = map[name]
            override fun save(name: String, key: String) { map[name] = key }
        }
        assertEquals(emptySet<SessionStatus>(), SessionViewStore(fake).statusFilter())
        SessionViewStore(fake).setStatusFilter(setOf(SessionStatus.Working, SessionStatus.Draft))
        assertEquals(setOf(SessionStatus.Working, SessionStatus.Draft), SessionViewStore(fake).statusFilter())
    }
}
