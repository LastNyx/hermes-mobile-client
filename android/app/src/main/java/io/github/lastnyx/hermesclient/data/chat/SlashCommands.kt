package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.IOException

private const val MAX_ALIAS_HOPS = 5

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            chat.addNote("Can't reach the gateway. Check your connection.")
        } catch (e: Exception) {
            chat.addNote("Couldn't load the command list.")
        }
    }

    /** [input] is what the user typed, with or without the leading slash. */
    fun filter(input: String): List<SlashCommand> {
        val prefix = input.removePrefix("/").substringBefore(' ').lowercase()
        return _commands.value.filter { it.name.lowercase().startsWith(prefix) }
    }

    /** [command] has no leading slash, e.g. "status" or "goal pause". */
    suspend fun run(command: String) {
        runLine("/" + command.removePrefix("/"))
    }

    /** Runs a typed slash line. Returns composer text for `prefill`; null for no text or a non-command line. */
    suspend fun runLine(text: String): String? {
        var line = parseSlashLine(text) ?: return null
        var hops = 0
        while (true) {
            val name = line.name
            if (_catalog.value.unavailable(name)) {
                chat.addCommandOutput("/$name", "/$name isn't available in this app.", true)
                return null
            }
            val result = try {
                exec(line)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chat.addCommandOutput("/$name", slashErrorText(e, name), true)
                return null
            }
            when (val parsed = parseSlashResult(result)) {
                null -> {
                    chat.addCommandOutput("/$name", "/$name didn't work. Try again.", true)
                    return null
                }
                is SlashResult.Output -> {
                    chat.addCommandOutput("/$name", if (parsed.warning.isEmpty()) parsed.text else parsed.warning + "\n\n" + parsed.text, false)
                    return null
                }
                is SlashResult.Alias -> {
                    if (++hops > MAX_ALIAS_HOPS) {
                        chat.addCommandOutput("/$name", "/$name loops back on itself.", true)
                        return null
                    }
                    line = SlashLine(parsed.target.removePrefix("/").lowercase(), line.arg)
                }
                is SlashResult.Send -> {
                    if (chat.state.value.running) {
                        chat.addCommandOutput("/$name", "A reply is running. Stop it or wait, then run /$name again.", true)
                        return null
                    }
                    parsed.notice?.let { chat.addNote(it) }
                    chat.sendWithDisplay(parsed.message, parsed.display ?: ("/" + line.name + if (line.arg.isEmpty()) "" else " " + line.arg))
                    return null
                }
                is SlashResult.Prefill -> {
                    try {
                        chat.reloadHistory()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        chat.addCommandOutput("/$name", slashErrorText(e, name), true)
                    }
                    parsed.notice?.let { chat.addNote(it) }
                    return parsed.message
                }
            }
        }
    }

    private suspend fun exec(line: SlashLine): JSONObject {
        val whole = if (line.arg.isEmpty()) line.name else line.name + " " + line.arg
        return try {
            chat.sessionCall("slash.exec") { it.put("command", whole) }
        } catch (first: RpcError) {
            if (first.code == 4001) throw first
            try {
                chat.sessionCall("command.dispatch") {
                    it.put("name", line.name)
                    if (line.arg.isNotEmpty()) it.put("arg", line.arg)
                    it
                }
            } catch (second: RpcError) {
                if (second.code == 4018 && second.message.orEmpty().startsWith("not a ")) throw first
                throw second
            }
        }
    }
}
