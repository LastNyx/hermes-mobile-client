package io.github.lastnyx.hermesclient.data.cron

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class CronJob(
    val id: String,
    val name: String,
    val schedule: String,
    val state: String,
    val nextRunAt: String?,
    val lastRunAt: String?,
    val lastStatus: String?,
    val lastError: String?,
    val prompt: String = "",
)

fun cronFormError(prompt: String, schedule: String): String? = when {
    prompt.isBlank() -> "Add a prompt."
    schedule.isBlank() -> "Add a schedule, like every 30m."
    else -> null
}

fun cronEditUpdates(job: CronJob, name: String, prompt: String, schedule: String): Map<String, String> {
    val n = name.trim()
    val p = prompt.trim()
    val s = schedule.trim()
    return buildMap {
        if (n.isNotEmpty() && n != job.name) put("name", n)
        if (p != job.prompt) put("prompt", p)
        if (s != job.schedule) put("schedule", s)
    }
}

data class CronRun(val title: String, val preview: String, val startedAt: Double, val source: String)

// optString turns a JSON null into the text "null".
private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun parseJobs(rows: JSONArray?): List<CronJob> {
    if (rows == null) return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val id = row.text("id")?.takeIf { it != "unknown" } ?: return@mapNotNull null
        CronJob(
            id = id,
            name = row.text("name") ?: id,
            schedule = row.text("schedule_display") ?: "",
            state = row.text("state") ?: "",
            nextRunAt = row.text("next_run_at"),
            lastRunAt = row.text("last_run_at"),
            lastStatus = row.text("last_status"),
            lastError = row.text("last_error"),
            prompt = row.text("prompt") ?: "",
        )
    }
}

fun parseRuns(body: JSONObject?): List<CronRun> {
    val rows = body?.optJSONArray("runs") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        CronRun(
            title = row.text("title") ?: "Run",
            preview = row.text("preview") ?: "",
            startedAt = row.optDouble("started_at", 0.0),
            source = row.text("source") ?: "",
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

fun formatCronTime(iso: String?, zone: ZoneId): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(TIME_FORMAT)
    } catch (e: java.time.DateTimeException) {
        null
    }
}

fun formatRunTime(epochSeconds: Double, zone: ZoneId): String =
    if (epochSeconds <= 0.0) "" else Instant.ofEpochMilli((epochSeconds * 1000).toLong()).atZone(zone).format(TIME_FORMAT)
