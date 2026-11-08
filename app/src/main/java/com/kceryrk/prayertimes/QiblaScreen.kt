package com.kceryrk.prayertimes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262

/** Great-circle bearing from (lat, lon) to the Kaaba, in degrees clockwise from true north. */
fun qiblaBearing(lat: Double, lon: Double): Float {
    val p1 = Math.toRadians(lat)
    val p2 = Math.toRadians(KAABA_LAT)
    val dl = Math.toRadians(KAABA_LON - lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

@Composable
fun QiblaScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gps = PrayerRepo.savedGps(ctx)
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rot = FloatArray(9)
            private val orient = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                heading = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.qibla), style = MaterialTheme.typography.headlineMedium)
        if (gps == null) {
            Text(stringResource(R.string.qibla_needs_gps))
        } else {
            val target = qiblaBearing(gps.first, gps.second)
            val diff = ((target - heading + 540) % 360) - 180
            Text(stringResource(R.string.qibla_degrees, target.toInt()))
            Text(
                stringResource(if (kotlin.math.abs(diff) < 5) R.string.qibla_aligned else R.string.qibla_turn, diff.toInt()),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.qibla_note))
        }
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
