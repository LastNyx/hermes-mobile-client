package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.chat.AttachLimits
import io.github.lastnyx.hermesclient.data.chat.AttachmentKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopAttachmentReaderTest {
    @Test
    fun pickedFilesAreMappedAndOversizeOnesRefusedWithTheAndroidMessage() {
        val dir = File.createTempFile("pick", "").apply { delete(); mkdirs() }
        try {
            val small = File(dir, "notes.txt").apply { writeText("hello") }
            val ok = readPickedFile(small).getOrThrow()
            assertEquals("notes.txt", ok.name)
            assertEquals(AttachmentKind.File, ok.kind)
            assertEquals("hello", String(ok.bytes))

            val big = File(dir, "big.txt").apply {
                outputStream().use { it.write(ByteArray((AttachLimits.FILE_MAX_BYTES + 1).toInt())) }
            }
            assertEquals("big.txt is larger than 10 MB.", readPickedFile(big).exceptionOrNull()?.message)
        } finally {
            dir.deleteRecursively()
        }
    }
}
