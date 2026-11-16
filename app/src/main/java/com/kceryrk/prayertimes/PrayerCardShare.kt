package com.kceryrk.prayertimes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CARD_W = 1080
private const val CARD_H = 1350
private const val NAVY = 0xFF0A1633.toInt()

fun sharePrayerCard(ctx: Context, date: LocalDate, times: Map<Prayer, LocalTime>, accent: Int) {
    val locale = AppSettings.localized(ctx).resources.configuration.locales[0]
    val isTr = locale.language == "tr"
    val bmp = renderPrayerCard(ctx, date, times, accent, locale, isTr)
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "prayer_card.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()

    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(ctx.contentResolver, "prayer_card", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_card_chooser)))
}

private fun renderPrayerCard(
    ctx: Context,
    date: LocalDate,
    times: Map<Prayer, LocalTime>,
    accent: Int,
    locale: Locale,
    isTr: Boolean
): Bitmap {
    val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, CARD_H.toFloat(),
            ColorUtils.blendARGB(accent, NAVY, 0.25f), NAVY, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bgPaint)

    val serif = ResourcesCompat.getFont(ctx, R.font.playfair_display) ?: Typeface.SERIF
    val title = textPaint(serif, 120f, Color.WHITE, centered = true)
    val location = textPaint(Typeface.DEFAULT, 56f, 0xCCFFFFFF.toInt(), centered = true)
    val dateText = textPaint(Typeface.DEFAULT, 44f, 0xB3FFFFFF.toInt(), centered = true)
    val hijriText = textPaint(Typeface.DEFAULT_BOLD, 40f, accent, centered = true)
    val rowLabel = textPaint(Typeface.DEFAULT, 60f, 0xCCFFFFFF.toInt())
    val rowTime = textPaint(Typeface.DEFAULT_BOLD, 64f, Color.WHITE, rightAligned = true)
    val divider = Paint().apply { color = accent; strokeWidth = 4f }

    canvas.drawText("Hilal'ify", CARD_W / 2f, 190f, title)
    PrayerRepo.locationLabel(ctx)?.let { canvas.drawText(it, CARD_W / 2f, 280f, location) }
    canvas.drawText(
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
        CARD_W / 2f, 370f, dateText
    )
    PrayerRepo.hijri(ctx, date)?.let { canvas.drawText(HijriInfo.label(isTr, it), CARD_W / 2f, 430f, hijriText) }
    canvas.drawLine(130f, 500f, CARD_W - 130f, 500f, divider)

    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val rows = Prayer.entries.filter { times.containsKey(it) }
    rows.forEachIndexed { i, prayer ->
        val y = 620f + i * 130f
        canvas.drawText(ctx.getString(prayer.labelRes), 130f, y, rowLabel)
        canvas.drawText(times.getValue(prayer).format(timeFmt), CARD_W - 130f, y, rowTime)
    }
    return bmp
}

private fun textPaint(
    typeface: Typeface,
    size: Float,
    color: Int,
    centered: Boolean = false,
    rightAligned: Boolean = false
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    textSize = size
    this.color = color
    textAlign = when {
        centered -> Paint.Align.CENTER
        rightAligned -> Paint.Align.RIGHT
        else -> Paint.Align.LEFT
    }
}
