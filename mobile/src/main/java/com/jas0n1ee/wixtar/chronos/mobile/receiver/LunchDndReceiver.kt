package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
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

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = AppStorage(context)
                val enabled = storage.lunchDndEnabled.first()
                val clockIn = storage.clockInTime.first()

                if (enabled && !clockIn.isNullOrBlank()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                        if (action == ACTION_LUNCH_DND_START) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                            Log.d("LunchDndReceiver", "Lunch DND mode ENABLED silently (clockIn=$clockIn)")
                        } else if (action == ACTION_LUNCH_DND_END) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            Log.d("LunchDndReceiver", "Lunch DND mode RESTORED to ALL silently (clockIn=$clockIn)")
                        }
                    } else {
                        Log.d("LunchDndReceiver", "Lunch DND skipped: permission not granted")
                    }
                } else {
                    Log.d("LunchDndReceiver", "Lunch DND skipped: enabled=$enabled, clockIn=$clockIn")
                }
            } catch (e: Exception) {
                Log.e("LunchDndReceiver", "Error processing Lunch DND action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
