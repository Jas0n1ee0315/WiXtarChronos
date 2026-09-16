package com.jas0n1ee.wixtar.chronos.watch.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OffWorkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val helper = NotificationHelper(context)
        when (intent.action) {
            NotificationHelper.ACTION_REMINDER_10 -> helper.showTenMinutesBeforeNotification()
            NotificationHelper.ACTION_REMINDER_NOW -> helper.showOffWorkNotification()
        }
    }
}
