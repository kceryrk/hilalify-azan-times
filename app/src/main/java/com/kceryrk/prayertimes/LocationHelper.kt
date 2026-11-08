package com.kceryrk.prayertimes

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object LocationHelper {
    private const val TIMEOUT_MS = 15_000L

    /**
     * Requests a fresh fix from the first enabled provider, falling back to the last known location.
     * Calls back with null when no location is available. Caller must hold a location permission.
     */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, onResult: (Location?) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) return onResult(lastKnown(lm))

        val signal = CancellationSignal()
        val main = Handler(Looper.getMainLooper())
        val timeout = Runnable { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
            main.removeCallbacks(timeout)
            onResult(loc ?: lastKnown(lm))
        }
        main.postDelayed(timeout, TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
}
