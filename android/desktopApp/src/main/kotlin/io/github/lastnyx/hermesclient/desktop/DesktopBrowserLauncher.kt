package io.github.lastnyx.hermesclient.desktop

import io.github.lastnyx.hermesclient.data.auth.BrowserLauncher
import java.awt.Desktop
import java.net.URI

/** Tries the system browser, then `xdg-open`, then hands the URL to [showUrl] so the user can copy it. */
class DesktopBrowserLauncher(
    private val browse: (URI) -> Unit = ::awtBrowse,
    private val xdgOpen: (String) -> Unit = ::runXdgOpen,
    private val showUrl: (String) -> Unit,
) : BrowserLauncher {
    override fun open(url: String) {
        // Any failure moves to the next option; the last one cannot fail.
        if (runCatching { browse(URI(url)) }.isSuccess) return
        if (runCatching { xdgOpen(url) }.isSuccess) return
        showUrl(url)
    }
}

private fun awtBrowse(uri: URI) {
    check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { "No desktop browser" }
    Desktop.getDesktop().browse(uri)
}

private fun runXdgOpen(url: String) {
    ProcessBuilder("xdg-open", url).start()
}
