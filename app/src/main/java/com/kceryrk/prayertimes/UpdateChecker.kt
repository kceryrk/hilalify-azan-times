package com.kceryrk.prayertimes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. [apkUrl] is null when the release has no APK attached. */
data class UpdateInfo(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

object UpdateChecker {
    private const val LIST_URL = "https://api.github.com/repos/kceryrk/hilalify-azan-times/releases?per_page=30"
    private val sectionMarker = Regex("(?im)^##\\s*(English|T[üu]rk[çc]e|Turkish)\\s*$")

    /**
     * Picks the release notes for the device language. The release body holds an English section
     * and a Turkish section, each introduced by a "## English" or "## Türkçe" heading.
     * Without headings the whole body is returned.
     */
    fun notesFor(body: String, turkish: Boolean): String {
        val markers = sectionMarker.findAll(body).toList()
        if (markers.isEmpty()) return body.trim()
        val wanted = markers.firstOrNull { m -> (m.groupValues[1].first() == 'T' || m.groupValues[1] == "Turkish") == turkish }
            ?: markers.first()
        val index = markers.indexOf(wanted)
        val start = wanted.range.last + 1
        val end = markers.getOrNull(index + 1)?.range?.first ?: body.length
        return body.substring(start, end).trim()
    }

    /**
     * Returns the latest release if it is newer than the installed version, otherwise null.
     * The notes include every published release between the installed version and the latest,
     * newest first.
     */
    suspend fun check(ctx: Context): UpdateInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val conn = (URL(LIST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val releases = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                val installed = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: return@runCatching null
                val newer = (0 until releases.length())
                    .map { releases.getJSONObject(it) }
                    .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                    .map { it.getString("tag_name").removePrefix("v") to it }
                    .filter { compareVersions(it.first, installed) > 0 }
                    .sortedWith { a, b -> compareVersions(b.first, a.first) }
                if (newer.isEmpty()) return@runCatching null
                val (tag, latest) = newer.first()
                val apk = latest.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name").endsWith(".apk") }
                        ?.getString("browser_download_url")
                }
                val chosen = AppSettings.str(ctx, "language", "system")
                val turkish = (if (chosen == "system") java.util.Locale.getDefault().language else chosen) == "tr"
                val notes = if (newer.size == 1) {
                    notesFor(latest.optString("body"), turkish)
                } else {
                    newer.joinToString("\n\n") { (version, release) ->
                        "### $version\n" + notesFor(release.optString("body"), turkish)
                    }
                }
                UpdateInfo(tag, latest.getString("html_url"), apk, notes)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Downloads the APK into the app cache. [onProgress] receives 0..100, or -1 when the
     * server does not report a size.
     */
    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "Hilalify-update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.connect()
                val total = conn.contentLengthLong
                var written = 0L
                onProgress(if (total > 0) 0 else -1)
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            written += n
                            if (total > 0) onProgress((written * 100 / total).toInt())
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            file
        }

    /**
     * Opens the system installer for [apk]. Returns false when the user must first allow
     * installs from this app; the settings screen is opened in that case.
     */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    /** Compares dotted numeric versions such as "1.10" and "1.9". */
    internal fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
