package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.worker.DailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            Log.d("BootReceiver", "System event detected ($action), syncing daily reset and reminders...")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DailyResetWorker.performDailyReset(context)
                    DailyResetWorker.scheduleDailyReset(context)

                    val storage = AppStorage(context)
                    val notificationHelper = MobileNotificationHelper(context)

                    val clockIn = storage.clockInTime.first()
                    val clockOut = storage.clockOutTime.first()
                    val notifyBefore = storage.notifyBeforeOffWork.first()
                    val notifyOn = storage.notifyOnOffWork.first()
                    val advanceMinutes = storage.notifyAdvanceMinutes.first()
                    val useLiveUpdates = storage.useLiveUpdates.first()

                    if ((notifyBefore || notifyOn) && !clockIn.isNullOrBlank() && clockOut.isNullOrBlank()) {
                        notificationHelper.scheduleReminders(clockIn, advanceMinutes, useLiveUpdates)
                    }

                    if (storage.forgetClockOutNotifyEnabled.first()) {
                        val forgetTime = storage.forgetClockOutNotifyTime.first()
                        notificationHelper.scheduleForgetClockOutReminders(forgetTime)
                    }

                    val autoClockManager = com.jas0n1ee.wixtar.chronos.mobile.autoclock.AutoClockManager(context)
                    if (storage.autoClockInEnabled.first()) {
                        autoClockManager.scheduleAutoClockIn()
                    }
                    if (storage.autoClockOutEnabled.first() && !clockIn.isNullOrBlank() && clockOut.isNullOrBlank()) {
                        try {
                            val cleanTime = clockIn.substringBefore(" ").substringBefore("(").trim()
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                            val inTime = java.time.LocalTime.parse(cleanTime, formatter)
                            val offWork = com.jas0n1ee.wixtar.chronos.data.WorkShiftPolicy.calculateExpectedOffWorkTime(inTime)
                            autoClockManager.scheduleAutoClockOut(offWork, bufferSeconds = 5)
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Error re-scheduling auto clock out", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error re-scheduling after $action", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
