package io.github.lastnyx.hermesclient.ui

internal fun toolDetailPreview(detail: String, limit: Int): String =
    if (detail.length <= limit) detail
    else detail.take(limit) + "\n... ${detail.length - limit} more characters"
