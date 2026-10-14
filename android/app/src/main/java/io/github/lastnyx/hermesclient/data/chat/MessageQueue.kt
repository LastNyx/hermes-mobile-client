package io.github.lastnyx.hermesclient.data.chat

data class QueueEntry(val id: String, val text: String)

/** Drafts parked while a turn runs. Not thread-safe: the owner serializes access. */
class MessageQueue {
    private var counter = 0
    private val items = mutableListOf<QueueEntry>()

    val entries: List<QueueEntry> get() = items.toList()

    /** Returns null when [text] is blank. */
    fun add(text: String): QueueEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return QueueEntry("q${++counter}", trimmed).also { items += it }
    }

    fun remove(id: String): QueueEntry? {
        val index = items.indexOfFirst { it.id == id }
        return if (index < 0) null else items.removeAt(index)
    }

    fun edit(id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(text = trimmed)
    }

    /** Takes the first entry out of the queue. */
    fun next(): QueueEntry? = if (items.isEmpty()) null else items.removeAt(0)
}
