package com.kceryrk.prayertimes

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LuxuryFont = FontFamily(Font(R.font.playfair_display, FontWeight.Normal, FontStyle.Normal))

/** Crescent mark plus the "Hilal'ify" wordmark in a serif display face. */
@Composable
fun HilalifyHeader(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(38.dp)) {
            val s = size.minDimension
            val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, s, s)) }
            val inner = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(s * 0.32f, s * 0.08f, s * 1.08f, s * 0.84f))
            }
            val crescent = android.graphics.Path(outer.asAndroidPath()).apply {
                op(inner.asAndroidPath(), android.graphics.Path.Op.DIFFERENCE)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(crescent, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.toArgb() })
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                append("Hilal'")
                withStyle(SpanStyle(color = accent)) { append("ify") }
            },
            style = TextStyle(fontFamily = LuxuryFont, fontSize = 30.sp, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
