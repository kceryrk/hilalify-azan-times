package com.kceryrk.prayertimes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules adhan notifications. Uses inexact alarms so they don't need the exact-alarm permission.
 * They may fire a few minutes late in Doze mode.
 */
object Alarms {
    const val CH_ADHAN = "adhan"
    const val CH_STATUS = "status"
    const val NOTIF_STATUS = 1001
    private const val REQ_ADHAN = 2001
    private const val REQ_TICK = 2002

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

    /** Schedules a 1-minute tick used to refresh the status bar countdown. */
    fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_TICK, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 60_000, pi)
    }

    fun updateStatus(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val now = LocalDateTime.now()
        val next = PrayerRepo.upcoming(ctx, now) ?: return nm.cancel(NOTIF_STATUS)
        val mins = java.time.Duration.between(now, next.at).toMinutes()
        if (mins > 60) return nm.cancel(NOTIF_STATUS)
        val text = ctx.getString(R.string.status_countdown, ctx.getString(next.prayer.labelRes), mins)
        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_STATUS, n)
        scheduleTick(ctx)
    }

    fun showAdhan(ctx: Context, prayerName: String) {
        val prayer = Prayer.valueOf(prayerName)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(ctx, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.adhan_title, ctx.getString(prayer.labelRes)))
            .setContentText(ctx.getString(R.string.adhan_text))
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
