package com.kceryrk.prayertimes

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PrayerRepo.refresh(applicationContext)
        Alarms.scheduleNext(applicationContext)
        Alarms.updateStatus(applicationContext)
        PrayerWidget.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        /** Refreshes the cached calendar at the interval set in settings (6, 12 or 24 hours). */
        fun schedule(ctx: Context) {
            val hours = AppSettings.refreshHours(ctx).toLong()
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(hours, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("prayer-refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
