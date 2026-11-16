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
import java.util.concurrent.atomic.AtomicBoolean

object LocationHelper {
    private const val TIMEOUT_MS = 3_500L

    /**
     * Returns a recent cached fix immediately when available, or races enabled providers (preferring fast
     * network/Wi-Fi fixes indoors) with a short timeout so startup never stalls.
     */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, onResult: (Location?) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cached = lastKnown(lm)
        if (cached != null && System.currentTimeMillis() - cached.time <= 30 * 60_000L) {
            onResult(cached)
            return
        }
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)
        val signals = mutableListOf<CancellationSignal>()
        fun finish(loc: Location?) {
            if (done.compareAndSet(false, true)) {
                main.removeCallbacksAndMessages(null)
                signals.forEach { runCatching { it.cancel() } }
                onResult(loc ?: cached)
            }
        }
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return finish(null)

        main.postDelayed({ finish(null) }, TIMEOUT_MS)
        for (p in providers) {
            val sig = CancellationSignal()
            signals.add(sig)
            LocationManagerCompat.getCurrentLocation(lm, p, sig, ContextCompat.getMainExecutor(ctx)) { loc ->
                if (loc != null) finish(loc)
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
}
