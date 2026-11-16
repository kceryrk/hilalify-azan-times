package com.kceryrk.prayertimes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import java.time.Duration
import java.time.LocalDateTime

/** Launcher long-press shortcut that shows the minutes left until the next prayer. */
object NextPrayerShortcut {
    private const val ID = "next_prayer"
    private var lastLabel: String? = null

    fun update(ctx: Context) {
        val c = AppSettings.localized(ctx)
        val now = LocalDateTime.now()
        val next = PrayerRepo.upcoming(ctx, now)
        val label = if (next != null) {
            val mins = Duration.between(now, next.at).toMinutes()
            c.getString(R.string.shortcut_next_label, c.getString(next.prayer.labelRes), PrayerWidget.countdown(c, mins))
        } else {
            ctx.getString(R.string.app_name)
        }
        if (label == lastLabel) return
        lastLabel = label

        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("hilalify://next-prayer"))
        val shortcut = ShortcutInfoCompat.Builder(ctx, ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIntent(intent)
            .build()
        try {
            ShortcutManagerCompat.setDynamicShortcuts(ctx, listOf(shortcut))
        } catch (_: Exception) {
            lastLabel = null
        }
    }
}
