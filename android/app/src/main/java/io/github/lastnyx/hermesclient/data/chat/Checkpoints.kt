package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class Checkpoint(val hash: String, val timestamp: String, val message: String)

data class CheckpointList(val enabled: Boolean, val checkpoints: List<Checkpoint>)

data class RestoreResult(
    val success: Boolean,
    val restoredTo: String? = null,
    val restoredFiles: List<String> = emptyList(),
    val skippedUserEdits: List<String> = emptyList(),
    val historyRemoved: Int? = null,
    val error: String? = null,
)

data class CheckpointsState(
    val loading: Boolean = false,
    val enabled: Boolean? = null,
    val checkpoints: List<Checkpoint> = emptyList(),
    val diffFor: String? = null,
    val diffText: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.opt(it) as? String }
}

fun parseCheckpoints(result: JSONObject?): CheckpointList {
    if (result == null) return CheckpointList(false, emptyList())
    val array = result.optJSONArray("checkpoints")
    val rows = (0 until (array?.length() ?: 0)).mapNotNull { i ->
        val row = array!!.optJSONObject(i) ?: return@mapNotNull null
        val hash = row.stringOrNull("hash")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Checkpoint(hash, row.stringOrNull("timestamp").orEmpty(), row.stringOrNull("message").orEmpty())
    }
    return CheckpointList(result.optBoolean("enabled", false), rows)
}

fun parseRestore(result: JSONObject?): RestoreResult {
    if (result == null) return RestoreResult(success = false)
    return RestoreResult(
        success = result.optBoolean("success", false),
        restoredTo = result.stringOrNull("restored_to"),
        restoredFiles = result.strings("restored_files"),
        skippedUserEdits = result.strings("skipped_user_edits"),
        historyRemoved = if (result.has("history_removed") && !result.isNull("history_removed")) result.optInt("history_removed") else null,
        error = result.stringOrNull("error"),
    )
}

private const val RESTORE_FAILED = "Couldn't restore that checkpoint."

fun restoreSummary(r: RestoreResult): String {
    if (!r.success) return r.error?.takeIf { it.isNotBlank() } ?: RESTORE_FAILED
    val removed = r.historyRemoved ?: 0
    return listOfNotNull(
        "Restored to ${r.restoredTo ?: "the checkpoint"}",
        r.restoredFiles.takeIf { it.isNotEmpty() }?.let { "Restored: ${it.joinToString(", ")}" },
        r.skippedUserEdits.takeIf { it.isNotEmpty() }?.let { "Skipped (edited since): ${it.joinToString(", ")}" },
        if (removed > 0) "Removed $removed ${if (removed == 1) "turn" else "turns"} from the chat" else null,
    ).joinToString("\n")
}

private const val DIFF_MAX_LINES = 120
private const val DIFF_MAX_CHARS = 6000

fun capDiff(stat: String, diff: String): String {
    val full = listOf(stat, diff).filter { it.isNotEmpty() }.joinToString("\n\n")
    if (full.isEmpty()) return "No changes"
    val lines = full.lines()
    var cut = full.take(DIFF_MAX_CHARS)
    if (lines.size > DIFF_MAX_LINES) cut = cut.lines().take(DIFF_MAX_LINES).joinToString("\n")
    return if (cut.length < full.length) "$cut\n…" else full
}

/** Checkpoints sheet for the chat's session: list, view diff, restore. */
class CheckpointsController(private val chat: ChatController) {
    private val _state = MutableStateFlow(CheckpointsState())
    val state: StateFlow<CheckpointsState> = _state

    suspend fun load(keepMessage: Boolean = false) {
        _state.update { it.copy(loading = true) }
        try {
            val list = parseCheckpoints(chat.sessionCall("rollback.list") { it })
            _state.update { it.copy(loading = false, enabled = list.enabled, checkpoints = list.checkpoints) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, message = if (keepMessage) it.message else "Couldn't load checkpoints.") }
        }
    }

    suspend fun showDiff(hash: String) {
        try {
            val reply = chat.sessionCall("rollback.diff") { it.put("hash", hash) }
            val text = capDiff(reply.stringOrNull("stat").orEmpty(), reply.stringOrNull("diff").orEmpty())
            _state.update { it.copy(diffFor = hash, diffText = text, message = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(message = "Couldn't load that diff.") }
        }
    }

    suspend fun restore(hash: String) {
        if (chat.state.value.running || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        var restored = false
        try {
            val result = parseRestore(chat.sessionCall("rollback.restore") { it.put("hash", hash) })
            restored = result.success
            _state.update { it.copy(message = restoreSummary(result)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcError) {
            val text = if (e.code == 4009) "Wait for the reply to finish, then try again." else RESTORE_FAILED
            _state.update { it.copy(message = text) }
        } catch (e: Exception) {
            _state.update { it.copy(message = RESTORE_FAILED) }
        } finally {
            _state.update { it.copy(busy = false) }
        }
        if (!restored) return
        try {
            chat.reloadHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The summary stays; the transcript refreshes on the next open.
        }
        _state.update { it.copy(diffFor = null, diffText = null) }
        load(keepMessage = true)
    }
}
