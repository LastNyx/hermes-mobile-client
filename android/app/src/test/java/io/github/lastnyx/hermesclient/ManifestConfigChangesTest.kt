package io.github.lastnyx.hermesclient

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestConfigChangesTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun mainActivityConfigChanges(): String {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.exists() }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val activities = factory.newDocumentBuilder().parse(file).getElementsByTagName("activity")
        val main = (0 until activities.length)
            .map { activities.item(it) as org.w3c.dom.Element }
            .firstOrNull { it.getAttributeNS(androidNs, "name") == ".MainActivity" }
        assertNotNull("MainActivity not in manifest", main)
        return main!!.getAttributeNS(androidNs, "configChanges")
    }

    @Test
    fun rotationDoesNotRecreateMainActivity() {
        val flags = mainActivityConfigChanges().split("|")
        for (needed in listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout", "keyboardHidden", "fontScale")) {
            assertTrue("configChanges lacks $needed", needed in flags)
        }
        assertFalse("locale must recreate", "locale" in flags)
    }
}
