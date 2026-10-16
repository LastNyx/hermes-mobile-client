package io.github.lastnyx.hermesclient.data.chat

import org.json.JSONObject

/** "1.2k in · 85 out" from a `message.complete` usage object; null when there is nothing worth showing. */
fun usageLine(usage: JSONObject?): String? {
    if (usage == null) return null
    val input = count(usage, "input")
    val output = count(usage, "output")
    if ((input ?: 0L) == 0L && (output ?: 0L) == 0L) return null
    return listOfNotNull(input?.let { "${compact(it)} in" }, output?.let { "${compact(it)} out" }).joinToString(" · ")
}

// opt() keeps a missing key and JSON null apart from a real 0, which optLong would not.
private fun count(usage: JSONObject, key: String): Long? =
    (usage.opt(key) as? Number)?.toLong()?.takeIf { it >= 0 }

private fun compact(n: Long): String = when {
    n < 1000 -> n.toString()
    n < 999_500 -> oneDecimal(n, 1000, "k")
    else -> oneDecimal(n, 1_000_000, "M")
}

private fun oneDecimal(n: Long, unit: Long, suffix: String): String {
    val tenths = (n * 10 + unit / 2) / unit
    val whole = tenths / 10
    return if (tenths % 10 == 0L) "$whole$suffix" else "$whole.${tenths % 10}$suffix"
}
