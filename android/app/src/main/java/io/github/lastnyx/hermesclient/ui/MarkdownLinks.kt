package io.github.lastnyx.hermesclient.ui

private val allowedPrefixes = listOf("http://", "https://", "mailto:")

/** Only web and mail links may open; anything else a reply carries is ignored. */
fun isAllowedLink(uri: String): Boolean {
    val link = uri.trim()
    if (link.any { it.isISOControl() }) return false
    return allowedPrefixes.any { link.startsWith(it, ignoreCase = true) }
}
