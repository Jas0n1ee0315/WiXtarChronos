package com.jas0n1ee.wixtar.chronos.mobile

import android.app.Application
import com.jas0n1ee.wixtar.chronos.mobile.worker.DailyResetWorker

/**
 * Ensures the daily 00:00 reset schedule is armed whenever the app process starts.
 */
class WiXtarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DailyResetWorker.scheduleDailyReset(this)
    }
}
