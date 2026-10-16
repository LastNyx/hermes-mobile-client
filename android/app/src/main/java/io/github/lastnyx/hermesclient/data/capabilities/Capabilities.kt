package io.github.lastnyx.hermesclient.data.capabilities

import org.json.JSONArray
import org.json.JSONObject

data class SkillItem(
    val name: String,
    val description: String?,
    val category: String?,
    val enabled: Boolean,
    val usage: Int,
    val provenance: String?,
)

data class ToolsetItem(
    val name: String,
    val label: String,
    val description: String?,
    val enabled: Boolean,
    val configured: Boolean,
)

data class McpServerItem(val name: String, val enabled: Boolean, val plugin: String?, val transport: String?) {
    val locked get() = plugin != null
}

data class McpTestResult(val ok: Boolean, val tools: List<String>, val error: String?)

private const val TEST_FAILED = "Test failed"
private const val ERROR_MAX = 200

// optString turns JSON null into the text "null".
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key)

private fun <T> JSONArray?.rows(map: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(map) }

fun parseSkills(rows: JSONArray?): List<SkillItem> = rows.rows { row ->
    val name = row.text("name") ?: return@rows null
    SkillItem(name, row.text("description"), row.text("category"), row.optBoolean("enabled", false), row.optInt("usage", 0), row.text("provenance"))
}

fun parseToolsets(rows: JSONArray?): List<ToolsetItem> = rows.rows { row ->
    val name = row.text("name") ?: return@rows null
    ToolsetItem(name, row.text("label")?.ifBlank { null } ?: name, row.text("description"), row.optBoolean("enabled", false), row.optBoolean("configured", true))
}

fun parseMcpServers(body: JSONObject?): List<McpServerItem> = body?.optJSONArray("servers").rows { row ->
    val name = row.text("name") ?: return@rows null
    McpServerItem(name, row.optBoolean("enabled", true), row.text("plugin"), row.text("transport"))
}

fun parseMcpTest(body: JSONObject?): McpTestResult {
    if (body == null) return McpTestResult(false, emptyList(), TEST_FAILED)
    val tools = body.optJSONArray("tools").rows { it.text("name") }
    if (body.optBoolean("ok", false)) return McpTestResult(true, tools, null)
    return McpTestResult(false, tools, body.text("error")?.ifBlank { null }?.take(ERROR_MAX) ?: TEST_FAILED)
}
