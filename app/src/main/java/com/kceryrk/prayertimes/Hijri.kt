package com.kceryrk.prayertimes

import java.time.LocalDate
import java.time.chrono.HijrahDate

data class Hijri(val day: Int, val month: Int, val year: Int)

/** Hijri calendar helpers: localized date text and the next Islamic occasion. */
object HijriInfo {
    private val monthsEn = listOf(
        "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani", "Jumada al-awwal", "Jumada al-thani",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )
    private val monthsTr = listOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
        "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"
    )

    private class Occasion(val month: Int, val day: Int, val en: String, val tr: String)

    // Kandil nights are listed on the eve of the day, as the night begins at the previous sunset.
    private val occasions = listOf(
        Occasion(1, 1, "Islamic New Year", "Hicri Yılbaşı"),
        Occasion(1, 10, "Day of Ashura", "Aşure Günü"),
        Occasion(3, 11, "Mawlid Night", "Mevlid Kandili"),
        Occasion(7, 26, "Isra and Mi'raj Night", "Miraç Kandili"),
        Occasion(8, 14, "Bara'at Night", "Berat Kandili"),
        Occasion(9, 1, "Ramadan", "Ramazan"),
        Occasion(9, 26, "Laylat al-Qadr", "Kadir Gecesi"),
        Occasion(10, 1, "Eid al-Fitr", "Ramazan Bayramı"),
        Occasion(12, 10, "Eid al-Adha", "Kurban Bayramı")
    )

    fun label(tr: Boolean, h: Hijri): String {
        val names = if (tr) monthsTr else monthsEn
        return "${h.day} ${names.getOrElse(h.month - 1) { "" }} ${h.year}"
    }

    /** Next occasion on or after [date] as (name, localized "in N days"), or null when out of range. */
    fun nextOccasion(tr: Boolean, date: LocalDate, h: Hijri): Pair<String, String>? = try {
        // The API's Hijri date can differ by a day from the built-in calendar, so anchor on it.
        val shift = HijrahDate.of(h.year, h.month, h.day).toEpochDay() - date.toEpochDay()
        var best: Pair<Occasion, Long>? = null
        for (o in occasions) for (y in h.year..h.year + 1) {
            val days = HijrahDate.of(y, o.month, o.day).toEpochDay() - shift - date.toEpochDay()
            if (days >= 0 && (best == null || days < best.second)) best = o to days
        }
        best?.let { (o, days) ->
            val name = if (tr) o.tr else o.en
            val whenText = when (days) {
                0L -> if (tr) "bugün" else "today"
                1L -> if (tr) "yarın" else "tomorrow"
                else -> if (tr) "$days gün" else "in $days days"
            }
            name to whenText
        }
    } catch (_: Exception) {
        null
    }
}
