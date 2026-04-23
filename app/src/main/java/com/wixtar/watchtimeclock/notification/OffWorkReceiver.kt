package com.wixtar.watchtimeclock.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wixtar.watchtimeclock.data.AppStorage
import com.wixtar.watchtimeclock.data.ClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OffWorkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val helper = NotificationHelper(context)
        val storage = AppStorage(context)
        
        when (intent.action) {
            "SCHEDULE_REMINDER_10" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    if (storage.notifyBeforeOffWork.first()) {
                        helper.showTenMinutesBeforeNotification()
                    }
                }
            }
            "SCHEDULE_REMINDER_NOW" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    if (storage.notifyOnOffWork.first()) {
                        helper.showOffWorkNowNotification()
                    }
                }
            }
            NotificationHelper.ACTION_CLOCK_OUT -> {
                // Perform clock out in background
                val repository = ClockRepository(storage)
                CoroutineScope(Dispatchers.IO).launch {
                    val id = storage.empId.first()
                    val pass = storage.password.first()
                    val cookie = storage.cookie.first()
                    val enc = storage.enc.first()
                    val gps = storage.gpsLocation.first()
                    
                    val result = repository.clockIn(id, pass, cookie, enc, gps)
                    if (result.isSuccess) {
                        storage.saveClockOut(result.getOrNull() ?: "")
                    }
                }
            }
        }
    }
}
