package io.github.lastnyx.hermesclient.data.auth

import org.json.JSONObject
import java.util.Base64

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val provider: String,
    val userId: String,
)

/** Android-only in production (Keystore AES-GCM); fakes in JVM tests. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

interface TokenStorage {
    fun load(): String?
    fun save(text: String?)
}

/** One Token pair per gateway id, sealed as a single blob so nothing readable sits in storage. */
class TokenStore(private val storage: TokenStorage, private val cipher: SecretCipher) {
    @Synchronized
    fun load(gatewayId: String): TokenPair? = read().optJSONObject(gatewayId)?.let {
        TokenPair(
            it.getString("at"), it.getString("rt"), it.getLong("exp"),
            it.optString("provider"), it.optString("user"),
        )
    }

    @Synchronized
    fun save(gatewayId: String, pair: TokenPair) {
        val root = read()
        root.put(
            gatewayId,
            JSONObject().put("at", pair.accessToken).put("rt", pair.refreshToken).put("exp", pair.expiresAt)
                .put("provider", pair.provider).put("user", pair.userId),
        )
        write(root)
    }

    @Synchronized
    fun clear(gatewayId: String) {
        val root = read()
        root.remove(gatewayId)
        write(root)
    }

    private fun read(): JSONObject {
        val text = storage.load() ?: return JSONObject()
        return runCatching {
            JSONObject(String(cipher.decrypt(Base64.getDecoder().decode(text)), Charsets.UTF_8))
        }.getOrDefault(JSONObject())
    }

    private fun write(root: JSONObject) {
        val sealed = cipher.encrypt(root.toString().toByteArray(Charsets.UTF_8))
        storage.save(Base64.getEncoder().encodeToString(sealed))
    }
}
