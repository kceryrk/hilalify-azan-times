package com.kceryrk.prayertimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Resizable prayer widget. Content adapts to the widget size and to the settings. */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, views(context, newOptions))
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, views(ctx, mgr.getAppWidgetOptions(it))) }
            refreshOptionWidgets(ctx)
        }

        private fun Int.dp(ctx: Context): Int = (this * ctx.resources.displayMetrics.density).toInt()

        /** Hours are omitted when zero, so the countdown reads "in 26m" rather than "in 0h 26m". */
        internal fun countdown(c: Context, mins: Long): String {
            val m = mins.coerceAtLeast(0)
            return if (m >= 60) c.getString(R.string.countdown_hm, m / 60, m % 60) else c.getString(R.string.countdown_m, m)
        }

        internal fun homePendingIntent(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_home", true)
            }
            return PendingIntent.getActivity(
                ctx, 3001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun views(ctx: Context, opts: Bundle): RemoteViews {
            val c = AppSettings.localized(ctx)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val large = minH >= 120
            val rv = RemoteViews(ctx.packageName, if (large) R.layout.widget_prayer_large else R.layout.widget_prayer)
            val launch = homePendingIntent(ctx)
            rv.setOnClickPendingIntent(R.id.widget_root, launch)
            rv.setOnClickPendingIntent(R.id.widget_bg_img, launch)
            val opacity = AppSettings.int(ctx, "widget_opacity", 100)
            val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            // Fixed dark blue with white text, matching the option widgets, so it reads the same in light and dark mode.
            val fg = Color.WHITE
            val dim = 0xB3FFFFFF.toInt()
            val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            // The background is its own layer, so opacity fades only the panel and never the text.
            rv.setImageViewBitmap(R.id.widget_bg_img, background(ctx, minW, minH, opacity, night, accent, fixedDarkBlue = true))

            val now = LocalDateTime.now()
            val next = PrayerRepo.upcoming(ctx, now)
            val kerahat = Kerahat.active(ctx, now)
            val compact = !large && (minH in 1..75 || minH == 0)
            val showNext = AppSettings.bool(ctx, "widget_next") && (large || kerahat == null)
            val showTimes = AppSettings.bool(ctx, "widget_times")
            val padH = (if (large) 14 else 6).dp(ctx)
            val padV = (if (large) 14 else if (compact) 4 else 6).dp(ctx)
            rv.setViewPadding(R.id.widget_root, padH, padV, padH, padV)
            rv.setViewVisibility(R.id.widget_next, visibleIf(showNext))
            rv.setViewVisibility(R.id.widget_grid, visibleIf(showTimes))

            rv.setViewVisibility(R.id.widget_kerahat, visibleIf(kerahat != null))
            if (kerahat != null) {
                rv.setImageViewBitmap(R.id.widget_kerahat_hatch, Kerahat.hatch(width = 600, height = 60, color = dim))
                rv.setTextViewText(R.id.widget_kerahat_text, Kerahat.label(ctx, kerahat.second, now))
                rv.setTextColor(R.id.widget_kerahat_text, fg)
                rv.setTextViewTextSize(R.id.widget_kerahat_text, TypedValue.COMPLEX_UNIT_SP, if (large) 15f else 11f)
            }

            if (next != null) {
                val mins = Duration.between(now, next.at).toMinutes()
                rv.setTextViewText(
                    R.id.widget_next,
                    "${c.getString(R.string.next_prayer, c.getString(next.prayer.labelRes))} · ${countdown(c, mins)}"
                )
            } else {
                rv.setTextViewText(R.id.widget_next, c.getString(R.string.no_times))
            }
            rv.setTextViewTextSize(R.id.widget_next, TypedValue.COMPLEX_UNIT_SP, if (large) 26f else if (compact) 16f else 18f)
            rv.setTextColor(R.id.widget_next, fg)

            val today = PrayerRepo.today(ctx)
            val rows = today?.entries?.toList().orEmpty()
            val currentIdx = rows.indexOfLast { !it.value.isAfter(now.toLocalTime()) }
            val labelIds = intArrayOf(R.id.widget_l0, R.id.widget_l1, R.id.widget_l2, R.id.widget_l3, R.id.widget_l4, R.id.widget_l5)
            val timeIds = intArrayOf(R.id.widget_t0, R.id.widget_t1, R.id.widget_t2, R.id.widget_t3, R.id.widget_t4, R.id.widget_t5)
            val labelTextSize = if (compact) 10f else 11f
            val timeTextSize = if (compact) 13f else 14f
            for (i in labelIds.indices) {
                val row = rows.getOrNull(i)
                val isCurrent = i == currentIdx
                rv.setTextViewText(labelIds[i], row?.let { c.getString(it.key.labelRes) } ?: "")
                rv.setTextColor(labelIds[i], if (isCurrent) accent else dim)
                rv.setTextViewTextSize(labelIds[i], TypedValue.COMPLEX_UNIT_SP, labelTextSize)
                rv.setTextViewText(timeIds[i], row?.value?.toString() ?: "")
                rv.setTextColor(timeIds[i], if (isCurrent) accent else fg)
                rv.setTextViewTextSize(timeIds[i], TypedValue.COMPLEX_UNIT_SP, timeTextSize)
            }
            return rv
        }

        /** Accent-tinted rounded panel with stars, at the user's opacity. Sized to the widget and kept under RemoteViews' size limit. */
        internal fun background(ctx: Context, minW: Int, minH: Int, opacity: Int, night: Boolean, accent: Int, fixedDarkBlue: Boolean = false): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val w = (if (minW > 0) minW else 250) * d
            val h = (if (minH > 0) minH else 50) * d
            val scale = minOf(1f, sqrt(400_000f / (w * h * 4)))
            val bw = max(1, (w * scale).toInt())
            val bh = max(1, (h * scale).toInt())
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val top = if (fixedDarkBlue) 0xFF1B2A4E.toInt() else if (night) mix(accent, 0xFF1B2A4E.toInt(), 0.45f) else mix(accent, Color.WHITE, 0.85f)
            val bottom = if (fixedDarkBlue) 0xFF0A1226.toInt() else if (night) mix(accent, 0xFF0A1226.toInt(), 0.8f) else mix(accent, Color.WHITE, 0.6f)
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom))
            bg.cornerRadius = 28 * d * scale
            if (!fixedDarkBlue) bg.setStroke(max(1, (d * scale).roundToInt()), if (night) 0x33FFFFFF else 0x33000000)
            bg.setBounds(0, 0, bw, bh)
            bg.alpha = opacity * 255 / 100
            val canvas = Canvas(bmp)
            bg.draw(canvas)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val starAlpha = opacity / 100f
            Stars.home.forEach { s ->
                paint.alpha = (s.alpha * starAlpha * 255).toInt()
                canvas.drawCircle(s.x * bw, s.y * bh, s.radiusDp * d * scale * 0.55f, paint)
            }
            return bmp
        }

        private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt()
        )
    }
}

