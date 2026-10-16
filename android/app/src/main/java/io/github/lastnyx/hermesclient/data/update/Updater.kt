package io.github.lastnyx.hermesclient.data.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val version: String, val apkUrl: String, val size: Long, val sha256: String, val pageUrl: String)

sealed interface UpdateOutcome {
    data object NonePublished : UpdateOutcome
    data object UpToDate : UpdateOutcome
    data class Available(val info: UpdateInfo) : UpdateOutcome
}

const val CANT_REACH_GITHUB = "Can't reach GitHub"
const val CHECKSUM_MISMATCH = "Download did not match its checksum"

/** All methods are blocking; call off the main thread. Failures carry a plain-sentence message. */
class Updater(
    private val http: OkHttpClient,
    private val apiBase: String,
    private val repo: String,
    private val currentVersion: String,
) {
    fun check(): Result<UpdateOutcome> = try {
        val request = Request.Builder().url("$apiBase/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> Result.success(UpdateOutcome.NonePublished)
                !response.isSuccessful -> Result.failure(IllegalStateException(CANT_REACH_GITHUB))
                else -> Result.success(outcome(JSONObject(response.body!!.string())))
            }
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException(CANT_REACH_GITHUB))
    }

    private fun outcome(json: JSONObject): UpdateOutcome {
        val version = json.getString("tag_name").removePrefix("v")
        if (compareVersions(version, currentVersion) <= 0) return UpdateOutcome.UpToDate
        val assets = json.getJSONArray("assets")
        val list = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = list.firstOrNull { it.getString("name").endsWith(".apk") } ?: return UpdateOutcome.UpToDate
        val digest = apk.optString("digest", "").removePrefix("sha256:").ifEmpty { null }
        val sha = digest ?: list.firstOrNull { it.getString("name") == apk.getString("name") + ".sha256" }
            ?.let { fetchText(it.getString("browser_download_url")).trim().split(Regex("\\s+")).first() }
            ?: throw IOException("no checksum")
        return UpdateOutcome.Available(
            UpdateInfo(version, apk.getString("browser_download_url"), apk.optLong("size"), sha, json.optString("html_url")),
        )
    }

    private fun fetchText(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            it.body!!.string()
        }

    /** Downloads to [dest] and checks the digest; a mismatch deletes the file and fails. */
    fun download(info: UpdateInfo, dest: File): Result<File> = try {
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body!!.byteStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        if (verify(dest, info.sha256)) Result.success(dest) else {
            dest.delete()
            Result.failure(IllegalStateException(CHECKSUM_MISMATCH))
        }
    } catch (e: Exception) {
        Result.failure(IllegalStateException(CANT_REACH_GITHUB))
    }

    companion object {
        fun verify(apk: File, sha256: String): Boolean {
            val md = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            return actual.equals(sha256.trim(), ignoreCase = true)
        }

        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }
            return parts(a).zip(parts(b)).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
        }
    }
}
