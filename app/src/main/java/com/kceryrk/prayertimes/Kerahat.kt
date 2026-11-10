package com.kceryrk.prayertimes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Makruh (kerahat) time windows, derived from today's prayer times. */
object Kerahat {
    /**
     * The kerahat window containing [now], as (start, end), or null when the setting is off or no window is active.
     * Windows: N min after sunrise, N min before Dhuhr, and N min before Maghrib, where N is the "kerahat_min" setting.
     */
    fun active(ctx: Context, now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, LocalDateTime>? =
        windows(ctx).firstOrNull { now >= it.first && now < it.second }

    /** Today's kerahat windows, empty when the setting is off or times are unavailable. */
    fun windows(ctx: Context): List<Pair<LocalDateTime, LocalDateTime>> {
        if (!AppSettings.bool(ctx, "kerahat", true)) return emptyList()
        val t = PrayerRepo.today(ctx) ?: return emptyList()
        val day = LocalDate.now()
        val sunrise = t[Prayer.SUNRISE] ?: return emptyList()
        val dhuhr = t[Prayer.DHUHR] ?: return emptyList()
        val maghrib = t[Prayer.MAGHRIB] ?: return emptyList()
        val window = AppSettings.int(ctx, "kerahat_min", 45).coerceIn(20, 60).toLong()
        return listOf(
            day.atTime(sunrise) to day.atTime(sunrise).plusMinutes(window),
            day.atTime(dhuhr).minusMinutes(window) to day.atTime(dhuhr),
            day.atTime(maghrib).minusMinutes(window) to day.atTime(maghrib),
        )
    }

    /** Short status such as "Kerahat vakti · 12 dk kaldı", with the time left until the window ends. */
    fun label(ctx: Context, end: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val c = AppSettings.localized(ctx)
        val mins = Duration.between(now, end).toMinutes().coerceAtLeast(0)
        val left = if (mins >= 60) c.getString(R.string.countdown_hm, mins / 60, mins % 60)
        else c.getString(R.string.countdown_m, mins)
        return "${c.getString(R.string.kerahat_active)} · $left"
    }

    /** A box-filling pattern of 45° diagonal lines, drawn once per use. */
    fun hatch(width: Int = 300, height: Int = 60, color: Int = Color.argb(0x80, 0xFF, 0xFF, 0xFF)): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
        }
        val step = 10f
        var x = -height.toFloat()
        val canvas = Canvas(bmp)
        while (x < width) {
            canvas.drawLine(x, height.toFloat(), x + height, 0f, paint)
            x += step
        }
        return bmp
    }
}
