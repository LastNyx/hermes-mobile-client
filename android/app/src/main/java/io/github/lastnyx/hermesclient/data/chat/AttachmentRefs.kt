package io.github.lastnyx.hermesclient.data.chat

import io.github.lastnyx.hermesclient.data.media.pathName

data class AttachmentRef(val kind: AttachmentKind, val name: String, val path: String)

private const val PATH = "(?:\"([^\"]+)\"|`([^`]+)`|(\\S+))"
private val REF_LINE = Regex("^@(file|image):$PATH$")
private val FOOTER = Regex("(?:^|\\n)--- (?:Context Warnings|Attached Context) ---[ \\t]*(?:\\n|$)")
private val FOOTER_FILE = Regex("@file:$PATH")

private fun MatchResult.path() = groupValues.drop(groupValues.size - 3).first { it.isNotEmpty() }

private fun refOf(kind: AttachmentKind, path: String) = AttachmentRef(kind, pathName(path), path)

fun splitAttachmentRefs(text: String): Pair<List<AttachmentRef>, String> {
    val footer = FOOTER.find(text)
    val body = if (footer == null) text else text.substring(0, footer.range.first)
    val refs = mutableListOf<AttachmentRef>()
    val kept = mutableListOf<String>()
    for (line in body.split("\n")) {
        val m = REF_LINE.find(line.trim())
        if (m == null) kept += line
        else refs += refOf(if (m.groupValues[1] == "image") AttachmentKind.Image else AttachmentKind.File, m.path())
    }
    if (refs.isEmpty() && footer == null) return emptyList<AttachmentRef>() to text
    if (footer != null) {
        val tail = text.substring(footer.range.last + 1)
        FOOTER_FILE.findAll(tail).forEach { m ->
            val ref = refOf(AttachmentKind.File, m.path())
            if (refs.none { it.name == ref.name }) refs += ref
        }
        val images = ArrayDeque<AttachmentRef>()
        for (line in tail.trimEnd().split("\n").asReversed()) {
            val m = REF_LINE.find(line.trim())
            if (m == null || m.groupValues[1] != "image") break
            images.addFirst(refOf(AttachmentKind.Image, m.path()))
        }
        refs += images
    }
    return refs to kept.joinToString("\n").trim()
}

/** A stored user row: [text] keeps file lines only, pictures come back as gateway [imagePaths] to draw. */
data class HistoryParts(val text: String, val imagePaths: List<String>)

fun historyParts(text: String): HistoryParts {
    val (refs, visible) = splitAttachmentRefs(text)
    val files = refs.filter { it.kind == AttachmentKind.File }.map { attachmentLine(it.kind, it.name) }
    return HistoryParts(
        (listOfNotNull(visible.ifEmpty { null }) + files).joinToString("\n"),
        refs.filter { it.kind == AttachmentKind.Image }.map { it.path },
    )
}

fun historyText(text: String): String {
    val (refs, visible) = splitAttachmentRefs(text)
    if (refs.isEmpty()) return visible
    return (listOfNotNull(visible.ifEmpty { null }) + refs.map { attachmentLine(it.kind, it.name) }).joinToString("\n")
}
