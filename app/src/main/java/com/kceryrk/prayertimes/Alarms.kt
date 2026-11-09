package com.kceryrk.prayertimes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules adhan notifications and the permanent prayer-times notification.
 * Uses inexact alarms so they don't need the exact-alarm permission.
 */
object Alarms {
    const val CH_ADHAN = "adhan"
    const val CH_STATUS = "status"
    const val NOTIF_STATUS = 1001
    private const val REQ_ADHAN = 2001
    private const val REQ_TICK = 2002

    private fun small(text: CharSequence): CharSequence =
        SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.72f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    /** Monospace header row of prayer names over a row of times, so the columns line up. */
    private fun table(names: List<String>, times: List<String>): CharSequence {
        val widths = names.indices.map { maxOf(names[it].length, times[it].length) + 2 }
        val header = names.indices.joinToString("") { names[it].padEnd(widths[it]) }.trimEnd()
        val body = times.indices.joinToString("") { times[it].padEnd(widths[it]) }.trimEnd()
        return SpannableString("$header\n$body").apply {
            setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.72f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ADHAN, ctx.getString(R.string.channel_adhan), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, ctx.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW))
    }

    fun scheduleNext(ctx: Context) {
        val next = PrayerRepo.upcoming(ctx, adhanOnly = true) ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("prayer", next.prayer.name)
        val pi = PendingIntent.getBroadcast(ctx, REQ_ADHAN, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    /** Schedules a 1-minute tick used to refresh the permanent notification countdown. */
    fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_TICK, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 60_000, pi)
    }

    /** Shows or cancels the permanent notification, honouring the show/hide settings. */
    fun updateStatus(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!AppSettings.bool(ctx, "notif_on")) {
            nm.cancel(NOTIF_STATUS)
            return
        }
        val c = AppSettings.localized(ctx)
        val now = LocalDateTime.now()
        val next = PrayerRepo.upcoming(ctx, now)
        val today = PrayerRepo.today(ctx)

        val rows = today?.entries?.toList().orEmpty()
        val showTimes = AppSettings.bool(ctx, "notif_times")
        val nextLine = if (next != null) {
            val mins = Duration.between(now, next.at).toMinutes()
            val label = c.getString(next.prayer.labelRes)
            val time = if (mins < 60) c.getString(R.string.in_minutes, mins)
            else c.getString(R.string.in_time, mins / 60, mins % 60)
            "$label · $time"
        } else c.getString(R.string.no_times)
        val cells = rows.map { c.getString(it.key.labelRes) to it.value.toString() }
        val compactTimes = rows.joinToString(" · ") { it.value.toString() }
        val hasTimes = showTimes && rows.isNotEmpty()
        val bigContent: CharSequence = if (hasTimes) {
            table(cells.map { it.first }, cells.map { it.second })
        } else small(nextLine)

        val mins = next?.let { Duration.between(now, it.at).toMinutes() }
        val icon = if (mins != null && mins < 60) {
            IconCompat.createWithBitmap(minuteIcon(mins))
        } else null

        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(icon ?: IconCompat.createWithResource(ctx, R.drawable.ic_notification))
            .setContentTitle(if (hasTimes) small(nextLine) else null)
            .setContentText(if (hasTimes) small(compactTimes) else small(nextLine))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_STATUS, n)
        scheduleTick(ctx)
    }

    /** Renders the remaining minutes as a small white status-bar icon. */
    private fun minuteIcon(mins: Long): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        Canvas(bmp).drawText(mins.toString(), size / 2f, size / 2f + 20f, paint)
        return bmp
    }

    fun showAdhan(ctx: Context, prayerName: String) {
        val prayer = Prayer.valueOf(prayerName)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val c = AppSettings.localized(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.adhan_title, c.getString(prayer.labelRes)))
            .setContentText(c.getString(R.string.adhan_text))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(prayer.ordinal + 100, n)
    }

    private fun launchIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.showAdhan(context, intent.getStringExtra("prayer") ?: return)
        Alarms.scheduleNext(context)
        Alarms.updateStatus(context)
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.updateStatus(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Alarms.scheduleNext(context)
        Alarms.updateStatus(context)
        RefreshWorker.schedule(context)
    }
}
