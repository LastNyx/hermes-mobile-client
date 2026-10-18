package io.github.lastnyx.hermesclient.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentRefsTest {
    private fun file(name: String, path: String) = AttachmentRef(AttachmentKind.File, name, path)
    private fun image(name: String, path: String) = AttachmentRef(AttachmentKind.Image, name, path)

    @Test fun fileLineAndTypedText() =
        assertEquals(listOf(file("a.txt", "/h/a.txt")) to "hello", splitAttachmentRefs("@file:/h/a.txt\nhello"))

    @Test fun imageLine() =
        assertEquals(listOf(image("p.png", "/h/p.png")) to "look", splitAttachmentRefs("@image:/h/p.png\nlook"))

    @Test fun refLinesThenBlankThenText() =
        assertEquals(
            listOf(file("a.txt", "/h/a.txt"), file("b.pdf", "/h/b.pdf")) to "hello",
            splitAttachmentRefs("@file:/h/a.txt\n@file:/h/b.pdf\n\nhello"),
        )

    @Test fun quotedPathWithSpaces() =
        assertEquals(listOf(file("my file.txt", "/a/my file.txt")) to "x", splitAttachmentRefs("@file:\"/a/my file.txt\"\nx"))

    @Test fun backtickedPath() =
        assertEquals(listOf(file("b.txt", "/a/b.txt")) to "x", splitAttachmentRefs("@file:`/a/b.txt`\nx"))

    @Test fun windowsPath() =
        assertEquals(listOf(file("b.txt", "C:\\a\\b.txt")) to "x", splitAttachmentRefs("@file:C:\\a\\b.txt\nx"))

    @Test fun footerIsDroppedAndItsFileRefIsAdded() =
        assertEquals(
            listOf(file("c.txt", "/h/c.txt")) to "typed",
            splitAttachmentRefs("typed\n\n--- Attached Context ---\n📄 @file:/h/c.txt (3 lines)\nsecret contents"),
        )

    @Test fun sameFileInLineAndFooterAppearsOnce() =
        assertEquals(
            listOf(file("c.txt", "/h/c.txt")) to "typed",
            splitAttachmentRefs("@file:/h/c.txt\n\ntyped\n--- Attached Context ---\n@file:/h/c.txt\nbody"),
        )

    @Test fun closingImageRunAfterFooter() =
        assertEquals(
            listOf(image("p.png", "/h/p.png")) to "typed",
            splitAttachmentRefs("typed\n--- Attached Context ---\nbody\n@image:/h/p.png\n"),
        )

    @Test fun plainTextIsUnchanged() =
        assertEquals(emptyList<AttachmentRef>() to "  hi \n", splitAttachmentRefs("  hi \n"))

    @Test fun emptyString() = assertEquals(emptyList<AttachmentRef>() to "", splitAttachmentRefs(""))

    @Test fun fileWithoutPathStaysText() =
        assertEquals(emptyList<AttachmentRef>() to "@file:\nhi", splitAttachmentRefs("@file:\nhi"))

    @Test fun historyTextLists() =
        assertEquals("typed\n📎 a.txt\n[image]", historyText("@file:/h/a.txt\n@image:/h/p.png\n\ntyped"))

    @Test fun historyTextImageOnly() = assertEquals("[image]", historyText("@image:/h/p.png"))

    @Test fun historyTextPlain() = assertEquals("just text", historyText("just text"))
}
