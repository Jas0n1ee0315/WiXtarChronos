package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.MidnightAlarmHelper
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.receiver.DailyResetReceiver
import com.jas0n1ee.wixtar.chronos.mobile.service.WiXtarTileService
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetActionReceiver
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        performDailyReset(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "DailyResetWorker"
        private const val DAILY_RESET_ALARM_REQUEST_CODE = 9000
        private const val UNIQUE_WORK_NAME = "DailyMidnightReset"

        /**
         * Clears expired clock-in/out records, refreshes widgets/tiles/watch,
         * re-schedules daily reminders, and arms the next 00:00 exact alarm.
         */
        suspend fun performDailyReset(context: Context) {
            val appContext = context.applicationContext
            val storage = AppStorage(appContext)
            val cleared = storage.clearClockDataIfExpired()

            val notificationHelper = MobileNotificationHelper(appContext)
            if (cleared) {
                notificationHelper.cancelLiveUpdateNotification()
                if (storage.enableWatchSync.first()) {
                    WearableSyncManager.syncClockStatus(appContext, "", "", LocalDate.now().toString())
                }
            }

            if (storage.lunchDndEnabled.first()) {
                val dndStart = storage.lunchDndStartTime.first()
                val dndEnd = storage.lunchDndEndTime.first()
                notificationHelper.scheduleLunchDndReminders(dndStart, dndEnd)
            }

            if (storage.forgetClockOutNotifyEnabled.first()) {
                val forgetTime = storage.forgetClockOutNotifyTime.first()
                notificationHelper.scheduleForgetClockOutReminders(forgetTime)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    TileService.requestListeningState(
                        appContext,
                        ComponentName(appContext, WiXtarTileService::class.java)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update Quick Settings Tile", e)
                }
            }

            PunchWidgetActionReceiver.updateAllWidgets(appContext)
            scheduleMidnightAlarm(appContext)
        }

        fun scheduleMidnightAlarm(context: Context) {
            MidnightAlarmHelper.scheduleNextMidnightAlarm(
                context = context,
                receiverClass = DailyResetReceiver::class.java,
                action = DailyResetReceiver.ACTION_DAILY_MIDNIGHT_RESET,
                requestCode = DAILY_RESET_ALARM_REQUEST_CODE
            )
        }

        fun scheduleDailyReset(context: Context) {
            val appContext = context.applicationContext
            scheduleMidnightAlarm(appContext)

            try {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val initialDelayMinutes = Duration.between(now, midnight).toMinutes().coerceAtLeast(1)

                val resetRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    resetRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue DailyResetWorker", e)
            }
        }
    }
}
