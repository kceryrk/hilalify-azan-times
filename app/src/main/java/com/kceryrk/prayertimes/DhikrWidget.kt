package com.kceryrk.prayertimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews

private const val ZIKIR_PREFS = "zikir"
private const val ACTION_DHIKR_INC = "com.kceryrk.prayertimes.DHIKR_INC"
private const val ACTION_DHIKR_RESET = "com.kceryrk.prayertimes.DHIKR_RESET"
private const val DEFAULT_TARGET = 33
private const val DHIKR_SIDE_DP = 180
private const val DONE_GREEN = 0xFF8BE3A0.toInt()

/** Tap-to-count dhikr widget. Shares the "zikir" prefs with the Tools zikir screen. */
class DhikrWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, dhikrViews(context, manager.getAppWidgetOptions(it))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        manager.updateAppWidget(id, dhikrViews(context, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(ZIKIR_PREFS, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_DHIKR_INC -> {
                val target = prefs.getInt("target", DEFAULT_TARGET)
                val next = prefs.getInt("count", 0) + 1
                prefs.edit().putInt("count", next).apply()
                playDhikrHaptic(context, complete = next == target)
            }
            ACTION_DHIKR_RESET -> prefs.edit().putInt("count", 0).apply()
            else -> return
        }
        refreshDhikrWidgets(context)
    }
}

fun refreshDhikrWidgets(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    mgr.getAppWidgetIds(ComponentName(ctx, DhikrWidget::class.java))
        .forEach { mgr.updateAppWidget(it, dhikrViews(ctx, mgr.getAppWidgetOptions(it))) }
}

/** Tap tick, or a rising thud when the target is reached. Uses crisp primitives when the device supports them. */
@Suppress("MissingPermission")
fun playDhikrHaptic(ctx: Context, complete: Boolean) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(premiumDhikrEffect(vibrator, complete))
}

private fun premiumDhikrEffect(vibrator: Vibrator, complete: Boolean): VibrationEffect {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val supported = if (complete) {
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )
        } else {
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        }
        if (supported) {
            val composition = VibrationEffect.startComposition()
            if (complete) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 30)
            } else {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
            }
            return composition.compose()
        }
    }
    // Fallback: amplitude-shaped waveform, or a plain pulse without amplitude control.
    val amplitudes = vibrator.hasAmplitudeControl()
    return if (complete) {
        val timings = longArrayOf(0, 25, 45, 70)
        if (amplitudes) VibrationEffect.createWaveform(timings, intArrayOf(0, 90, 0, 255), -1)
        else VibrationEffect.createWaveform(timings, -1)
    } else {
        if (amplitudes) VibrationEffect.createOneShot(14, 150)
        else VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE)
    }
}

private fun dhikrViews(ctx: Context, opts: android.os.Bundle = android.os.Bundle.EMPTY): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_dhikr)
    val tap = Intent(ctx, DhikrWidget::class.java).setAction(ACTION_DHIKR_INC)
    val pending = PendingIntent.getBroadcast(
        ctx, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    rv.setOnClickPendingIntent(R.id.dh_root, pending)
    rv.setOnClickPendingIntent(R.id.dh_bg, pending)

    val reset = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, DhikrWidget::class.java).setAction(ACTION_DHIKR_RESET),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val prefs = ctx.getSharedPreferences(ZIKIR_PREFS, Context.MODE_PRIVATE)
    val count = prefs.getInt("count", 0)
    val target = prefs.getInt("target", DEFAULT_TARGET)
    val done = count >= target
    val opacity = AppSettings.int(ctx, "widget_opacity", 100)
    val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
    val fill = AppSettings.bool(ctx, "widget_fill_square", def = false)
    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: DHIKR_SIDE_DP
    val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: DHIKR_SIDE_DP
    rv.setBoolean(R.id.dh_bg, "setAdjustViewBounds", !fill)
    val bg = PrayerWidget.background(
        ctx, if (fill) minW else DHIKR_SIDE_DP, if (fill) minH else DHIKR_SIDE_DP, opacity, true, accent, fixedDarkBlue = true
    ).apply {
        density = maxOf(1, ctx.resources.displayMetrics.densityDpi / 4)
    }
    rv.setImageViewBitmap(R.id.dh_bg, bg)
    rv.setTextViewText(
        R.id.dh_label,
        ctx.getString(if (done) R.string.widget_dhikr_done else R.string.widget_dhikr_label)
    )
    rv.setTextColor(R.id.dh_label, if (done) DONE_GREEN else Color.argb(0xB3, 255, 255, 255))
    rv.setTextViewText(R.id.dh_count, ctx.getString(R.string.dhikr_count_format, count, target))
    rv.setTextColor(R.id.dh_count, if (done) DONE_GREEN else Color.WHITE)
    rv.setOnClickPendingIntent(R.id.dh_reset, reset)
    rv.setViewVisibility(R.id.dh_reset, if (done) View.VISIBLE else View.GONE)
    return rv
}

