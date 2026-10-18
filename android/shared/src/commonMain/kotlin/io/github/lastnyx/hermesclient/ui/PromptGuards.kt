package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.live.LinkState

/** Drops taps in the first moments a card shows: it can pop up under a thumb that is scrolling. */
class TapGuard(private val clock: () -> Long, private val delayMs: Long = 700L) {
    private val shownAt = clock()

    fun allows(): Boolean = clock() - shownAt >= delayMs
}

fun promptActionsEnabled(link: LinkState): Boolean = link is LinkState.Connected

data class CommandPreview(val text: String, val cut: Boolean)

fun commandPreview(command: String, maxLines: Int = 4): CommandPreview {
    val lines = command.trimEnd('\n').lines()
    return if (lines.size <= maxLines) CommandPreview(command, false)
    else CommandPreview(lines.take(maxLines).joinToString("\n"), true)
}
