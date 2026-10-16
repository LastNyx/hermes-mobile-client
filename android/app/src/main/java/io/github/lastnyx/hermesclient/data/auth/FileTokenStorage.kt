package io.github.lastnyx.hermesclient.data.auth

import android.content.Context
import java.io.File

/** noBackupFilesDir is excluded from backup, so sealed tokens never leave the device. */
class FileTokenStorage(context: Context) : TokenStorage {
    private val file = File(context.noBackupFilesDir, "tokens.bin")

    override fun load(): String? = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    override fun save(text: String?) {
        if (text == null) file.delete() else file.writeText(text)
    }
}
