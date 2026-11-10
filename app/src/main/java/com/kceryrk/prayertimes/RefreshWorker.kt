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
        /** Refreshes the cached calendar in the background at the interval set in settings (1–10 days). */
        fun schedule(ctx: Context) {
            val days = AppSettings.refreshDays(ctx).toLong()
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(days, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("prayer-refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
