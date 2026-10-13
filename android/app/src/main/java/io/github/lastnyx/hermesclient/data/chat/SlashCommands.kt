package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SlashCommand(val name: String, val description: String)

/** Slash commands the gateway offers, and running one; output lands in the chat as a note. */
class SlashCommands(private val chat: ChatController) {
    private val _commands = MutableStateFlow<List<SlashCommand>>(emptyList())
    val commands: StateFlow<List<SlashCommand>> = _commands

    private val _catalog = MutableStateFlow(SlashCatalog())
    val catalog: StateFlow<SlashCatalog> = _catalog

    suspend fun suggestions(text: String): List<SlashSuggestion> {
        if (text == "/") return _catalog.value.suggestions
        return try {
            val result = chat.sessionCall("complete.slash") { it.put("text", text) }
            val catalog = _catalog.value
            parseSlashCompletions(text, result).filterNot {
                it.kind == SlashKind.Command && catalog.unavailable(it.text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun load() {
        try {
            val result = chat.sessionCall("commands.catalog") { it }
            val parsed = parseSlashCatalog(result)
            _catalog.value = parsed
            if (parsed.warning.isNotEmpty()) chat.addNote(parsed.warning)
            val pairs = result.optJSONArray("pairs") ?: return
            _commands.update {
                (0 until pairs.length()).map {
                    val pair = pairs.getJSONArray(it)
                    SlashCommand(pair.getString(0).removePrefix("/"), pair.optString(1))
                }
            }
        } catch (e: Exception) {
            chat.addNote(e.message ?: "Could not load commands")
        }
    }

    /** [input] is what the user typed, with or without the leading slash. */
    fun filter(input: String): List<SlashCommand> {
        val prefix = input.removePrefix("/").substringBefore(' ').lowercase()
        return _commands.value.filter { it.name.lowercase().startsWith(prefix) }
    }

    /** [command] has no leading slash, e.g. "status" or "goal pause". */
    suspend fun run(command: String) {
        val text = command.removePrefix("/").trim()
        try {
            val result = try {
                chat.sessionCall("slash.exec") { it.put("command", text) }
            } catch (e: RpcError) {
                if (e.code == 4001) throw e
                chat.sessionCall("command.dispatch") {
                    it.put("name", text.substringBefore(' '))
                    val arg = text.substringAfter(' ', "")
                    if (arg.isNotEmpty()) it.put("arg", arg)
                    it
                }
            }
            val shown = result.optString("output").ifEmpty { result.optString("warning") }.ifEmpty { result.optString("notice") }
            chat.addNote(shown.ifEmpty { "/$text done" })
        } catch (e: Exception) {
            chat.addNote(e.message ?: "Command failed")
        }
    }
}
