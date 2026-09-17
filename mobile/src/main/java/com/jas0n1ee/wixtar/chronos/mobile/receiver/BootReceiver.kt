package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Reboot/Package update detected ($action), re-scheduling notifications...")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val storage = AppStorage(context)
                    val notificationHelper = MobileNotificationHelper(context)

                    val clockIn = storage.clockInTime.first()
                    val notifyBefore = storage.notifyBeforeOffWork.first()
                    val notifyOn = storage.notifyOnOffWork.first()
                    val advanceMinutes = storage.notifyAdvanceMinutes.first()
                    val useLiveUpdates = storage.useLiveUpdates.first()

                    if ((notifyBefore || notifyOn) && !clockIn.isNullOrBlank()) {
                        notificationHelper.scheduleReminders(clockIn, advanceMinutes, useLiveUpdates)
                    }

                    val fishOilEnabled = storage.fishOilNotifyEnabled.first()
                    val fishOilTime = storage.fishOilNotifyTime.first()
                    val fishOilDays = storage.fishOilNotifyDays.first()

                    if (fishOilEnabled) {
                        notificationHelper.scheduleFishOilReminders(fishOilTime, fishOilDays)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error re-scheduling notifications after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
