package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import org.json.JSONObject
import java.io.IOException

data class SlashLine(val name: String, val arg: String)

fun parseSlashLine(text: String): SlashLine? {
    val line = text.trimEnd()
    if (!line.startsWith("/")) return null
    val body = line.substring(1)
    val name = body.takeWhile { !it.isWhitespace() && it != '/' }
    if (name.isEmpty()) return null
    val rest = body.substring(name.length)
    if (rest.isNotEmpty() && !rest[0].isWhitespace()) return null
    return SlashLine(name.lowercase(), rest.trim())
}

sealed interface SlashResult {
    data class Output(val text: String, val warning: String) : SlashResult
    data class Alias(val target: String) : SlashResult
    data class Send(val message: String, val display: String?, val notice: String?) : SlashResult
    data class Prefill(val message: String, val notice: String?) : SlashResult
}

internal val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

private fun clean(text: String): String {
    val stripped = ANSI.replace(text, "").trim()
    return if (stripped.isEmpty() || stripped == "(no output)") "Done." else stripped
}

fun parseSlashResult(result: JSONObject): SlashResult? {
    fun opt(key: String) = result.optString(key).ifEmpty { null }
    return when (result.optString("type")) {
        "exec", "plugin" -> SlashResult.Output(clean(result.optString("output")), cleanWarning(result))
        "alias" -> opt("target")?.let { SlashResult.Alias(it) }
        "send", "skill" -> opt("message")?.let { SlashResult.Send(it, opt("display"), opt("notice")) }
        "prefill" -> opt("message")?.let { SlashResult.Prefill(it, opt("notice")) }
        "" -> if (result.has("output")) SlashResult.Output(clean(result.optString("output")), cleanWarning(result)) else null
        else -> null
    }
}

private fun cleanWarning(result: JSONObject): String =
    ANSI.replace(result.optString("warning"), "").trim()

fun slashErrorText(e: Exception, name: String): String = when {
    e is IOException -> "Can't reach the gateway. Check your connection."
    e is RpcError && e.code == 5030 -> "The gateway's command helper isn't ready. Try again in a moment."
    e is RpcError && e.code == 4018 -> "/$name isn't a command this gateway knows."
    e is RpcError && e.code == 4004 -> e.message.orEmpty()
    else -> "/$name didn't work. Try again."
}
