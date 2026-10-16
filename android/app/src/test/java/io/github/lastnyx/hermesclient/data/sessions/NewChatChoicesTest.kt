package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.live.RpcError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class NewChatChoicesTest {
    private fun project(label: String, path: String?, last: Double = 1.0, none: Boolean = false) =
        Project("id-$label", label, 1, last, false, none, emptyList(), path)

    @Test
    fun nullOrNoUsableProjectGivesNoMenu() {
        assertEquals(emptyList<NewChatChoice>(), newChatChoices(null))
        assertEquals(emptyList<NewChatChoice>(), newChatChoices(listOf(project("None", null, none = true))))
    }

    @Test
    fun noProjectFirstThenNewestFirstAndUnusableLeftOut() {
        val choices = newChatChoices(
            listOf(
                project("old", "/a", 1.0),
                project("none", "/n", 9.0, none = true),
                project("nofolder", null, 8.0),
                project("blankfolder", " ", 8.0),
                project("", "/b", 8.0).copy(label = " "),
                project("new", "/c", 5.0),
            ),
        )
        assertEquals(
            listOf(NewChatChoice("No project", null), NewChatChoice("new", "/c"), NewChatChoice("old", "/a")),
            choices,
        )
    }

    @Test
    fun createErrorsBecomeOurOwnSentences() {
        assertEquals("That folder already belongs to a project.", projectCreateMessage(RpcError(5063, "folder already belongs to project x")))
        assertEquals("This gateway can't create projects.", projectCreateMessage(RpcError(-32601, "no")))
        assertEquals(CANT_REACH_GATEWAY, projectCreateMessage(IOException("Offline")))
        val other = projectCreateMessage(RpcError(5061, "secret path /home/x"))
        assertEquals("Could not create the project.", other)
        assertFalse(other.contains("secret"))
    }
}
