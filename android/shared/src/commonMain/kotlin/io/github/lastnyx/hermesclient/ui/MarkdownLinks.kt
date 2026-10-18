package io.github.lastnyx.hermesclient.ui

import io.github.lastnyx.hermesclient.data.media.WINDOWS_PATH
import java.net.URLDecoder

private val allowedPrefixes = listOf("http://", "https://", "mailto:")

/** Only web and mail links may open; anything else a reply carries is ignored. */
fun isAllowedLink(uri: String): Boolean {
    val link = uri.trim()
    if (link.any { it.isISOControl() }) return false
    return allowedPrefixes.any { link.startsWith(it, ignoreCase = true) }
}

/** The gateway path a link points at (`file://` or a bare absolute path), else null. The phone never opens it itself. */
fun gatewayFilePath(uri: String): String? {
    val link = uri.trim()
    if (link.any { it.isISOControl() }) return null
    val path = if (link.startsWith("file://", ignoreCase = true)) {
        val rest = link.substring("file://".length)
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val host = rest.substring(0, slash)
        if (host.isNotEmpty() && !host.equals("localhost", ignoreCase = true)) return null
        val decoded = try {
            URLDecoder.decode(rest.substring(slash).replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (decoded.any { it.isISOControl() }) return null
        if (decoded.length > 2 && decoded[0] == '/' && WINDOWS_PATH.matches(decoded.substring(1))) decoded.substring(1) else decoded
    } else link
    val absolute = (path.startsWith("/") && !path.startsWith("//")) || WINDOWS_PATH.matches(path)
    if (!absolute || path.endsWith("/") || path.endsWith("\\")) return null
    if (path.split('/', '\\').any { it == ".." }) return null
    return path
}
