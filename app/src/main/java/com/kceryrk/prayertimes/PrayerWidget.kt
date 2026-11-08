package com.kceryrk.prayertimes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.time.LocalDateTime

class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, PrayerWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, views(ctx)) }
        }

        private fun views(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            val today = PrayerRepo.today(ctx)
            val next = PrayerRepo.upcoming(ctx, LocalDateTime.now())
            rv.setTextViewText(
                R.id.widget_next,
                next?.let { ctx.getString(R.string.next_prayer, ctx.getString(it.prayer.labelRes)) } ?: ctx.getString(R.string.no_times)
            )
            rv.setTextViewText(
                R.id.widget_times,
                today?.entries?.joinToString("  ") { "${ctx.getString(it.key.labelRes)} ${it.value}" } ?: ""
            )
            return rv
        }
    }
}
