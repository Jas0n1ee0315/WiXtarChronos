package com.jas0n1ee.wixtar.chronoswear.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.MidnightAlarmHelper
import com.jas0n1ee.wixtar.chronoswear.receiver.WearDailyResetReceiver
import com.jas0n1ee.wixtar.chronoswear.tile.MainTileService
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class WearDailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        performDailyReset(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "WearDailyResetWorker"
        private const val WEAR_DAILY_RESET_ALARM_REQUEST_CODE = 9001
        private const val UNIQUE_WORK_NAME = "WearDailyMidnightReset"

        /**
         * Clears expired clock records on the watch, forces a Wear Tile refresh,
         * and schedules the next 00:00 exact alarm.
         */
        suspend fun performDailyReset(context: Context) {
            val appContext = context.applicationContext
            AppStorage(appContext).clearClockDataIfExpired()
            MainTileService.forceUpdate(appContext)
            scheduleMidnightAlarm(appContext)
        }

        fun scheduleMidnightAlarm(context: Context) {
            MidnightAlarmHelper.scheduleNextMidnightAlarm(
                context = context,
                receiverClass = WearDailyResetReceiver::class.java,
                action = WearDailyResetReceiver.ACTION_WEAR_DAILY_MIDNIGHT_RESET,
                requestCode = WEAR_DAILY_RESET_ALARM_REQUEST_CODE
            )
        }

        fun scheduleDailyReset(context: Context) {
            val appContext = context.applicationContext
            scheduleMidnightAlarm(appContext)

            try {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val initialDelayMinutes = Duration.between(now, midnight).toMinutes().coerceAtLeast(1)

                val resetRequest = PeriodicWorkRequestBuilder<WearDailyResetWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    resetRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue WearDailyResetWorker", e)
            }
        }
    }
}
