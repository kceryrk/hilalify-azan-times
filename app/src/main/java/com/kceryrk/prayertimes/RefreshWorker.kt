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
        return Result.success()
    }

    companion object {
        /** Refreshes the cached calendar every 6 hours, and works offline from the cache. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("prayer-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
