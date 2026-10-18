package io.github.lastnyx.hermesclient.data.sessions

import org.json.JSONObject

/** A gateway workspace of one or more folders, as `projects.tree` reports it. */
data class Project(
    val id: String,
    val label: String,
    val sessionCount: Int,
    val lastActive: Double,
    val isAuto: Boolean,
    val isNoProject: Boolean,
    val sessionIds: List<String>,
    val primaryPath: String?,
)

/** `optString` turns JSON null into "null", so every text read checks `isNull` first. */
fun parseProjects(result: JSONObject?): List<Project> {
    val rows = result?.optJSONArray("projects") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        fun text(key: String) = if (row.isNull(key)) null else (row.opt(key) as? String)?.takeIf { it.isNotBlank() }
        val id = text("id") ?: return@mapNotNull null
        val ids = row.optJSONArray("sessionIds")
        Project(
            id = id,
            label = text("label") ?: id,
            sessionCount = row.optInt("sessionCount"),
            lastActive = if (row.isNull("lastActive")) 0.0 else (row.opt("lastActive") as? Number)?.toDouble() ?: 0.0,
            isAuto = row.optBoolean("isAuto"),
            isNoProject = row.optBoolean("isNoProject"),
            sessionIds = (0 until (ids?.length() ?: 0)).mapNotNull { j ->
                (ids!!.opt(j) as? String)?.takeIf { it.isNotBlank() }
            },
            primaryPath = text("path"),
        )
    }
}
