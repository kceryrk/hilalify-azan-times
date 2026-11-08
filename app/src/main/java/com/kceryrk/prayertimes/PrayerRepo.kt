package com.kceryrk.prayertimes

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class Prayer(val apiKey: String, val labelRes: Int, val notify: Boolean) {
    FAJR("Fajr", R.string.prayer_fajr, true),
    SUNRISE("Sunrise", R.string.prayer_sunrise, false),
    DHUHR("Dhuhr", R.string.prayer_dhuhr, true),
    ASR("Asr", R.string.prayer_asr, true),
    MAGHRIB("Maghrib", R.string.prayer_maghrib, true),
    ISHA("Isha", R.string.prayer_isha, true),
}

data class Upcoming(val at: LocalDateTime, val prayer: Prayer)

/**
 * Fetches monthly prayer calendars from Aladhan and caches the raw JSON per location and month,
 * so the times still work offline. Method 13 is Diyanet (Turkey).
 */
object PrayerRepo {
    private const val METHOD = 13
    private const val API = "https://api.aladhan.com/v1"
    private const val PREFS = "prayer_times"
    private const val KEY_MODE = "mode"   // "gps" or "city"
    private const val KEY_GPS = "gps"     // "lat,lon"
    private const val KEY_CITY = "city"   // "city|country"
    private const val KEY_OK = "last_ok"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveGps(ctx: Context, lat: Double, lon: Double) {
        prefs(ctx).edit().putString(KEY_MODE, "gps").putString(KEY_GPS, "$lat,$lon").apply()
    }

    fun saveCity(ctx: Context, city: String, country: String) {
        prefs(ctx).edit().putString(KEY_MODE, "city").putString(KEY_CITY, "$city|$country").apply()
    }

    fun savedGps(ctx: Context): Pair<Double, Double>? {
        val (lat, lon) = prefs(ctx).getString(KEY_GPS, null)?.split(",") ?: return null
        return lat.toDouble() to lon.toDouble()
    }

    fun lastOk(ctx: Context) = prefs(ctx).getBoolean(KEY_OK, true)

    /** GPS when selected and known, otherwise the manual city. */
    private fun source(ctx: Context): String? {
        val p = prefs(ctx)
        val gps = p.getString(KEY_GPS, null)
        val city = p.getString(KEY_CITY, null)
        return if (p.getString(KEY_MODE, "gps") == "gps" && gps != null) "gps:$gps"
        else city?.let { "city:$it" } ?: gps?.let { "gps:$it" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun url(src: String, ym: YearMonth): String {
        val kind = src.substringBefore(':')
        val value = src.substringAfter(':')
        return if (kind == "gps") {
            val (lat, lon) = value.split(",")
            "$API/calendar/${ym.year}/${ym.monthValue}?latitude=$lat&longitude=$lon&method=$METHOD"
        } else {
            val (city, country) = value.split("|")
            "$API/calendarByCity/${ym.year}/${ym.monthValue}?city=${enc(city)}&country=${enc(country)}&method=$METHOD"
        }
    }

    private fun cacheKey(src: String, ym: YearMonth) = "cache_${src}_$ym"

    private fun download(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** Downloads this month and next month. Keeps the old cache when the network fails. */
    fun refresh(ctx: Context): Boolean {
        val src = source(ctx) ?: return false
        val now = YearMonth.now()
        var ok = true
        val edit = prefs(ctx).edit()
        for (ym in listOf(now, now.plusMonths(1))) {
            val body = download(url(src, ym))
            if (body != null) edit.putString(cacheKey(src, ym), body) else ok = false
        }
        edit.putBoolean(KEY_OK, ok).apply()
        return ok
    }

    private fun days(ctx: Context, src: String, ym: YearMonth): Map<LocalDate, Map<Prayer, LocalTime>> {
        val body = prefs(ctx).getString(cacheKey(src, ym), null) ?: return emptyMap()
        val data = JSONObject(body).getJSONArray("data")
        val out = HashMap<LocalDate, Map<Prayer, LocalTime>>()
        for (i in 0 until data.length()) {
            val timings = data.getJSONObject(i).getJSONObject("timings")
            out[ym.atDay(i + 1)] = Prayer.values().associateWith {
                LocalTime.parse(timings.getString(it.apiKey).substring(0, 5))
            }
        }
        return out
    }

    fun today(ctx: Context): Map<Prayer, LocalTime>? {
        val src = source(ctx) ?: return null
        return days(ctx, src, YearMonth.now())[LocalDate.now()]
    }

    /** Next prayer after [now]. With [adhanOnly], Sunrise is skipped. */
    fun upcoming(ctx: Context, now: LocalDateTime = LocalDateTime.now(), adhanOnly: Boolean = false): Upcoming? {
        val src = source(ctx) ?: return null
        val all = days(ctx, src, YearMonth.from(now)) + days(ctx, src, YearMonth.from(now.plusDays(1)))
        return all.flatMap { (date, times) ->
            times.filter { !adhanOnly || it.key.notify }.map { (prayer, time) -> Upcoming(LocalDateTime.of(date, time), prayer) }
        }.filter { it.at.isAfter(now) }.minByOrNull { it.at }
    }
}
