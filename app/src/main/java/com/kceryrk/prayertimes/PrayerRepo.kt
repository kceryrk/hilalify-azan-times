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
import java.util.Locale

enum class Prayer(val apiKey: String, val labelRes: Int, val notify: Boolean) {
    FAJR("Fajr", R.string.prayer_fajr, true),
    SUNRISE("Sunrise", R.string.prayer_sunrise, false),
    DHUHR("Dhuhr", R.string.prayer_dhuhr, true),
    ASR("Asr", R.string.prayer_asr, true),
    MAGHRIB("Maghrib", R.string.prayer_maghrib, true),
    ISHA("Isha", R.string.prayer_isha, true),
}

data class Upcoming(val at: LocalDateTime, val prayer: Prayer)

/** A city from the geocoding search, with coordinates used for the prayer calendar. */
data class CityHit(val name: String, val country: String, val lat: Double, val lon: Double, val countryCode: String = "") {
    /** Country name in the given (in-app) language, falling back to the API's name. */
    fun countryName(locale: Locale): String =
        if (countryCode.length == 2) Locale("", countryCode).getDisplayCountry(locale).ifBlank { country } else country

    fun label(locale: Locale): String {
        val c = countryName(locale)
        return if (c.isBlank()) name else "$name, $c"
    }
}

/**
 * Fetches monthly prayer calendars from Aladhan and caches the raw JSON per location, method and month,
 * so the times still work offline. The calculation method comes from settings (default 13, Diyanet).
 */
object PrayerRepo {
    private const val API = "https://api.aladhan.com/v1"
    private const val PREFS = "prayer_times"
    private const val KEY_MODE = "mode"   // "gps" or "city"
    private const val KEY_GPS = "gps"     // "lat,lon"
    private const val KEY_CITY = "city"   // "name|country|lat|lon"
    private const val KEY_GPS_LABEL = "gps_label"
    private const val KEY_GPS_CC = "gps_cc"   // ISO country code of the GPS locality
    private const val KEY_OK = "last_ok"
    private const val KEY_CITIES = "saved_cities" // JSON list of CityHit, newest first

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveGps(ctx: Context, lat: Double, lon: Double, label: String? = null, countryCode: String = "") {
        prefs(ctx).edit().putString(KEY_MODE, "gps").putString(KEY_GPS, "$lat,$lon")
            .putString(KEY_GPS_LABEL, label).putString(KEY_GPS_CC, countryCode).apply()
    }

    /** Selects a city as the active location and remembers it in the saved list (newest first). */
    fun saveCity(ctx: Context, hit: CityHit) {
        val others = savedCities(ctx).filter { it.lat != hit.lat || it.lon != hit.lon }
        val arr = org.json.JSONArray()
        (listOf(hit) + others).take(20).forEach {
            arr.put(org.json.JSONObject().put("n", it.name).put("c", it.country).put("a", it.lat).put("o", it.lon).put("cc", it.countryCode))
        }
        prefs(ctx).edit().putString(KEY_MODE, "city")
            .putString(KEY_CITY, listOf(hit.name, hit.country, hit.lat, hit.lon, hit.countryCode).joinToString("|"))
            .putString(KEY_CITIES, arr.toString()).apply()
    }

    fun savedCities(ctx: Context): List<CityHit> {
        val arr = try { org.json.JSONArray(prefs(ctx).getString(KEY_CITIES, "[]")) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CityHit(o.getString("n"), o.getString("c"), o.getDouble("a"), o.getDouble("o"), o.optString("cc"))
        }
    }

    fun cityHit(ctx: Context): CityHit? {
        val parts = prefs(ctx).getString(KEY_CITY, null)?.split("|") ?: return null
        if (parts.size < 4) return null
        return CityHit(
            parts[0], parts[1],
            parts[2].toDoubleOrNull() ?: return null,
            parts[3].toDoubleOrNull() ?: return null,
            parts.getOrNull(4) ?: ""
        )
    }

