package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.SlashKind
import io.github.lastnyx.hermesclient.data.chat.SlashSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class SlashSuggestionsTest {
    private fun s(text: String, group: String?) = SlashSuggestion(text, text, "d", SlashKind.Command, group)

    @Test
    fun suggestionsAreAskedForOnlyBySlashCommandDrafts() {
        for (hidden in listOf("hello", "/usr/local", "/re\nmore", "")) {
            assertEquals(hidden, false, asksForSuggestions(hidden))
        }
        for (shown in listOf("/", "/re", "/personality ")) {
            assertEquals(shown, true, asksForSuggestions(shown))
        }
    }

    @Test
    fun headerBeforeEachNewGroupAndNoneForNullGroups() {
        val a = s("/a", "Session")
        val b = s("/b", "Session")
        val c = s("/c", "Tools")
        val d = s("/d", null)
        assertEquals(
            listOf(
                SuggestionRow.Header("Session"), SuggestionRow.Item(a), SuggestionRow.Item(b),
                SuggestionRow.Header("Tools"), SuggestionRow.Item(c), SuggestionRow.Item(d),
            ),
            suggestionRows(listOf(a, b, c, d)),
        )
        assertEquals(listOf(SuggestionRow.Item(d)), suggestionRows(listOf(d)))
    }
}
