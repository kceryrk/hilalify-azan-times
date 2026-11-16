package com.kceryrk.prayertimes

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

const val NEXT_PRAYER_PATH = "/next_prayer"
const val NEXT_PRAYER_PREFS = "wear_next_prayer"

/** Stores the next prayer pushed by the phone so the watch UI can show it without a connection. */
class NextPrayerListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != NEXT_PRAYER_PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            getSharedPreferences(NEXT_PRAYER_PREFS, Context.MODE_PRIVATE).edit()
                .putString("label", map.getString("label"))
                .putLong("at", map.getLong("at"))
                .apply()
        }
    }
}
