package io.github.lastnyx.hermesclient.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    private fun randomToken(bytes: Int) = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))

    fun verifier(): String = randomToken(32)

    fun state(): String = randomToken(24)

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
