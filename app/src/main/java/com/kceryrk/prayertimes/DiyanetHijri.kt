package com.kceryrk.prayertimes

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Official Diyanet Hijri dates. Diyanet publishes one Turkey-wide calendar (the same Hijri date everywhere),
 * so a single page covers the whole year; it is cached and used when the Diyanet method is selected.
 */
object DiyanetHijri {
    private const val KEY = "diyanet_hijri"
    private const val PAGE = "https://namazvakitleri.diyanet.gov.tr/tr-TR/9206/ankara-icin-namaz-vakti"
    private val gregorian = listOf("ocak", "subat", "mart", "nisan", "mayis", "haziran", "temmuz", "agustos", "eylul", "ekim", "kasim", "aralik")
    private val hijriMonths = listOf(
        "muharrem", "safer", "rebiulevvel", "rebiulahir", "cemaziyelevvel", "cemaziyelahir",
        "recep", "saban", "ramazan", "sevval", "zilkade", "zilhicce"
    )
    private val row = Regex("""(\d{1,2}) (\S+) (\d{4}) \S+ (\d{1,2}) (\S+) (\d{4}) \d{2}:\d{2}""")

    private fun fold(s: String) = s.lowercase()
        .replace('ü', 'u').replace('ş', 's').replace('ı', 'i').replace('ö', 'o').replace('ç', 'c').replace('ğ', 'g')
        .replace("â", "a").replace("î", "i").replace("û", "u")

    /** Downloads and caches the year's Hijri dates. Keeps the old cache on failure. */
    fun refresh(ctx: Context) {
        try {
            val conn = URL(PAGE).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val html = try {
                if (conn.responseCode != 200) return
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
            val text = Html.fromHtml(html.replace(Regex("<[^>]+>"), " "), Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("\\s+"), " ")
            val out = JSONObject()
            for (m in row.findAll(text)) {
                val (d, gm, gy, hd, hm, hy) = m.destructured
                val gMonth = gregorian.indexOf(fold(gm)) + 1
                val hMonth = hijriMonths.indexOf(fold(hm)) + 1
                if (gMonth == 0 || hMonth == 0) continue
                out.put(LocalDate.of(gy.toInt(), gMonth, d.toInt()).toString(), "$hd|$hMonth|$hy")
            }
            if (out.length() > 0) PrayerRepo.prefs(ctx).edit().putString(KEY, out.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun lookup(ctx: Context, date: LocalDate): Hijri? = try {
        val v = JSONObject(PrayerRepo.prefs(ctx).getString(KEY, "{}")!!).optString(date.toString())
        if (v.isEmpty()) null else v.split("|").let { Hijri(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
    } catch (_: Exception) { null }
}
