package com.kceryrk.prayertimes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import java.time.Duration
import java.time.LocalDateTime

/** Shared 2x2 widget variants: list, grid and ring. Each provider only supplies its layout. */
abstract class OptionWidget(private val layoutRes: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, optionViews(context, manager.getAppWidgetOptions(id), layoutRes)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, optionViews(context, newOptions, layoutRes))
    }
}

class PrayerListWidget : OptionWidget(R.layout.widget_opt_list)
class PrayerGridWidget : OptionWidget(R.layout.widget_opt_grid)
class PrayerRingWidget : OptionWidget(R.layout.widget_opt_ring)

private val optionProviders = listOf(
    PrayerListWidget::class.java to R.layout.widget_opt_list,
    PrayerGridWidget::class.java to R.layout.widget_opt_grid,
    PrayerRingWidget::class.java to R.layout.widget_opt_ring
)
private val labelIds = intArrayOf(R.id.opt_l0, R.id.opt_l1, R.id.opt_l2, R.id.opt_l3, R.id.opt_l4, R.id.opt_l5)
private val timeIds = intArrayOf(R.id.opt_t0, R.id.opt_t1, R.id.opt_t2, R.id.opt_t3, R.id.opt_t4, R.id.opt_t5)

fun refreshOptionWidgets(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    optionProviders.forEach { (cls, layout) ->
        mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { mgr.updateAppWidget(it, optionViews(ctx, mgr.getAppWidgetOptions(it), layout)) }
    }
}

private fun optionViews(ctx: Context, opts: Bundle, layoutRes: Int): RemoteViews {
    val c = AppSettings.localized(ctx)
    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val rv = RemoteViews(ctx.packageName, layoutRes)
    val opacity = AppSettings.int(ctx, "widget_opacity", 85)
    // Always dark blue with light text, so these widgets read the same in light and dark mode.
    val fg = Color.WHITE
    val dim = 0xB3FFFFFF.toInt()
    val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
    rv.setImageViewBitmap(R.id.opt_bg, PrayerWidget.background(ctx, minW, minH, opacity, true, accent, fixedDarkBlue = true))

    val now = LocalDateTime.now()
    val next = PrayerRepo.upcoming(ctx, now)
    val rows = PrayerRepo.today(ctx)?.entries?.toList().orEmpty()
    val currentIdx = rows.indexOfLast { !it.value.isAfter(now.toLocalTime()) }

    rv.setTextViewText(R.id.opt_next, next?.let { c.getString(it.prayer.labelRes) } ?: c.getString(R.string.no_times))
    rv.setTextColor(R.id.opt_next, fg)
    val mins = next?.let { Duration.between(now, it.at).toMinutes() }
    rv.setTextViewText(R.id.opt_count, mins?.let { c.getString(R.string.in_time, it / 60, it % 60) } ?: "")
    rv.setTextColor(R.id.opt_count, accent)

    for (i in labelIds.indices) {
        val row = rows.getOrNull(i)
        val isCurrent = i == currentIdx
        rv.setTextViewText(labelIds[i], row?.let { c.getString(it.key.labelRes) } ?: "")
        rv.setTextColor(labelIds[i], if (isCurrent) accent else dim)
        rv.setTextViewText(timeIds[i], row?.value?.toString() ?: "")
        rv.setTextColor(timeIds[i], if (isCurrent) accent else fg)
    }

    if (layoutRes == R.layout.widget_opt_ring) {
        val prevAt = if (currentIdx >= 0) LocalDateTime.of(now.toLocalDate(), rows[currentIdx].value) else null
        rv.setImageViewBitmap(R.id.opt_ring, ringBitmap(ctx, prevAt, next?.at, now, accent, fg))
    }
    return rv
}

/** Progress ring from the previous prayer to the next one. */
private fun ringBitmap(ctx: Context, prevAt: LocalDateTime?, nextAt: LocalDateTime?, now: LocalDateTime, accent: Int, track: Int): Bitmap {
    val size = (120 * ctx.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = size * 0.07f
    val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = track
    paint.alpha = 0x40
    canvas.drawArc(rect, 0f, 360f, false, paint)

    val fraction = if (prevAt != null && nextAt != null && nextAt.isAfter(prevAt)) {
        val total = Duration.between(prevAt, nextAt).seconds.toFloat()
        (Duration.between(prevAt, now).seconds / total).coerceIn(0f, 1f)
    } else 0f
    paint.color = accent
    paint.alpha = 255
    canvas.drawArc(rect, -90f, 360f * fraction, false, paint)
    return bmp
}
