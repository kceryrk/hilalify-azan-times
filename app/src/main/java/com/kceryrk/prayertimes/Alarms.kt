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
import android.graphics.drawable.Icon
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * Schedules adhan notifications and the permanent prayer-times notification.
 */
object Alarms {
    const val CH_ADHAN = "adhan"
    const val CH_STATUS = "status"
    const val CH_STATUS_LIVE = "status_live"
    const val CH_SAHUR = "sahur"
    const val CH_REMINDER = "reminder"
    const val NOTIF_STATUS = 1001
    private const val NOTIF_SAHUR = 1100
    private const val NOTIF_JUMUAH = 1101
    private const val NOTIF_QADA = 1102
    private const val NOTIF_KERAHAT = 1103
    private const val REQ_ADHAN = 2001
    private const val REQ_TICK = 2002
    private const val REQ_RESTORE = 2003
    private const val REQ_SAHUR = 2004
    private const val REQ_JUMUAH = 2005
    private const val REQ_QADA = 2006
    private const val REQ_KERAHAT = 2007
    private const val SAHUR_LEAD_MIN = 10L
    private const val JUMUAH_LEAD_MIN = 30L
    private const val QADA_HOUR_DEFAULT = 21
    private const val RAMADAN_MONTH = 9
    private const val PREF_SILENCE_PREV = "silence_prev"
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

