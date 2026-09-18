package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LunchDndReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_LUNCH_DND_START = "com.jas0n1ee.wixtar.chronos.ACTION_LUNCH_DND_START"
        const val ACTION_LUNCH_DND_END = "com.jas0n1ee.wixtar.chronos.ACTION_LUNCH_DND_END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("LunchDndReceiver", "Received DND action: $action")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val helper = MobileNotificationHelper(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = AppStorage(context)
                val enabled = storage.lunchDndEnabled.first()
                val clockIn = storage.clockInTime.first()

                if (action == ACTION_LUNCH_DND_START) {
                    if (enabled && !clockIn.isNullOrBlank()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                            helper.sendNotification("午休勿擾模式", "已為您開啟午休勿擾模式，將於 13:00 自動恢復通知")
                            Log.d("LunchDndReceiver", "Lunch DND mode ENABLED successfully")
                        } else {
                            helper.sendNotification("午休勿擾提示", "尚未授權勿擾模式存取權限，無法自動切換勿擾")
                        }
                    } else {
                        Log.d("LunchDndReceiver", "Lunch DND skipped: enabled=$enabled, clockIn=$clockIn")
                    }
                } else if (action == ACTION_LUNCH_DND_END) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        helper.sendNotification("午休結束", "午休時間結束，已為您恢復正常通知模式")
                        Log.d("LunchDndReceiver", "Lunch DND mode RESTORED to ALL successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("LunchDndReceiver", "Error processing Lunch DND action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
