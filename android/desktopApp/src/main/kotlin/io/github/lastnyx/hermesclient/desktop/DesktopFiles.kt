package io.github.lastnyx.hermesclient.desktop

import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.gateway.GatewayStorage
import io.github.lastnyx.hermesclient.data.profiles.ProfileStorage
import io.github.lastnyx.hermesclient.data.settings.DraftStorage
import io.github.lastnyx.hermesclient.data.settings.SessionViewStorage
import io.github.lastnyx.hermesclient.data.settings.TextScaleStorage
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import org.json.JSONObject

/** The protection on desktop is the owner-only file permission, not the cipher. */
object PlainCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain
    override fun decrypt(sealed: ByteArray): ByteArray = sealed
}

fun dataFolder(osName: String, env: Map<String, String>, home: String): Path =
    if (osName.startsWith("Windows")) {
        val appData = env["APPDATA"]?.takeIf { it.isNotBlank() } ?: Path.of(home, "AppData", "Roaming").toString()
        Path.of(appData, "HermesClient")
    } else {
        val base = env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: Path.of(home, ".local", "share")
        base.resolve("hermes-client")
    }

class DesktopFiles(dir: Path) {
    private val settings = JsonKeyValues(TextFile(dir.resolve("settings.json")))
    private val draftValues = JsonKeyValues(TextFile(dir.resolve("drafts.json")))
    private val gatewayFile = TextFile(dir.resolve("gateways.json"))
    private val tokenFile = TextFile(dir.resolve("tokens.bin"))

    val gateways: GatewayStorage = object : GatewayStorage {
        override fun load(): String? = gatewayFile.read()
        override fun save(text: String) = gatewayFile.write(text)
    }

    val tokens: TokenStorage = object : TokenStorage {
        override fun load(): String? = tokenFile.read()
        override fun save(text: String?) = if (text == null) tokenFile.delete() else tokenFile.write(text)
    }

    val profiles: ProfileStorage = object : ProfileStorage {
        override fun load(gatewayId: String): String? = settings.get("profile_$gatewayId")
        override fun save(gatewayId: String, name: String?) = settings.put("profile_$gatewayId", name)
    }

    val drafts: DraftStorage = object : DraftStorage {
        override fun load(key: String): String? = draftValues.get(key)
        override fun save(key: String, text: String) = draftValues.put(key, text)
        override fun clear(key: String) = draftValues.put(key, null)
    }

    val sessionView: SessionViewStorage = object : SessionViewStorage {
        override fun load(name: String): String? = settings.get(name)
        override fun save(name: String, key: String) = settings.put(name, key)
    }

    val textScale: TextScaleStorage = object : TextScaleStorage {
        override fun load(): String? = settings.get("text_scale")
        override fun save(key: String) = settings.put("text_scale", key)
    }
}

private class JsonKeyValues(private val file: TextFile) {
    @Synchronized
    fun get(key: String): String? = read().let { if (it.has(key)) it.optString(key) else null }

    @Synchronized
    fun put(key: String, value: String?) {
        val json = read()
        if (value == null) json.remove(key) else json.put(key, value)
        file.write(json.toString())
    }

    private fun read(): JSONObject = file.read()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
}

private class TextFile(private val path: Path) {
    fun read(): String? = if (Files.exists(path)) runCatching { Files.readString(path) }.getOrNull() else null

    fun delete() {
        Files.deleteIfExists(path)
    }

    /** Temp file is created owner-only, so the secret is never readable by others, then moved over the old file in one step. */
    fun write(text: String) {
        val dir = path.parent
        Files.createDirectories(dir)
        val tmp = dir.resolve(path.fileName.toString() + ".tmp")
        Files.deleteIfExists(tmp)
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        }
        try {
            Files.writeString(tmp, text)
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
