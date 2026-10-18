package io.github.lastnyx.hermesclient.data.sessions

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectsParseTest {
    private fun parse(json: String) = parseProjects(JSONObject(json))

    @Test
    fun readsAFullRow() {
        val p = parse(
            """{"projects":[{"id":"p1","label":"repo","path":"/src/repo","isAuto":true,"isNoProject":false,
            "sessionCount":2,"lastActive":12.5,"sessionIds":["a","b"],"totalTokens":9}]}""",
        ).single()
        assertEquals(Project("p1", "repo", 2, 12.5, true, false, listOf("a", "b"), "/src/repo"), p)
    }

    @Test
    fun homeRowHasNoPath() {
        val p = parse("""{"projects":[{"id":"home","label":"Home","path":null,"isNoProject":true}]}""").single()
        assertNull(p.primaryPath)
        assertEquals(true, p.isNoProject)
    }

    @Test
    fun skipsRowsWithoutIdAndFallsBackToIdForLabel() {
        val list = parse("""{"projects":[{"label":"x"},{"id":" "},{"id":"p2"}]}""")
        assertEquals(listOf("p2"), list.map { it.id })
        assertEquals("p2", list.single().label)
    }

    @Test
    fun jsonNullsDoNotBecomeText() {
        val p = parse("""{"projects":[{"id":"p3","label":null,"path":null,"lastActive":null}]}""").single()
        assertEquals("p3", p.label)
        assertNull(p.primaryPath)
        assertEquals(0.0, p.lastActive, 0.0)
    }

    @Test
    fun dropsNonStringSessionIds() {
        val p = parse("""{"projects":[{"id":"p4","sessionIds":["a",null,5,"","b"]}]}""").single()
        assertEquals(listOf("a", "b"), p.sessionIds)
    }

    @Test
    fun emptyInputsGiveEmptyList() {
        assertEquals(emptyList<Project>(), parseProjects(null))
        assertEquals(emptyList<Project>(), parse("{}"))
    }
}
