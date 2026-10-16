package io.github.lastnyx.hermesclient.data.sessions

import io.github.lastnyx.hermesclient.data.live.RpcError
import java.io.IOException

data class NewChatChoice(val label: String, val cwd: String?)

/** Empty means the caller opens a plain chat without a menu. A project without a folder cannot host a chat. */
fun newChatChoices(projects: List<Project>?): List<NewChatChoice> {
    val usable = projects.orEmpty()
        .filter { !it.isNoProject && !it.primaryPath.isNullOrBlank() && it.label.isNotBlank() }
        .sortedByDescending { it.lastActive }
        .map { NewChatChoice(it.label, it.primaryPath) }
    return if (usable.isEmpty()) emptyList() else listOf(NewChatChoice("No project", null)) + usable
}

/** The gateway's own sentence is never shown: it can carry paths. */
fun projectCreateMessage(e: Throwable): String = when {
    e is RpcError && e.code == 5063 -> "That folder already belongs to a project."
    e is RpcError && e.code == -32601 -> "This gateway can't create projects."
    e is IOException -> CANT_REACH_GATEWAY
    else -> "Could not create the project."
}
