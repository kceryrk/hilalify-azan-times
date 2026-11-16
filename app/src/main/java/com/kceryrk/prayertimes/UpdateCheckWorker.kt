package com.kceryrk.prayertimes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import java.util.concurrent.TimeUnit

/**
 * Asks Google Play whether a newer version is available and posts a notification linking to the Play listing.
 * Only works for builds installed from Play (internal/closed/production tracks). Other installs are ignored.
 */
class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val info = try {
            AppUpdateManagerFactory.create(ctx).requestAppUpdateInfo()
        } catch (_: Exception) {
            return Result.success()
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return Result.success()

        val versionCode = info.availableVersionCode()
        val prefs = AppSettings.prefs(ctx)
        if (versionCode <= prefs.getInt(KEY_NOTIFIED_CODE, 0)) return Result.success()
        if (postNotification(ctx)) prefs.edit().putInt(KEY_NOTIFIED_CODE, versionCode).apply()
        return Result.success()
    }

    private fun postNotification(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, ctx.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}"))
        val pi = PendingIntent.getActivity(
            ctx, REQ_UPDATE, storeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.update_notif_title))
            .setContentText(ctx.getString(R.string.update_notif_text))
            .setContentIntent(pi)
            .addAction(0, ctx.getString(R.string.update_notif_action), pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_UPDATE, n)
        return true
    }

    companion object {
        const val CH_UPDATES = "updates"
        private const val NOTIF_UPDATE = 1002
        private const val REQ_UPDATE = 3001
        private const val KEY_NOTIFIED_CODE = "notified_update_code"

        /** Checks once a day in the background. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Runs a single check right now (e.g. on app launch). */
        fun checkNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateCheckWorker>().build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("update-check-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
