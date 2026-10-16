package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.PreparedAttachment
import io.github.lastnyx.hermesclient.data.chat.acceptPick

class AttachTrayState(val items: List<PreparedAttachment> = emptyList()) {
    val isEmpty: Boolean get() = items.isEmpty()

    fun add(more: List<PreparedAttachment>): Pair<AttachTrayState, String?> {
        val decision = acceptPick(items.size, more.size)
        return AttachTrayState(items + more.take(decision.accepted)) to decision.message
    }

    fun remove(index: Int): AttachTrayState =
        if (index !in items.indices) this else AttachTrayState(items.filterIndexed { i, _ -> i != index })

    fun clear(): AttachTrayState = AttachTrayState()
}

private const val CHIP_MAX = 24

fun chipLabel(a: PreparedAttachment): String =
    if (a.name.length <= CHIP_MAX) a.name else a.name.take(CHIP_MAX - 1) + "…"

// Attachments cannot redirect a running turn, so they alone never enable sending then.
fun canSend(draft: String, tray: AttachTrayState, running: Boolean): Boolean =
    draft.isNotBlank() || (!tray.isEmpty && !running)
