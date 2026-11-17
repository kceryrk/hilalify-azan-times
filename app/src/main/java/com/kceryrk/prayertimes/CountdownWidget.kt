package com.kceryrk.prayertimes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.sqrt

/** Minimalist 1x1 resizable countdown widget with a perimeter countdown border in the app accent color. */
class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, countdownViews(context, manager.getAppWidgetOptions(id))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, countdownViews(context, newOptions))
    }
}

fun refreshCountdownWidgets(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    mgr.getAppWidgetIds(ComponentName(ctx, CountdownWidget::class.java)).forEach {
        mgr.updateAppWidget(it, countdownViews(ctx, mgr.getAppWidgetOptions(it)))
    }
}

private fun countdownViews(ctx: Context, opts: Bundle = Bundle.EMPTY): RemoteViews {
    val rv = RemoteViews(ctx.packageName, R.layout.widget_countdown)
    val launch = PrayerWidget.homePendingIntent(ctx)
    rv.setOnClickPendingIntent(R.id.cd_root, launch)

    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 72
    val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 72
    val opacity = AppSettings.int(ctx, "widget_opacity", 100)
    val accent = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)

    val now = LocalDateTime.now()
    val next = PrayerRepo.upcoming(ctx, now)
    val rows = PrayerRepo.today(ctx)?.entries?.toList().orEmpty()
    val currentIdx = rows.indexOfLast { !it.value.isAfter(now.toLocalTime()) }
    val prevAt = if (currentIdx >= 0) LocalDateTime.of(now.toLocalDate(), rows[currentIdx].value) else null

    val mins = next?.let { Duration.between(now, it.at).toMinutes().coerceAtLeast(0) }
    val text = when {
        mins == null -> "--"
        mins >= 60 -> "${mins / 60}:%02d".format(mins % 60)
        else -> "$mins"
    }

    val remaining = if (prevAt != null && next != null && next.at.isAfter(prevAt)) {
        val total = Duration.between(prevAt, next.at).seconds.toFloat().coerceAtLeast(1f)
        (1f - Duration.between(prevAt, now).seconds / total).coerceIn(0f, 1f)
    } else 1f

    rv.setImageViewBitmap(R.id.cd_bg, countdownCanvasBitmap(ctx, minW, minH, opacity, remaining, accent, text))
    return rv
}

/**
 * Renders the widget background (with clipped stars), centrally-aligned perimeter countdown line,
 * and Outfit Bold countdown text scaled cleanly to fit the inner box.
 */
private fun countdownCanvasBitmap(
    ctx: Context,
    wDp: Int,
    hDp: Int,
    opacity: Int,
    remaining: Float,
    accent: Int,
    text: String
): Bitmap {
    val d = ctx.resources.displayMetrics.density.coerceAtLeast(1.5f)
    val rawW = wDp * d
    val rawH = hDp * d
    val scale = minOf(1f, sqrt(400_000f / (rawW * rawH * 4f)))
    val pxScale = d * scale
    val w = (rawW * scale).toInt().coerceAtLeast(96)
    val h = (rawH * scale).toInt().coerceAtLeast(96)

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = (minOf(w, h) * 0.055f).coerceIn(4f * scale, 10f * pxScale)
    val pad = stroke / 2f + 1f
    val rect = RectF(pad, pad, w - pad, h - pad)
    val cr = minOf(44f * pxScale, minOf(rect.width(), rect.height()) * 0.5f)

    val bgPath = Path().apply {
        moveTo(rect.centerX(), rect.top)
        lineTo(rect.right - cr, rect.top)
        arcTo(RectF(rect.right - 2 * cr, rect.top, rect.right, rect.top + 2 * cr), -90f, 90f, false)
        lineTo(rect.right, rect.bottom - cr)
        arcTo(RectF(rect.right - 2 * cr, rect.bottom - 2 * cr, rect.right, rect.bottom), 0f, 90f, false)
        lineTo(rect.left + cr, rect.bottom)
        arcTo(RectF(rect.left, rect.bottom - 2 * cr, rect.left + 2 * cr, rect.bottom), 90f, 90f, false)
        lineTo(rect.left, rect.top + cr)
        arcTo(RectF(rect.left, rect.top, rect.left + 2 * cr, rect.top + 2 * cr), 180f, 90f, false)
        lineTo(rect.centerX(), rect.top)
    }

    canvas.save()
    canvas.clipPath(bgPath)
    val bg = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF1B2A4E.toInt(), 0xFF0A1226.toInt())
    )
    bg.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt() + 1, rect.bottom.toInt() + 1)
    bg.alpha = opacity * 255 / 100
    bg.draw(canvas)

    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val starAlpha = opacity / 100f
    Stars.home.forEach { s ->
        starPaint.alpha = (s.alpha * starAlpha * 255).toInt()
        canvas.drawCircle(
            rect.left + s.x * rect.width(),
            rect.top + s.y * rect.height(),
            s.radiusDp * pxScale * 0.55f,
            starPaint
        )
    }
    canvas.restore()

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        alpha = 0x33
    }
    canvas.drawPath(bgPath, borderPaint)

    if (remaining > 0.002f) {
        val pm = PathMeasure(bgPath, false)
        val seg = Path()
        pm.getSegment(0f, pm.length * remaining, seg, true)
        borderPaint.color = accent
        borderPaint.alpha = 255
        canvas.drawPath(seg, borderPaint)
    }

    // Render countdown text directly in Outfit Bold so OEM launchers cannot override the font.
    val outfit = runCatching { ResourcesCompat.getFont(ctx, R.font.outfit) }.getOrNull()
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = outfit ?: Typeface.DEFAULT_BOLD
        fontVariationSettings = "'wght' 700"
        textSize = minOf(rect.width(), rect.height()) * 0.58f
    }
    val maxTextW = rect.width() * 0.70f
    val maxTextH = rect.height() * 0.56f
    val measuredW = textPaint.measureText(text).coerceAtLeast(1f)
    if (measuredW > maxTextW) textPaint.textSize *= maxTextW / measuredW
    val fm = textPaint.fontMetrics
    val textH = (fm.descent - fm.ascent).coerceAtLeast(1f)
    if (textH > maxTextH) textPaint.textSize *= maxTextH / textH
    val finalFm = textPaint.fontMetrics
    val baseline = rect.centerY() - (finalFm.ascent + finalFm.descent) * 0.5f
    canvas.drawText(text, rect.centerX(), baseline, textPaint)

    return bmp
}
