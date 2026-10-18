package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.live.LinkEvent
import org.json.JSONObject

enum class SubagentStatus { Queued, Running, Completed, Failed, Error, Timeout, Interrupted }

data class Subagent(
    val key: String,
    val id: String?,
    val parentId: String?,
    val delegationId: String?,
    val depth: Int?,
    val goal: String,
    val taskIndex: Int,
    val taskCount: Int,
    val model: String?,
    val status: SubagentStatus,
    val lastTool: String?,
    val toolCount: Int?,
    val activity: List<String>,
    val summary: String?,
    val durationSeconds: Double?,
)

private const val MAX_ACTIVITY = 24
private const val MAX_FINISHED = 100
private const val MAX_LINE = 200

private val TERMINAL = setOf(
    SubagentStatus.Completed, SubagentStatus.Failed, SubagentStatus.Error,
    SubagentStatus.Timeout, SubagentStatus.Interrupted,
)

private fun JSONObject.str(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }

private fun JSONObject.int(key: String): Int? =
    if (has(key) && !isNull(key)) (opt(key) as? Number)?.toInt() else null

private fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) (opt(key) as? Number)?.toDouble() else null

private fun statusOf(word: String?): SubagentStatus? = when (word) {
    "queued" -> SubagentStatus.Queued
    "running" -> SubagentStatus.Running
    "completed" -> SubagentStatus.Completed
    "failed" -> SubagentStatus.Failed
    "error" -> SubagentStatus.Error
    "timeout" -> SubagentStatus.Timeout
    "interrupted" -> SubagentStatus.Interrupted
    else -> null
}

private fun activityLine(raw: String): String {
    val line = raw.trim().replace(Regex("\\s+"), " ")
    return if (line.length > MAX_LINE) line.take(MAX_LINE - 1) + "…" else line
}

fun reduceSubagents(current: List<Subagent>, event: LinkEvent): List<Subagent> {
    if (!event.type.startsWith("subagent.")) return current
    val p = event.payload ?: return current
    val id = p.str("subagent_id")
    val goal = p.str("goal").orEmpty()
    val index = p.int("task_index") ?: 0

    var at = if (id != null) current.indexOfFirst { it.id == id } else -1
    if (at < 0) at = current.indexOfFirst { it.id == null && it.taskIndex == index && it.goal == goal }
    val old = current.getOrNull(at)
        ?: Subagent(id ?: "task$index:$goal", null, null, null, 0, goal, index, p.int("task_count") ?: 1, null, SubagentStatus.Queued, null, null, emptyList(), null, null)

    val kind = event.type.removePrefix("subagent.")
    val tool = p.str("tool_name")
    val toolsLine = tool?.let { name -> p.str("tool_preview")?.let { "$name: $it" } ?: name }
    val thinkingLine = if (kind == "thinking") p.str("text")?.lineSequence()?.firstOrNull { it.isNotBlank() } else null
    val newLine = when (kind) {
        "tool", "progress" -> toolsLine
        "thinking" -> thinkingLine
        else -> null
    }?.let(::activityLine)
    val activity = if (newLine != null && old.activity.lastOrNull() != newLine) (old.activity + newLine).takeLast(MAX_ACTIVITY) else old.activity

    val fromPayload = statusOf(p.str("status"))
    val status = fromPayload ?: when (kind) {
        "spawn_requested" -> SubagentStatus.Queued
        "start" -> SubagentStatus.Running
        "progress", "thinking", "tool" -> if (old.status in TERMINAL) old.status else SubagentStatus.Running
        "complete" -> SubagentStatus.Completed
        else -> old.status
    }

    val row = old.copy(
        id = id ?: old.id,
        parentId = p.str("parent_id") ?: old.parentId,
        delegationId = p.str("delegation_id") ?: old.delegationId,
        depth = p.int("depth") ?: old.depth,
        model = p.str("model") ?: old.model,
        status = status,
        lastTool = if (kind == "tool" || kind == "progress") tool ?: old.lastTool else old.lastTool,
        toolCount = p.int("tool_count") ?: old.toolCount,
        activity = activity,
        summary = p.str("summary") ?: old.summary,
        durationSeconds = p.dbl("duration_seconds") ?: old.durationSeconds,
    )
    val list = if (at >= 0) current.mapIndexed { i, s -> if (i == at) row else s } else current + row
    return dropOldFinished(list)
}

fun parseSubagentList(reply: JSONObject?): List<Subagent> {
    val rows = reply?.optJSONArray("subagents") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val id = row.str("subagent_id") ?: return@mapNotNull null
        Subagent(
            key = id, id = id, parentId = row.str("parent_id"), delegationId = row.str("delegation_id"),
            depth = row.int("depth"), goal = row.str("goal").orEmpty(), taskIndex = 0, taskCount = 1,
            model = row.str("model"), status = statusOf(row.str("status")) ?: SubagentStatus.Running,
            lastTool = row.str("last_tool"), toolCount = row.int("tool_count"),
            activity = emptyList(), summary = null, durationSeconds = null,
        )
    }
}

/** The list is the live roster only: rows it does not mention are left for events to end. */
fun mergeListed(current: List<Subagent>, listed: List<Subagent>): List<Subagent> {
    val byId = listed.filter { it.id != null }.associateBy { it.id }
    val known = current.mapNotNull { it.id }.toSet()
    val updated = current.map { old ->
        val l = byId[old.id] ?: return@map old
        old.copy(
            status = if (old.status in TERMINAL) old.status else l.status,
            model = l.model ?: old.model,
            lastTool = l.lastTool ?: old.lastTool,
            toolCount = l.toolCount ?: old.toolCount,
            parentId = l.parentId ?: old.parentId,
            depth = l.depth ?: old.depth,
            delegationId = l.delegationId ?: old.delegationId,
        )
    }
    return updated + listed.filter { it.id !in known }.map { it.copy(depth = it.depth ?: 0) }
}

private fun dropOldFinished(list: List<Subagent>): List<Subagent> {
    var excess = list.count { it.status in TERMINAL } - MAX_FINISHED
    if (excess <= 0) return list
    return list.filter { !(it.status in TERMINAL && excess-- > 0) }
}

fun subagentMeta(s: Subagent): String {
    val word = when (s.status) {
        SubagentStatus.Queued -> "queued"
        SubagentStatus.Running -> "running"
        SubagentStatus.Completed -> "done"
        SubagentStatus.Failed -> "failed"
        SubagentStatus.Error -> "error"
        SubagentStatus.Timeout -> "timed out"
        SubagentStatus.Interrupted -> "interrupted"
    }
    val duration = s.durationSeconds?.let {
        val secs = it.toLong()
        if (secs < 60) "${secs}s" else "${secs / 60}m ${secs % 60}s"
    }
    return listOfNotNull(word, s.model, duration, s.lastTool).filter { it.isNotEmpty() }.joinToString(" · ")
}

fun groupSubagents(all: List<Subagent>): List<List<Subagent>> =
    all.groupBy { it.delegationId }.values.toList()
