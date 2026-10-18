package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONArray
import org.json.JSONObject

enum class SlashKind { Command, Skill, Option }

/** [text] is the whole new composer text for this suggestion, without a trailing space. */
data class SlashSuggestion(
    val text: String,
    val label: String,
    val description: String,
    val kind: SlashKind,
    val group: String?,
)

data class SlashCatalog(
    val suggestions: List<SlashSuggestion> = emptyList(),
    private val canon: Map<String, String> = emptyMap(),
    private val unavailableNames: Set<String> = emptySet(),
    val warning: String = "",
) {
    fun canonical(name: String): String {
        val key = name.removePrefix("/").lowercase()
        return canon[key] ?: key
    }

    fun unavailable(name: String): Boolean = canonical(name) in unavailableNames
}

private fun bare(name: String) = name.removePrefix("/").lowercase()

private fun JSONArray?.pairs(): List<Pair<String, String>> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull {
        val pair = optJSONArray(it) ?: return@mapNotNull null
        val name = pair.optString(0)
        if (name.isBlank()) null else name to pair.optString(1)
    }
}

fun parseSlashCatalog(result: JSONObject): SlashCatalog {
    val canon = HashMap<String, String>()
    result.optJSONObject("canon")?.let { obj ->
        obj.keys().forEach { canon[bare(it)] = bare(obj.optString(it)) }
    }
    val unavailable = HashSet<String>()
    result.optJSONObject("commands")?.let { obj ->
        obj.keys().forEach { key ->
            val entry = obj.optJSONObject(key) ?: return@forEach
            if (!entry.isNull("desktop")) unavailable += canon[bare(key)] ?: bare(key)
        }
    }
    val suggestions = ArrayList<SlashSuggestion>()
    val inCategory = HashSet<String>()
    val categories = result.optJSONArray("categories")
    for (i in 0 until (categories?.length() ?: 0)) {
        val category = categories?.optJSONObject(i) ?: continue
        val group = category.optString("name")
        for ((name, description) in category.optJSONArray("pairs").pairs()) {
            inCategory += bare(name)
            if (bare(name) in unavailable) continue
            suggestions += SlashSuggestion("/" + name.removePrefix("/"), "/" + name.removePrefix("/"), description, SlashKind.Command, group)
        }
    }
    val seen = HashSet<String>()
    for ((name, description) in result.optJSONArray("pairs").pairs()) {
        val key = bare(name)
        if (key in inCategory || key in unavailable || !seen.add(key)) continue
        suggestions += SlashSuggestion("/" + name.removePrefix("/"), "/" + name.removePrefix("/"), description, SlashKind.Skill, "Skills")
    }
    return SlashCatalog(suggestions, canon, unavailable, result.optString("warning").trim())
}

fun parseSlashCompletions(text: String, result: JSONObject): List<SlashSuggestion> {
    val replaceFrom = if (result.has("replace_from")) result.optInt("replace_from", 1) else 1
    val option = replaceFrom > 1
    val prefix = if (option) text.substring(0, minOf(replaceFrom, text.length)) else ""
    val items = result.optJSONArray("items") ?: return emptyList()
    val out = LinkedHashMap<String, SlashSuggestion>()
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        val raw = item.optString("text")
        if (raw.isBlank()) continue
        val kind = when {
            option -> SlashKind.Option
            item.optString("kind") == "skill" -> SlashKind.Skill
            else -> SlashKind.Command
        }
        val full = if (option) prefix + raw else "/" + raw.removePrefix("/")
        val display = item.optString("display")
        val label = when {
            display.isBlank() -> full
            option -> display
            else -> "/" + display.removePrefix("/")
        }
        out.putIfAbsent(full, SlashSuggestion(full, label, item.optString("meta"), kind, null))
    }
    return out.values.toList()
}
