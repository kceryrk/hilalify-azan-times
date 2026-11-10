package com.kceryrk.prayertimes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relaunches the app after an update has been installed over it. */
class UpdateRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Alarms.scheduleNext(context)
        Alarms.updateStatus(context)
        PrayerWidget.refreshAll(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
