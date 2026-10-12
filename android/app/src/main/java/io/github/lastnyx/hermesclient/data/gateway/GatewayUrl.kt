package io.github.lastnyx.hermesclient.data.gateway

import java.net.URI

object GatewayUrl {
    /** Desktop-style dedupe key: trimmed, no trailing slashes, lowercase scheme and host. Path prefix kept. */
    fun normalise(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || host == null) return null
        val hostPart = if (host.contains(':')) "[$host]" else host
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$scheme://$hostPart$port${uri.rawPath.orEmpty().trimEnd('/')}"
    }

    /** True when credentials would cross the network unencrypted to a host that is not loopback or Tailscale. */
    fun warnsCleartext(raw: String): Boolean {
        val normal = normalise(raw) ?: return false
        if (!normal.startsWith("http://")) return false
        val host = URI(normal).host
        if (host == "localhost" || host == "[::1]" || host == "::1") return false
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size == 4 && host.split('.').size == 4) {
            if (octets[0] == 127) return false
            if (octets[0] == 100 && octets[1] in 64..127) return false
        }
        return true
    }
}
