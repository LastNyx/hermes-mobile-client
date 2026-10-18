package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.sessions.Session

fun chatTitle(sessions: List<Session>, id: String?): String =
    sessions.firstOrNull { it.id == id }?.title?.trim()?.takeIf { it.isNotEmpty() } ?: "New chat"