    /** Name shown on the home page: the chosen city, or the reverse-geocoded GPS locality. */
    fun locationLabel(ctx: Context): String? =
        if (isGpsMode(ctx) && savedGps(ctx) != null) {
            val locale = AppSettings.localized(ctx).resources.configuration.locales[0]
            val city = prefs(ctx).getString(KEY_GPS_LABEL, null) ?: return null
            val cc = prefs(ctx).getString(KEY_GPS_CC, "") ?: ""
            CityHit(city, "", 0.0, 0.0, cc).label(locale)
        } else cityHit(ctx)?.label(AppSettings.localized(ctx).resources.configuration.locales[0])

    /** Autocomplete search through Open-Meteo's free geocoding API. */
    fun searchCities(query: String, lang: String): List<CityHit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=8&language=$lang&format=json"
        val body = download(url) ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            CityHit(
                r.getString("name"), r.optString("country"),
                r.getDouble("latitude"), r.getDouble("longitude"),
                r.optString("country_code")
            )
        }
    }

    fun setGpsMode(ctx: Context, gps: Boolean) {
        prefs(ctx).edit().putString(KEY_MODE, if (gps) "gps" else "city").apply()
    }

    fun isGpsMode(ctx: Context): Boolean = prefs(ctx).getString(KEY_MODE, "gps") == "gps"

    fun savedGps(ctx: Context): Pair<Double, Double>? {
        val (lat, lon) = prefs(ctx).getString(KEY_GPS, null)?.split(",") ?: return null
        return lat.toDouble() to lon.toDouble()
    }

    fun lastOk(ctx: Context) = prefs(ctx).getBoolean(KEY_OK, true)

    /** GPS when selected and known, otherwise the chosen city's coordinates. */
    private fun source(ctx: Context): String? {
        val gps = prefs(ctx).getString(KEY_GPS, null)
        val city = cityHit(ctx)
        return if (isGpsMode(ctx) && gps != null) "gps:$gps"
        else city?.let { "geo:${it.lat},${it.lon}" } ?: gps?.let { "gps:$it" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun url(src: String, ym: YearMonth, method: Int): String {
        val (lat, lon) = src.substringAfter(':').split(",")
        return "$API/calendar/${ym.year}/${ym.monthValue}?latitude=$lat&longitude=$lon&method=$method"
    }

    private fun cacheKey(ctx: Context, src: String, ym: YearMonth) = "cache_${src}_m${AppSettings.method(ctx)}_$ym"

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
        val method = AppSettings.method(ctx)
        var ok = true
        val edit = prefs(ctx).edit()
        for (ym in listOf(now, now.plusMonths(1))) {
            val body = download(url(src, ym, method))
            if (body != null) edit.putString(cacheKey(ctx, src, ym), body) else ok = false
        }
        edit.putBoolean(KEY_OK, ok).apply()
        return ok
    }

    private fun days(ctx: Context, src: String, ym: YearMonth): Map<LocalDate, Map<Prayer, LocalTime>> {
        val body = prefs(ctx).getString(cacheKey(ctx, src, ym), null) ?: return emptyMap()
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

    /** Next prayer after [now]. With [adhanOnly], Sunrise and prayers with alarms turned off are skipped. */
    fun upcoming(ctx: Context, now: LocalDateTime = LocalDateTime.now(), adhanOnly: Boolean = false): Upcoming? {
        val src = source(ctx) ?: return null
        val all = days(ctx, src, YearMonth.from(now)) + days(ctx, src, YearMonth.from(now.plusDays(1)))
        return all.flatMap { (date, times) ->
            times.filter { !adhanOnly || (it.key.notify && AppSettings.alarmOn(ctx, it.key)) }
                .map { (prayer, time) -> Upcoming(LocalDateTime.of(date, time), prayer) }
        }.filter { it.at.isAfter(now) }.minByOrNull { it.at }
    }
}
