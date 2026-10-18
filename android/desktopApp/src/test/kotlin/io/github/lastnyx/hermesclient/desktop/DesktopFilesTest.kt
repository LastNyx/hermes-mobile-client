package io.github.lastnyx.hermesclient.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopFilesTest {
    private fun folder(): Path = Files.createTempDirectory("hermes-files")

    @Test
    fun everyStorageSurvivesANewDesktopFiles() {
        val dir = folder()
        val first = DesktopFiles(dir)
        first.gateways.save("""{"a":1}""")
        first.tokens.save("sealed")
        first.profiles.save("g1", "work")
        first.profiles.save("g2", "home")
        first.drafts.save("k1", "hello")
        first.drafts.save("k2", "bye")
        first.sessionView.save("sort", "recent")
        first.textScale.save("large")
        first.profiles.save("g2", null)
        first.drafts.clear("k2")

        val second = DesktopFiles(dir)
        assertEquals("""{"a":1}""", second.gateways.load())
        assertEquals("sealed", second.tokens.load())
        assertEquals("work", second.profiles.load("g1"))
        assertNull(second.profiles.load("g2"))
        assertEquals("hello", second.drafts.load("k1"))
        assertNull(second.drafts.load("k2"))
        assertEquals("recent", second.sessionView.load("sort"))
        assertEquals("large", second.textScale.load())
        assertEquals("sealed", second.tokens.load())
        second.tokens.save(null)
        assertNull(DesktopFiles(dir).tokens.load())
    }

    @Test
    fun plainCipherReturnsTheBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        assertArrayEquals(bytes, PlainCipher.encrypt(bytes))
        assertArrayEquals(bytes, PlainCipher.decrypt(bytes))
    }

    @Test
    fun leftoverHalfWrittenFileNeverReplacesAGoodOne() {
        val dir = folder()
        val files = DesktopFiles(dir)
        files.gateways.save("good")
        Files.writeString(dir.resolve("gateways.json.tmp"), "hal")
        assertEquals("good", DesktopFiles(dir).gateways.load())
        files.gateways.save("better")
        assertEquals("better", DesktopFiles(dir).gateways.load())
        Files.newDirectoryStream(dir).use { assertEquals(setOf("gateways.json"), it.map { p -> p.fileName.toString() }.toSet()) }
    }

    @Test
    fun tokenFileIsOwnerOnlyOnPosix() {
        assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val dir = folder()
        DesktopFiles(dir).tokens.save("secret")
        val mode = PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("tokens.bin")))
        assertEquals("rw-------", mode)
    }

    @Test
    fun dataFolderPerOs() {
        assertEquals(
            Path.of("C:\\Users\\u\\AppData\\Roaming", "HermesClient"),
            dataFolder("Windows 11", mapOf("APPDATA" to "C:\\Users\\u\\AppData\\Roaming"), "/home/u"),
        )
        assertEquals(Path.of("/x/share", "hermes-client"), dataFolder("Linux", mapOf("XDG_DATA_HOME" to "/x/share"), "/home/u"))
        assertEquals(Path.of("/home/u", ".local", "share", "hermes-client"), dataFolder("Linux", emptyMap(), "/home/u"))
    }
}
