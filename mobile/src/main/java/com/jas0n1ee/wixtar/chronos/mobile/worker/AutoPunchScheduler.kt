package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.content.Context
import androidx.work.WorkManager

object AutoPunchScheduler {
    fun scheduleAutoPunch(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("AutoClockInWork")
            workManager.cancelUniqueWork("AutoClockOutWork")
        } catch (_: Exception) {}
    }
}
