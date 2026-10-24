package com.luffy.adbwatchdog

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Persisted periodic job: survives reboot on MIUI TVs that block BOOT_COMPLETED.
 * Does a one-shot adb_enabled check (no foreground service -> no Android 12 FGS restriction).
 */
class BootJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (WatchdogService.prefs(this).getBoolean(WatchdogService.KEY_ENABLED, false)) {
            if (!AdbWifiController.isEnabled(this)) {
                val ok = AdbWifiController.enable(this)
                Log.i(TAG, "job: adb_enabled was 0 -> write result=$ok")
            }
        }
        schedule(this)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "AdbWatchdog"
        private const val JOB_ID = 7301

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, BootJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()
            runCatching { js.schedule(info) }
        }

        fun cancel(ctx: Context) {
            (ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(JOB_ID)
        }
    }
}
