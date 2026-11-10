package com.kceryrk.prayertimes

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules adhan notifications and the permanent prayer-times notification.
 */
object Alarms {
    const val CH_ADHAN = "adhan"
    const val CH_STATUS = "status"
    const val NOTIF_STATUS = 1001
    private const val REQ_ADHAN = 2001
    private const val REQ_TICK = 2002
    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun small(text: CharSequence): CharSequence =
        SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.72f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    /** Expanded view: a divider, prayer names and times in equal-weight columns that fill the row at any font scale. */
    private fun timesView(ctx: Context, cells: List<Pair<String, String>>, currentIdx: Int, kerahatText: String?): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.notif_times)
        val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val labelColor = if (night) 0xB3FFFFFF.toInt() else 0xB3000000.toInt()
        val timeColor = if (night) Color.WHITE else Color.BLACK
        val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
        rv.setInt(R.id.notif_rule, "setBackgroundColor", if (night) 0x55FFFFFF else 0x55000000)
        if (kerahatText != null) {
            rv.setViewVisibility(R.id.notif_kerahat, View.VISIBLE)
            rv.setImageViewBitmap(R.id.notif_kerahat_hatch, Kerahat.hatch(width = 600, height = 90, color = if (night) 0x80FFFFFF.toInt() else 0x80000000.toInt()))
            rv.setTextViewText(R.id.notif_kerahat_text, kerahatText)
            rv.setTextColor(R.id.notif_kerahat_text, timeColor)
        } else {
            rv.setViewVisibility(R.id.notif_kerahat, View.GONE)
        }
        val nameIds = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5)
        val timeIds = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5)
        cells.take(6).forEachIndexed { i, (name, time) ->
            val isCurrent = i == currentIdx
            rv.setTextViewText(nameIds[i], name)
            rv.setTextColor(nameIds[i], if (isCurrent) accent else labelColor)
            rv.setTextViewText(timeIds[i], time)
            rv.setTextColor(timeIds[i], if (isCurrent) accent else timeColor)
        }
        return rv
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
        try {
            if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun exactAllowed(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun tickPendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_TICK, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Schedules a wake-up tick at the next minute boundary to refresh the notification and widgets.
     */
    fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = tickPendingIntent(ctx)
        val nextMinute = (System.currentTimeMillis() / 60_000 + 1) * 60_000
        try {
            if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
        }
    }

    fun buildStatusNotification(ctx: Context): Notification {
        val c = AppSettings.localized(ctx)
        val now = LocalDateTime.now()
        val next = PrayerRepo.upcoming(ctx, now)
        val today = PrayerRepo.today(ctx)

        val rows = today?.entries?.toList().orEmpty()
        val showTimes = AppSettings.bool(ctx, "notif_times")
        val city = PrayerRepo.locationLabel(ctx)?.substringBefore(',')?.trim()
        val header = if (next != null) {
            val label = c.getString(next.prayer.labelRes)
            val prayerLine = "$label: ${next.at.format(HM)}"
            // City goes last so the system ellipsis cuts the city, not the prayer time.
            if (city != null) "$prayerLine · $city" else prayerLine
        } else c.getString(R.string.no_times)
        val cells = rows.map { c.getString(it.key.labelRes) to it.value.toString() }
        val currentIdx = rows.indexOfLast { !it.value.isAfter(now.toLocalTime()) }
        val kerahat = Kerahat.active(ctx, now)
        val kerahatText = kerahat?.let { Kerahat.label(ctx, it.second, now) }
        val hasTimes = showTimes && rows.isNotEmpty()
        val bigContent: CharSequence = small(header)

        val mins = next?.let { Duration.between(now, it.at).toMinutes().coerceAtLeast(0) }
        val icon = if (mins != null && mins < 60) {
            IconCompat.createWithBitmap(minuteIcon(mins))
        } else null
        val nextMillis = next?.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?.takeIf { it > System.currentTimeMillis() }

        return NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(icon ?: IconCompat.createWithResource(ctx, R.drawable.ic_notification))
            .setContentTitle(if (hasTimes) small(header) else null)
            .setContentText(small(kerahatText ?: remainingText(c, mins, header)))
            .apply {
                if (nextMillis != null) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                    setWhen(nextMillis)
                } else {
                    setShowWhen(false)
                }
                if (hasTimes) setCustomBigContentView(timesView(ctx, cells, currentIdx, kerahatText))
                else setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(launchIntent(ctx))
            .setDeleteIntent(tickPendingIntent(ctx))
            .build()
    }

    /** Shows or cancels the permanent notification, honouring the show/hide settings. */
    fun updateStatus(ctx: Context) {
        createChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!AppSettings.bool(ctx, "notif_on")) {
            nm.cancel(NOTIF_STATUS)
            try {
                ctx.stopService(Intent(ctx, CountdownService::class.java))
            } catch (_: Exception) {}
            return
        }
        // Re-arm the minute tick first so a rendering failure can't stop the background refresh chain.
        scheduleTick(ctx)
        val n = buildStatusNotification(ctx)
        nm.notify(NOTIF_STATUS, n)
        CountdownService.ensureRunning(ctx)
    }

    /** Line under the header: "N min left" until the next prayer, falling back to the header text. */
    private fun remainingText(c: Context, mins: Long?, fallback: String): String {
        if (mins == null) return fallback
        return if (mins < 60) c.getString(R.string.countdown_m, mins.toInt())
        else c.getString(R.string.countdown_hm, (mins / 60).toInt(), (mins % 60).toInt())
    }

    /** Renders the remaining minutes as a small white status-bar icon. */
    private fun minuteIcon(mins: Long): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val text = mins.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 72f
        }
        // Shrink the glyphs so the number always fits inside the icon, whatever the digit count.
        val maxWidth = size * 0.9f
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize = 72f * maxWidth / measured
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        Canvas(bmp).drawText(text, size / 2f, baseline, paint)
        return bmp
    }

    fun showAdhan(ctx: Context, prayerName: String) {
        createChannels(ctx)
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

/**
 * Lightweight foreground service bound to [Alarms.NOTIF_STATUS] that listens to system [Intent.ACTION_TIME_TICK]
 * and screen-wake events so the live countdown notification and widgets update every minute even when the app is closed.
 */
class CountdownService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val minuteTicker = object : Runnable {
        override fun run() {
            if (!AppSettings.bool(this@CountdownService, "notif_on")) {
                stopSelf()
                return
            }
            Alarms.updateStatus(this@CountdownService)
            PrayerWidget.refreshAll(this@CountdownService)
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
            handler.postDelayed(this, delayMs)
        }
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Alarms.updateStatus(context)
            PrayerWidget.refreshAll(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, timeTickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
        handler.postDelayed(minuteTicker, delayMs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppSettings.bool(this, "notif_on")) {
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Alarms.createChannels(this)
        val notif = Alarms.buildStatusNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Alarms.NOTIF_STATUS, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Alarms.NOTIF_STATUS, notif)
            }
        } catch (_: Exception) {}
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (AppSettings.bool(this, "notif_on")) {
            Alarms.scheduleTick(this)
            Alarms.updateStatus(this)
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(minuteTicker)
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        private var running = false

        fun ensureRunning(ctx: Context) {
            if (running || !AppSettings.bool(ctx, "notif_on")) return
            try {
                ContextCompat.startForegroundService(ctx.applicationContext, Intent(ctx.applicationContext, CountdownService::class.java))
            } catch (_: Exception) {}
        }
    }
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
        PrayerWidget.refreshAll(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.scheduleNext(context)
        Alarms.updateStatus(context)
        PrayerWidget.refreshAll(context)
        RefreshWorker.schedule(context)
    }
}
