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

    /**
     * Searches Open-Meteo (prefix match in the device language) and Photon (typo-tolerant OSM names in any
     * language, e.g. "bruksel" or "bruxelles" find Brussels). Results are merged and de-duplicated by place.
     */
    fun searchCities(query: String, lang: String): List<CityHit> {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.isEmpty()) return emptyList()
        val merged = openMeteoSearch(q, lang).toMutableList()
        photonSearch(q, if (lang in PHOTON_LANGS) lang else "en").forEach { hit ->
            if (merged.none { samePlace(it, hit) }) merged += hit
        }
        return merged.take(10)
    }

    private val PHOTON_LANGS = setOf("en", "de", "fr", "it")

    private fun samePlace(a: CityHit, b: CityHit): Boolean =
        a.name.equals(b.name, ignoreCase = true) &&
            (a.countryCode.equals(b.countryCode, ignoreCase = true) || a.country.equals(b.country, ignoreCase = true) ||
                (kotlin.math.abs(a.lat - b.lat) < 0.05 && kotlin.math.abs(a.lon - b.lon) < 0.05))

    private fun openMeteoSearch(q: String, lang: String): List<CityHit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=8&language=$lang&format=json"
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

    private fun photonSearch(q: String, lang: String): List<CityHit> {
        val url = "https://photon.komoot.io/api/?q=${enc(q)}&limit=8&lang=$lang" +
            "&osm_tag=place:city&osm_tag=place:town&osm_tag=place:village"
        val body = download(url) ?: return emptyList()
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val name = p.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
            CityHit(name, p.optString("country"), coords.getDouble(1), coords.getDouble(0), p.optString("countrycode"))
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
        val base = "$API/calendar/${ym.year}/${ym.monthValue}?latitude=$lat&longitude=$lon"
        if (method != 13) return "$base&method=$method"
        // Apply Diyanet's official angle and temkin adjustments (16° Isha + wider horizon dip >= 45° latitude).
        val highLat = kotlin.math.abs(lat.toDoubleOrNull() ?: 0.0) >= 45.0
        return if (highLat) {
            "$base&method=99&methodSettings=18,null,16&tune=0,0,-8,5,3,9,9,1,0"
        } else {
            "$base&method=13&tune=0,0,-7,5,4,8,8,1,0"
        }
    }

    private fun cacheKey(ctx: Context, src: String, ym: YearMonth) = "cache_v2_${src}_m${AppSettings.method(ctx)}_$ym"

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

    /** Downloads this month immediately and warms next month + Diyanet Hijri in the background. */
    fun refresh(ctx: Context): Boolean {
        val src = source(ctx) ?: return false
        val now = YearMonth.now()
        val method = AppSettings.method(ctx)
        val body = download(url(src, now, method))
        val ok = body != null
        prefs(ctx).edit().apply {
            if (body != null) putString(cacheKey(ctx, src, now), body)
            putBoolean(KEY_OK, ok)
        }.apply()
        val appCtx = ctx.applicationContext
        Thread {
            val nextYm = now.plusMonths(1)
            download(url(src, nextYm, method))?.let { nextBody ->
                prefs(appCtx).edit().putString(cacheKey(appCtx, src, nextYm), nextBody).apply()
            }
            if (method == 13) DiyanetHijri.refresh(appCtx)
        }.start()
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

    /** Cached prayer times for any day in the downloaded months, or null when not cached. */
    fun day(ctx: Context, date: LocalDate): Map<Prayer, LocalTime>? {
        val src = source(ctx) ?: return null
        return try { days(ctx, src, YearMonth.from(date))[date] } catch (_: Exception) { null }
    }

    /** Hijri date from the cached calendar, or null when the day isn't cached. */
    fun hijri(ctx: Context, date: LocalDate): Hijri? {
        if (AppSettings.method(ctx) == 13) DiyanetHijri.lookup(ctx, date)?.let { return it }
        val src = source(ctx) ?: return null
        val body = prefs(ctx).getString(cacheKey(ctx, src, YearMonth.from(date)), null) ?: return null
        return try {
            val h = JSONObject(body).getJSONArray("data").getJSONObject(date.dayOfMonth - 1)
                .getJSONObject("date").getJSONObject("hijri")
            Hijri(h.getString("day").toInt(), h.getJSONObject("month").getInt("number"), h.getString("year").toInt())
        } catch (_: Exception) { null }
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
