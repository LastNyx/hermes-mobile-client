package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject

enum class ProcessState { Running, Done, Failed }

data class BackgroundProcess(
    val id: String,
    val title: String,
    val state: ProcessState,
    val exitCode: Int?,
    val output: String?,
)

private const val FALLBACK_TITLE = "background process"

/** Never throws: every field may be missing; a row without an id is skipped. */
fun parseProcesses(result: JSONObject?): List<BackgroundProcess> {
    val rows = result?.optJSONArray("processes") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val id = row.stringOrNull("session_id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val exitCode = if (row.isNull("exit_code")) null else (row.opt("exit_code") as? Number)?.toInt()
        val state = when {
            row.stringOrNull("status") == "running" -> ProcessState.Running
            exitCode != null && exitCode != 0 -> ProcessState.Failed
            else -> ProcessState.Done
        }
        val title = row.stringOrNull("command")?.lineSequence()?.first()?.trim().orEmpty().ifEmpty { FALLBACK_TITLE }
        BackgroundProcess(id, title, state, exitCode, row.stringOrNull("output_tail"))
    }
}

fun mergeProcesses(incoming: List<BackgroundProcess>, dismissed: Set<String>): List<BackgroundProcess> =
    incoming.filter { it.id !in dismissed }
