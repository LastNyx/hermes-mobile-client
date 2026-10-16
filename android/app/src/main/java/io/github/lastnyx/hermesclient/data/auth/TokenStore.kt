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

const val SAVED_SIGN_IN_UNREADABLE = "Saved sign-in could not be read; sign in again"

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
    fun load(gatewayId: String): TokenPair? = read()?.optJSONObject(gatewayId)?.let {
        TokenPair(
            it.getString("at"), it.getString("rt"), it.getLong("exp"),
            it.optString("provider"), it.optString("user"),
        )
    }

    /** True only when storage holds data that could not be read; no data is not unreadable. */
    @Synchronized
    fun isUnreadable(): Boolean = read() == null

    @Synchronized
    fun save(gatewayId: String, pair: TokenPair) {
        val root = read() ?: JSONObject()
        root.put(
            gatewayId,
            JSONObject().put("at", pair.accessToken).put("rt", pair.refreshToken).put("exp", pair.expiresAt)
                .put("provider", pair.provider).put("user", pair.userId),
        )
        write(root)
    }

    @Synchronized
    fun clear(gatewayId: String) {
        // An unreadable blob is kept for the next save, never rewritten here.
        val root = read() ?: return
        root.remove(gatewayId)
        write(root)
    }

    /** Empty object when there is no data, null when data exists but cannot be read. */
    private fun read(): JSONObject? = runCatching {
        val text = storage.load() ?: return@runCatching JSONObject()
        JSONObject(String(cipher.decrypt(Base64.getDecoder().decode(text)), Charsets.UTF_8))
    }.getOrNull()

    private fun write(root: JSONObject) {
        val sealed = cipher.encrypt(root.toString().toByteArray(Charsets.UTF_8))
        storage.save(Base64.getEncoder().encodeToString(sealed))
    }
}
