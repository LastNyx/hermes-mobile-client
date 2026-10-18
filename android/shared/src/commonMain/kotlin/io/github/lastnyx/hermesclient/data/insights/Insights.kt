package io.github.lastnyx.hermesclient.data.insights

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class ModelUsage(val model: String, val tokens: Long, val cost: Double, val sessions: Long, val apiCalls: Long)

data class ToolUsage(val tool: String, val count: Long)

data class SkillUsage(val skill: String, val count: Long)

data class DayUsage(val day: String, val tokens: Long, val cost: Double, val sessions: Long)

data class UsageInsights(
    val periodDays: Int = 0,
    val sessions: Long = 0,
    val apiCalls: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cost: Double = 0.0,
    val byModel: List<ModelUsage> = emptyList(),
    val tools: List<ToolUsage> = emptyList(),
    val skills: List<SkillUsage> = emptyList(),
    val daily: List<DayUsage> = emptyList(),
) {
    val isEmpty: Boolean get() = sessions == 0L && apiCalls == 0L && byModel.isEmpty() && tools.isEmpty() && skills.isEmpty()
}

private const val TOP = 10
private const val DAYS_SHOWN = 14

fun usageCost(actual: Double, estimated: Double): Double = if (actual > 0) actual else estimated

// SQL SUM gives JSON null with no rows, and optLong/optDouble would not read that as 0.
private fun JSONObject.num(key: String): Double = if (isNull(key)) 0.0 else optDouble(key, 0.0)

private fun JSONObject.long(key: String): Long = num(key).toLong()

private fun JSONObject.name(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun <T> JSONArray?.rows(map: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(map) }

fun parseUsage(body: JSONObject?): UsageInsights {
    if (body == null) return UsageInsights()
    val totals = body.optJSONObject("totals") ?: JSONObject()
    return UsageInsights(
        periodDays = body.long("period_days").toInt(),
        sessions = totals.long("total_sessions"),
        apiCalls = totals.long("total_api_calls"),
        inputTokens = totals.long("total_input"),
        outputTokens = totals.long("total_output"),
        cacheReadTokens = totals.long("total_cache_read"),
        cost = usageCost(totals.num("total_actual_cost"), totals.num("total_estimated_cost")),
        byModel = body.optJSONArray("by_model").rows { r ->
            r.name("model")?.let {
                ModelUsage(it, r.long("input_tokens") + r.long("output_tokens"), usageCost(r.num("actual_cost"), r.num("estimated_cost")), r.long("sessions"), r.long("api_calls"))
            }
        },
        tools = body.optJSONArray("tools").rows { r -> r.name("tool")?.let { ToolUsage(it, r.long("count")) } },
        skills = body.optJSONObject("skills")?.optJSONArray("top_skills").rows { r ->
            r.name("skill")?.let { SkillUsage(it, r.long("total_count")) }
        },
        daily = body.optJSONArray("daily").rows { r ->
            r.name("day")?.let {
                DayUsage(it, r.long("input_tokens") + r.long("output_tokens"), usageCost(r.num("actual_cost"), r.num("estimated_cost")), r.long("sessions"))
            }
        },
    )
}

fun formatTokens(n: Long): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> scaled(n / 1000.0, "k")
    else -> scaled(n / 1_000_000.0, "M")
}

private fun scaled(value: Double, unit: String): String = String.format(Locale.US, "%.1f", value).removeSuffix(".0") + unit

fun formatCost(usd: Double): String = when {
    usd <= 0 -> "$0.00"
    usd < 0.01 -> "<$0.01"
    else -> String.format(Locale.US, "$%.2f", usd)
}

fun summaryLines(u: UsageInsights): List<String> = listOf(
    "${u.sessions} chats, ${u.apiCalls} requests",
    "${formatTokens(u.inputTokens)} in, ${formatTokens(u.outputTokens)} out, ${formatTokens(u.cacheReadTokens)} cached",
    "Cost ${formatCost(u.cost)}",
)

fun modelLines(u: UsageInsights): List<String> =
    u.byModel.take(TOP).map { "${it.model}  ${formatTokens(it.tokens)} tokens  ${formatCost(it.cost)}" }

fun toolLines(u: UsageInsights): List<String> = u.tools.take(TOP).map { "${it.tool}  ${it.count}" }

fun skillLines(u: UsageInsights): List<String> = u.skills.take(TOP).map { "${it.skill}  ${it.count}" }

fun dayLines(u: UsageInsights): List<String> =
    u.daily.sortedByDescending { it.day }.take(DAYS_SHOWN).map { "${it.day}  ${formatTokens(it.tokens)} tokens  ${formatCost(it.cost)}" }

data class InsightRow(val name: String, val value: String)

fun summaryStats(u: UsageInsights): List<InsightRow> = listOf(
    InsightRow("Chats", u.sessions.toString()),
    InsightRow("Requests", u.apiCalls.toString()),
    InsightRow("Tokens in", formatTokens(u.inputTokens)),
    InsightRow("Tokens out", formatTokens(u.outputTokens)),
    InsightRow("Cached", formatTokens(u.cacheReadTokens)),
    InsightRow("Cost", formatCost(u.cost)),
)

private fun usageValue(tokens: Long, cost: Double) = "${formatTokens(tokens)} · ${formatCost(cost)}"

fun modelRows(u: UsageInsights): List<InsightRow> =
    u.byModel.take(TOP).map { InsightRow(it.model, usageValue(it.tokens, it.cost)) }

fun toolRows(u: UsageInsights): List<InsightRow> = u.tools.take(TOP).map { InsightRow(it.tool, it.count.toString()) }

fun skillRows(u: UsageInsights): List<InsightRow> = u.skills.take(TOP).map { InsightRow(it.skill, it.count.toString()) }

fun dayRows(u: UsageInsights): List<InsightRow> =
    u.daily.sortedByDescending { it.day }.take(DAYS_SHOWN).map { InsightRow(it.day, usageValue(it.tokens, it.cost)) }

fun insightLines(u: UsageInsights): List<String> = summaryLines(u) + modelLines(u) + toolLines(u) + skillLines(u) + dayLines(u)
