package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.RpcError
import org.json.JSONObject

sealed interface RewindResult {
    data object Sent : RewindResult
    data object Empty : RewindResult
    data object Busy : RewindResult
    data object Stale : RewindResult
    data object WouldEraseAll : RewindResult
    data object NoTarget : RewindResult
    data object Unreachable : RewindResult
    data object Failed : RewindResult
}

/** `session_id` is added by the session call. [eraseAll] confirms a cut that empties the transcript. */
fun rewindParams(rowId: Long, text: String, eraseAll: Boolean): JSONObject =
    JSONObject().put("text", text).put("confirm_truncate", true).put("truncate_before_row_id", rowId).also {
        if (eraseAll) it.put("confirm_empty_truncate", true)
    }

fun rewindResultFor(error: RpcError): RewindResult = when (error.code) {
    4009 -> RewindResult.Busy
    4018 -> RewindResult.Stale
    4028 -> RewindResult.WouldEraseAll
    4004 -> RewindResult.NoTarget
    else -> RewindResult.Failed
}
