package com.jas0n1ee.wixtar.chronos.mobile.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FishOilActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FISH_OIL_EATEN = "com.jas0n1ee.wixtar.chronos.ACTION_FISH_OIL_EATEN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FISH_OIL_EATEN) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(MobileNotificationHelper.FISH_OIL_NOTIFICATION_ID)
            Log.d("FishOilActionReceiver", "Fish oil notification dismissed by user action")
        }
    }
}
