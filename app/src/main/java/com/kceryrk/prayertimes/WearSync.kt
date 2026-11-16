package com.kceryrk.prayertimes

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Sends the next prayer to a paired Wear OS watch over the Data Layer. Silently does nothing without a paired watch. */
object WearSync {
    const val PATH = "/next_prayer"

    fun pushNext(ctx: Context, label: String, atMillis: Long) {
        runCatching {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("label", label)
                dataMap.putLong("at", atMillis)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(ctx).putDataItem(request)
        }
    }
}