    fun adhanChannelId(ctx: Context): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val mode = AppSettings.str(ctx, "notif_sound_mode", "default")
        val uriStr = AppSettings.str(ctx, "notif_sound_uri", "")
        val id = when (mode) {
            "silent" -> "adhan_silent"
            "custom" -> if (uriStr.isNotEmpty()) "adhan_custom_${uriStr.hashCode()}" else CH_ADHAN
            else -> CH_ADHAN
        }
        val ch = NotificationChannel(id, ctx.getString(R.string.channel_adhan), NotificationManager.IMPORTANCE_HIGH)
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        when (mode) {
            "silent" -> {
                ch.setSound(null, null)
                ch.enableVibration(false)
            }
            "custom" -> if (uriStr.isNotEmpty()) {
                ch.setSound(android.net.Uri.parse(uriStr), attrs)
            }
        }
        nm.createNotificationChannel(ch)
        return id
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ADHAN, ctx.getString(R.string.channel_adhan), NotificationManager.IMPORTANCE_HIGH))
        adhanChannelId(ctx)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, ctx.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 36) {
            // Live Updates / HyperOS 3 Super Island require IMPORTANCE_DEFAULT or higher; keep it silent.
            val live = NotificationChannel(CH_STATUS_LIVE, ctx.getString(R.string.channel_status), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(live)
        }
        nm.createNotificationChannel(NotificationChannel(CH_SAHUR, ctx.getString(R.string.channel_sahur), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, ctx.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun scheduleNext(ctx: Context) {
        scheduleSahur(ctx)
        scheduleJumuah(ctx)
        scheduleQada(ctx)
        scheduleKerahat(ctx)
        NextPrayerShortcut.update(ctx)
        val next = PrayerRepo.upcoming(ctx, adhanOnly = true) ?: return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("prayer", next.prayer.name)
        val pi = PendingIntent.getBroadcast(ctx, REQ_ADHAN, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        WearSync.pushNext(ctx, ctx.getString(next.prayer.labelRes), millis)
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
     * Schedules a non-wakeup tick at the next minute boundary to refresh the notification and widgets
     * while the screen is interactive (skips during AOD / screen-off to avoid waking the AOD).
     */
    fun scheduleTick(ctx: Context) {
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = tickPendingIntent(ctx)
        if (!pm.isInteractive) {
            am.cancel(pi)
            return
        }
        val nextMinute = (System.currentTimeMillis() / 60_000 + 1) * 60_000
        try {
            if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pi)
        }
    }

    fun buildStatusNotification(ctx: Context): Notification {
        val c = AppSettings.localized(ctx)
        val now = LocalDateTime.now()
        val next = PrayerRepo.upcoming(ctx, now)
        val today = PrayerRepo.today(ctx)
        val interactive = ctx.getSystemService(android.os.PowerManager::class.java).isInteractive
        val locked = ctx.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        val hideLock = AppSettings.bool(ctx, "notif_hide_lockscreen", def = false)
        val legacyNotif = AppSettings.bool(ctx, "legacy_notif", def = false)
        val islandLive = AppSettings.bool(ctx, "island_live", def = true)

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

        val realMoon = AppSettings.bool(ctx, "real_moon_icon", def = true)
        val mins = next?.let { Duration.between(now, it.at).toMinutes().coerceAtLeast(0) }
        // Split notification into resting (>= 60 min) and active countdown (< 60 min).
        // Resting notification is hidden on AOD / lock screen without needing a setting.
        val isCountdown = mins != null && mins < 60
        val hideRestingAod = !isCountdown && (!interactive || locked)
        val effectiveHideLock = hideLock || hideRestingAod
        val visCompat = if (effectiveHideLock) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC
        val visNative = if (effectiveHideLock) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PUBLIC

        // Only render the dynamic minute icon while the screen is interactive so AOD doesn't wake up every minute.
        val icon = when {
            interactive && isCountdown -> IconCompat.createWithBitmap(minuteIcon(mins!!))
            realMoon -> IconCompat.createWithBitmap(moonPhaseIcon())
            else -> null
        }
        val nextMillis = next?.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?.takeIf { it > System.currentTimeMillis() }

        val canLive = Build.VERSION.SDK_INT >= 36 &&
            ctx.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        if (canLive && nextMillis != null && islandLive && !legacyNotif && !hideRestingAod && !(hideLock && (!interactive || locked))) {
            val islandMode = AppSettings.str(ctx, "island_vakit", "current")
            val chip = if (!isCountdown && islandMode == "current" && currentIdx >= 0) {
                c.getString(rows[currentIdx].key.labelRes)
            } else {
                c.getString(next.prayer.labelRes)
            }
            val body = kerahatText ?: remainingText(c, mins, header)
            val smallIcon = when {
                interactive && isCountdown -> Icon.createWithBitmap(minuteIcon(mins!!))
                realMoon -> Icon.createWithBitmap(moonPhaseIcon())
                else -> Icon.createWithResource(ctx, R.drawable.ic_notification)
            }

            fun buildLive(useCustomView: Boolean): Notification {
                val b = Notification.Builder(ctx, CH_STATUS_LIVE)
                    .setSmallIcon(smallIcon)
                    .setContentTitle(small(header))
                    .setContentText(small(body))
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(nextMillis)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(visNative)
                    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                    .addExtras(android.os.Bundle().apply {
                        putBoolean("android.requestPromotedOngoing", true)
                    })
                    .setShortCriticalText(chip)
                    .setContentIntent(launchIntent(ctx))
                    .setDeleteIntent(tickPendingIntent(ctx))

                if (useCustomView && hasTimes) {
                    b.setCustomBigContentView(timesView(ctx, cells, currentIdx, kerahatText))
                } else if (hasTimes) {
                    val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
                    val dimName = 0xB3FFFFFF.toInt()
                    val dm = ctx.resources.displayMetrics
                    val density = dm.density
                    val fontScale = ctx.resources.configuration.fontScale.coerceAtLeast(1f)
                    val baseSize = 14f * density * fontScale
                    val availPx = (minOf(dm.widthPixels.toFloat(), 480f * density) - 112f * density).coerceAtLeast(180f * density)
                    val maxTableW = availPx * 0.78f

                    val count = minOf(6, cells.size)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = baseSize
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val minRowW = paint.measureText("00:00") * count + paint.measureText("  ") * (count - 1).coerceAtLeast(0)
                    val relScale = if (minRowW > 0f) (maxTableW / minRowW).coerceIn(0.55f, 0.66f) else 0.62f
                    paint.textSize = baseSize * relScale

                    // Build Row 2 (times) in system font with 2-space gaps between columns
                    val row2 = android.text.SpannableStringBuilder()
                    cells.take(count).forEachIndexed { i, (_, time) ->
                        if (i > 0) row2.append("  ")
                        val start = row2.length
                        row2.append(time)
                        if (i == currentIdx) {
                            row2.setSpan(android.text.style.ForegroundColorSpan(accent), start, row2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    row2.setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, row2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val totalRow2W = paint.measureText(row2, 0, row2.length)

                    // Build Row 1 (prayer names) with non-overlapping RelativeSizeSpan so OEM SystemUI honors the smaller size
                    val sb = android.text.SpannableStringBuilder()
                    if (kerahatText != null) {
                        sb.append(kerahatText).append("\n")
                    }
                    val tableStart = sb.length
                    val ruleW = paint.measureText("─").coerceAtLeast(1f)
                    val ruleCount = ((totalRow2W * 0.92f) / ruleW).toInt().coerceAtLeast(1)
                    val ruleStart = sb.length
                    sb.append("─".repeat(ruleCount))
                    sb.setSpan(
                        android.text.style.ForegroundColorSpan(0x55FFFFFF),
                        ruleStart,
                        sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(RelativeSizeSpan(relScale), ruleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                    val row1Start = sb.length
                    val nameScale = 0.72f
                    val timeW = paint.measureText("00:00")
                    val baseGapW = paint.measureText("  ")
                    val spW = paint.measureText(" ").coerceAtLeast(1f)
                    val thinW = paint.measureText("\u2009").coerceAtLeast(0.5f)
                    fun gapFor(prev: String, cur: String): String {
                        val target = (baseGapW + timeW - (paint.measureText(prev) + paint.measureText(cur)) * 0.5f)
                            .coerceIn(spW * 0.8f, spW * 2.5f)
                        val nSp = (target / spW).toInt().coerceAtLeast(1)
                        val rem = target - nSp * spW
                        return if (rem >= thinW * 0.5f) " ".repeat(nSp) + "\u2009" else " ".repeat(nSp)
                    }
                    fun buildRow1Gaps(list: List<String>): List<String> =
                        (1 until list.size).map { i -> gapFor(list[i - 1], list[i]) }
                    fun row1Width(list: List<String>, gaps: List<String>): Float =
                        list.indices.sumOf { i ->
                            paint.measureText(list[i]).toDouble() + if (i > 0) paint.measureText(gaps[i - 1]).toDouble() else 0.0
                        }.toFloat()

                    val names = cells.take(count).map { it.first }.toMutableList()
                    var gaps = buildRow1Gaps(names)
                    while (row1Width(names, gaps) > totalRow2W * 0.94f) {
                        val longestIdx = names.indices.filter { names[it].length > 6 }.maxByOrNull { names[it].length } ?: break
                        names[longestIdx] = names[longestIdx].take(5) + "."
                        gaps = buildRow1Gaps(names)
                    }
                    names.forEachIndexed { i, shortName ->
                        if (i > 0) sb.append(gaps[i - 1])
                        val start = sb.length
                        sb.append(shortName)
                        sb.setSpan(
                            android.text.style.ForegroundColorSpan(if (i == currentIdx) accent else dimName),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    sb.setSpan(RelativeSizeSpan(relScale * nameScale), row1Start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                    val row2Start = sb.length
                    sb.append(row2)
                    sb.setSpan(RelativeSizeSpan(relScale), row2Start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(
                        android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                        tableStart,
                        sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    b.setStyle(Notification.BigTextStyle().bigText(sb))
                } else {
                    b.setStyle(Notification.BigTextStyle().bigText(bigContent))
                }
                return b.build()
            }

            val custom = if (hasTimes) buildLive(useCustomView = true) else null
            return if (custom != null && custom.hasPromotableCharacteristics()) {
                custom
            } else {
                buildLive(useCustomView = false)
            }
        }

        return NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(icon ?: IconCompat.createWithResource(ctx, R.drawable.ic_notification))
            .setContentTitle(small(header))
            .setContentText(small(kerahatText ?: remainingText(c, mins, header)))
            .apply {
                if (nextMillis != null) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                    setWhen(nextMillis)
                } else {
                    setUsesChronometer(false)
                    setShowWhen(false)
                }
                if (hasTimes) setCustomBigContentView(timesView(ctx, cells, currentIdx, kerahatText))
                else setStyle(NotificationCompat.BigTextStyle().bigText(bigContent))
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(visCompat)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(launchIntent(ctx))
            .setDeleteIntent(tickPendingIntent(ctx))
            .build()
    }

    /** Shows or cancels the permanent notification, honouring the show/hide settings. */
    fun updateStatus(ctx: Context) {
        createChannels(ctx)
        NextPrayerShortcut.update(ctx)
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

    /** Renders the current astronomical synodic lunar phase as a monochrome notification icon. */
    private fun moonPhaseIcon(): Bitmap {
        val size = 96
        val cx = size / 2f
        val cy = size / 2f
        val r = 36f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val synodicSec = 29.530588853 * 86400.0
        val phase = (((System.currentTimeMillis() / 1000.0 - 947182440.0) / synodicSec) % 1.0 + 1.0) % 1.0
        val cosP = kotlin.math.cos(2.0 * Math.PI * phase).toFloat()
        val rx = kotlin.math.abs(cosP) * r
        val waxing = phase < 0.5

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 95
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(cx, cy, r, ringPaint)

        val circle = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
        val term = android.graphics.RectF(cx - rx, cy - r, cx + rx, cy + r)
        val path = android.graphics.Path()
        val outerSweep = if (waxing) 180f else -180f
        path.arcTo(circle, -90f, outerSweep, true)
        if (rx < 0.5f) {
            path.lineTo(cx, cy - r)
        } else {
            val termSweep = when {
                waxing && cosP > 0f -> -180f
                waxing -> 180f
                cosP < 0f -> -180f
                else -> 180f
            }
            path.arcTo(term, 90f, termSweep, false)
        }
        path.close()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawPath(path, fillPaint)
        return bmp
    }

    fun showAdhan(ctx: Context, prayerName: String) {
        createChannels(ctx)
        val prayer = Prayer.valueOf(prayerName)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val c = AppSettings.localized(ctx)
        val iqamaDelay = AppSettings.int(ctx, "iqama_delay", 0)
        val iqama = if (iqamaDelay > 0) {
            PrayerRepo.today(ctx)?.get(prayer)?.plusMinutes(iqamaDelay.toLong())?.format(HM)
        } else null
        val text = listOfNotNull(c.getString(R.string.adhan_text), iqama?.let { c.getString(R.string.iqama_at, it) })
            .joinToString(" · ")
        val hideLock = AppSettings.bool(ctx, "adhan_hide_lockscreen", def = false)
        val islandAdhan = Build.VERSION.SDK_INT >= 36 && AppSettings.bool(ctx, "island_adhan", def = true)
        val realMoon = AppSettings.bool(ctx, "real_moon_icon", def = true)
        val moonBmp = if (realMoon) moonPhaseIcon() else null
        val ch = adhanChannelId(ctx)
        val n = if (islandAdhan) {
            val label = c.getString(prayer.labelRes)
            val adhanIcon = if (moonBmp != null) Icon.createWithBitmap(moonBmp) else Icon.createWithResource(ctx, R.drawable.ic_notification)
            Notification.Builder(ctx, ch)
                .setSmallIcon(adhanIcon)
                .setContentTitle(c.getString(R.string.adhan_title, label))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(10 * 60_000L)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(if (hideLock) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PUBLIC)
                .addExtras(android.os.Bundle().apply {
                    putBoolean("android.requestPromotedOngoing", true)
                })
                .setShortCriticalText(label)
                .setContentIntent(launchIntent(ctx))
                .build()
        } else {
            val b = NotificationCompat.Builder(ctx, ch)
            if (moonBmp != null) b.setSmallIcon(IconCompat.createWithBitmap(moonBmp))
            else b.setSmallIcon(R.drawable.ic_notification)
            b.setContentTitle(c.getString(R.string.adhan_title, c.getString(prayer.labelRes)))
                .setContentText(text)
                .setVisibility(if (hideLock) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(ctx))
                .build()
        }
        nm.notify(prayer.ordinal + 100, n)
    }

    /**
     * Posts the adhan event to the user's webhook (Home Assistant, IFTTT, Node-RED...) as JSON.
     * Blocking, so call it off the main thread.
     */
    fun sendWebhook(ctx: Context, prayer: Prayer) {
        val url = AppSettings.str(ctx, "smart_home_url", "").trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val time = PrayerRepo.today(ctx)?.get(prayer)
        val delay = AppSettings.int(ctx, "iqama_delay", 0)
        val body = JSONObject().put("prayer", prayer.name)
        time?.let { body.put("time", it.format(HM)) }
        if (delay > 0 && time != null) body.put("iqama", time.plusMinutes(delay.toLong()).format(HM))
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        }
    }

    /** Friday's Dhuhr is the Cuma prayer, so it follows its own switch. */
    private fun silenceKey(prayer: Prayer, date: LocalDate = LocalDate.now()): String =
        if (prayer == Prayer.DHUHR && date.dayOfWeek == DayOfWeek.FRIDAY) "silence_cuma" else "silence_${prayer.name}"

    /**
     * Switches to Do Not Disturb at adhan time and schedules the restore alarm. The previous filter is kept in
     * settings so it survives the app being killed; the restore alarm is handled by [AlarmReceiver].
     */
    fun enterSilence(ctx: Context, prayer: Prayer) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val isCuma = prayer == Prayer.DHUHR && LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY
        if (isCuma) {
            if (!AppSettings.bool(ctx, "silence_cuma", true)) return
        } else {
            if (!AppSettings.bool(ctx, "silence_on", false)) return
            if (!AppSettings.bool(ctx, "silence_${prayer.name}", true)) return
        }
        val prefs = AppSettings.prefs(ctx)
        if (!prefs.contains(PREF_SILENCE_PREV)) {
            prefs.edit().putInt(PREF_SILENCE_PREV, nm.currentInterruptionFilter).apply()
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        val minutes = AppSettings.int(ctx, "silence_min", 20)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_RESTORE,
            Intent(ctx, AlarmReceiver::class.java).putExtra("restore_silence", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = System.currentTimeMillis() + minutes * 60_000L
        if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /**
     * During Ramadan (Hijri month 9), schedules a sahur reminder [SAHUR_LEAD_MIN] minutes before the next Fajr.
     */
    fun scheduleSahur(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_SAHUR, Intent(ctx, AlarmReceiver::class.java).putExtra("sahur", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!AppSettings.bool(ctx, "ramadan_mode", false)) return
        val now = LocalDateTime.now()
        val at = (0L..1L).asSequence().map { offset ->
            val date = now.toLocalDate().plusDays(offset)
            if (PrayerRepo.hijri(ctx, date)?.month != RAMADAN_MONTH) return@map null
            val fajr = PrayerRepo.day(ctx, date)?.get(Prayer.FAJR) ?: return@map null
            LocalDateTime.of(date, fajr).minusMinutes(SAHUR_LEAD_MIN)
        }.firstOrNull { it != null && it.isAfter(now) } ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    /**
     * Schedules the Jumu'ah reminder [JUMUAH_LEAD_MIN] minutes before the next Friday's Dhuhr.
     */
    fun scheduleJumuah(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_JUMUAH, Intent(ctx, AlarmReceiver::class.java).putExtra("jumuah", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!AppSettings.bool(ctx, "jumuah_reminder", false)) return
        val now = LocalDateTime.now()
        val at = (0L..7L).asSequence().map { offset ->
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek != DayOfWeek.FRIDAY) return@map null
            val dhuhr = PrayerRepo.day(ctx, date)?.get(Prayer.DHUHR) ?: return@map null
            LocalDateTime.of(date, dhuhr).minusMinutes(JUMUAH_LEAD_MIN)
        }.firstOrNull { it != null && it.isAfter(now) } ?: return
        setAlarm(am, at, pi)
    }

    /**
     * Schedules the daily qada reminder at the chosen hour: today if still ahead, otherwise tomorrow.
     */
    fun scheduleQada(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_QADA, Intent(ctx, AlarmReceiver::class.java).putExtra("qada", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!AppSettings.bool(ctx, "qada_reminder", true)) return
        val now = LocalDateTime.now()
        val hour = AppSettings.int(ctx, "qada_reminder_hour", QADA_HOUR_DEFAULT)
        var at = now.toLocalDate().atTime(hour, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        setAlarm(am, at, pi)
    }

    /** Schedules an alarm [kerahat_alarm_min] (5..50 min) before the next kerahat window begins. */
    fun scheduleKerahat(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_KERAHAT, Intent(ctx, AlarmReceiver::class.java).putExtra("kerahat_alarm", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!AppSettings.bool(ctx, "kerahat_alarm", true)) return
        val now = LocalDateTime.now()
        val lead = AppSettings.int(ctx, "kerahat_alarm_min", 15).coerceIn(5, 50).toLong()
        val nextAt = (0L..1L).asSequence().flatMap { offset ->
            Kerahat.windowsForDate(ctx, now.toLocalDate().plusDays(offset)).asSequence()
        }.map { (start, _) -> start.minusMinutes(lead) }
            .firstOrNull { it.isAfter(now) } ?: return
        setAlarm(am, nextAt, pi)
    }

    fun showKerahatAlarm(ctx: Context) {
        createChannels(ctx)
        val c = AppSettings.localized(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val lead = AppSettings.int(ctx, "kerahat_alarm_min", 15).coerceIn(5, 50)
        val hideLock = AppSettings.bool(ctx, "adhan_hide_lockscreen", def = false)
        val ch = adhanChannelId(ctx)
        val title = c.getString(R.string.kerahat_alarm_title)
        val text = c.getString(R.string.kerahat_alarm_text, lead)
        val n = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(if (hideLock) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_KERAHAT, n)
    }

    private fun setAlarm(am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (exactAllowed(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun qadaTotal(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences("qada", Context.MODE_PRIVATE)
        return Prayer.values().filter { it.notify }.sumOf { prefs.getInt(it.name, 0) }
    }

    fun showJumuah(ctx: Context) {
        createChannels(ctx)
        val c = AppSettings.localized(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.jumuah_title))
            .setContentText(c.getString(R.string.jumuah_text, JUMUAH_LEAD_MIN))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_JUMUAH, n)
    }

    /** Shows nothing when there are no missed prayers to make up. */
    fun showQada(ctx: Context) {
        val total = qadaTotal(ctx)
        if (total <= 0) return
        createChannels(ctx)
        val c = AppSettings.localized(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.qada_title))
            .setContentText(c.getString(R.string.qada_text, total))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_QADA, n)
    }

    fun showSahur(ctx: Context) {
        createChannels(ctx)
        val c = AppSettings.localized(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(ctx, CH_SAHUR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.sahur_title))
            .setContentText(c.getString(R.string.sahur_text))
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        nm.notify(NOTIF_SAHUR, n)
    }

    fun restoreSilence(ctx: Context) {
        val prefs = AppSettings.prefs(ctx)
        if (!prefs.contains(PREF_SILENCE_PREV)) return
        val prev = prefs.getInt(PREF_SILENCE_PREV, NotificationManager.INTERRUPTION_FILTER_ALL)
        prefs.edit().remove(PREF_SILENCE_PREV).apply()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(prev)
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
            val interactive = getSystemService(android.os.PowerManager::class.java).isInteractive
            if (!interactive) return
            Alarms.updateStatus(this@CountdownService)
            PrayerWidget.refreshAll(this@CountdownService)
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
            handler.postDelayed(this, delayMs)
        }
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                handler.removeCallbacks(minuteTicker)
                Alarms.updateStatus(context)
                return
            }
            val interactive = context.getSystemService(android.os.PowerManager::class.java).isInteractive
            if (!interactive) return
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
                handler.removeCallbacks(minuteTicker)
                val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
                handler.postDelayed(minuteTicker, delayMs)
            }
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
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, timeTickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (getSystemService(android.os.PowerManager::class.java).isInteractive) {
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
            handler.postDelayed(minuteTicker, delayMs)
        }
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
        if (intent.getBooleanExtra("restore_silence", false)) {
            Alarms.restoreSilence(context)
            return
        }
        if (intent.getBooleanExtra("sahur", false)) {
            Alarms.showSahur(context)
            Alarms.scheduleSahur(context)
            return
        }
        if (intent.getBooleanExtra("jumuah", false)) {
            Alarms.showJumuah(context)
            Alarms.scheduleJumuah(context)
            return
        }
        if (intent.getBooleanExtra("qada", false)) {
            Alarms.showQada(context)
            Alarms.scheduleQada(context)
            return
        }
        if (intent.getBooleanExtra("kerahat_alarm", false)) {
            Alarms.showKerahatAlarm(context)
            Alarms.scheduleKerahat(context)
            return
        }
        val prayerName = intent.getStringExtra("prayer") ?: return
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
        Alarms.enterSilence(context, prayer)
        Alarms.showAdhan(context, prayerName)
        Alarms.scheduleNext(context)
        Alarms.updateStatus(context)
        val pending = goAsync()
        Thread {
            try {
                Alarms.sendWebhook(context, prayer)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.getSystemService(android.os.PowerManager::class.java).isInteractive) return
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
