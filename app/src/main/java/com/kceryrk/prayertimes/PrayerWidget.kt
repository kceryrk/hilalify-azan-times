package com.kceryrk.prayertimes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.Duration
import java.time.LocalDateTime

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
        }

        private fun Int.dp(ctx: Context): Int = (this * ctx.resources.displayMetrics.density).toInt()

        private fun views(ctx: Context, opts: Bundle): RemoteViews {
            val c = AppSettings.localized(ctx)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val large = minH >= 180
            val opacity = AppSettings.int(ctx, "widget_opacity", 85)

            rv.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
            rv.setFloat(R.id.widget_root, "setAlpha", opacity / 100f)

            val now = LocalDateTime.now()
            val next = PrayerRepo.upcoming(ctx, now)
            val showNext = AppSettings.bool(ctx, "widget_next")
            val showTimes = AppSettings.bool(ctx, "widget_times") && minH >= 100
            rv.setViewPadding(R.id.widget_root, 12.dp(ctx), 12.dp(ctx), 12.dp(ctx), 12.dp(ctx))
            rv.setViewVisibility(R.id.widget_next, visibleIf(showNext))
            rv.setViewVisibility(R.id.widget_grid, visibleIf(showTimes))

            if (next != null) {
                val mins = Duration.between(now, next.at).toMinutes()
                rv.setTextViewText(
                    R.id.widget_next,
                    "${c.getString(R.string.next_prayer, c.getString(next.prayer.labelRes))} · ${c.getString(R.string.in_time, mins / 60, mins % 60)}"
                )
            } else {
                rv.setTextViewText(R.id.widget_next, c.getString(R.string.no_times))
            }
            rv.setTextViewTextSize(R.id.widget_next, TypedValue.COMPLEX_UNIT_SP, if (large) 22f else 15f)

            val today = PrayerRepo.today(ctx)
            val rows = today?.entries?.toList().orEmpty()
            val labelIds = intArrayOf(R.id.widget_l0, R.id.widget_l1, R.id.widget_l2, R.id.widget_l3, R.id.widget_l4, R.id.widget_l5)
            val timeIds = intArrayOf(R.id.widget_t0, R.id.widget_t1, R.id.widget_t2, R.id.widget_t3, R.id.widget_t4, R.id.widget_t5)
            for (i in labelIds.indices) {
                val row = rows.getOrNull(i)
                rv.setTextViewText(labelIds[i], row?.let { c.getString(it.key.labelRes) } ?: "")
                rv.setTextViewText(timeIds[i], row?.value?.toString() ?: "")
            }
            return rv
        }
    }
}
