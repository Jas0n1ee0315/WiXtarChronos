package com.jas0n1ee.wixtar.chronos.mobile.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OffWorkNotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_OFF_WORK_PUNCH = "com.jas0n1ee.wixtar.chronos.ACTION_OFF_WORK_PUNCH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_OFF_WORK_PUNCH) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(MobileNotificationHelper.OFF_WORK_NOTIFICATION_ID)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val storage = AppStorage(context)
                    val repository = ClockRepository(storage)
                    val notificationHelper = MobileNotificationHelper(context)

                    val empId = storage.empId.first()
                    val pass = storage.password.first()
                    val cookie = storage.cookie.first()
                    val enc = storage.enc.first()
                    val gps = storage.gpsLocation.first()

                    val result = repository.clockIn(empId, pass, cookie, enc, gps)
                    if (result.isSuccess) {
                        val time = result.getOrNull() ?: ""
                        storage.saveClockOut(time)
                        notificationHelper.cancelLiveUpdateNotification()
                        notificationHelper.sendNotification("WiXtar 打卡成功", "已成功打下班卡 ($time)")
                        Log.d("OffWorkActionReceiver", "Punched off-work card successfully: $time")
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                        notificationHelper.sendNotification("WiXtar 打卡失敗", errorMsg)
                        Log.e("OffWorkActionReceiver", "Failed to punch off-work card: $errorMsg")
                    }
                } catch (e: Exception) {
                    Log.e("OffWorkActionReceiver", "Error in OffWorkNotificationActionReceiver", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
