package com.jas0n1ee.wixtar.chronoswear

import android.app.Application
import com.jas0n1ee.wixtar.chronoswear.worker.WearDailyResetWorker

/**
 * Ensures the Wear OS daily 00:00 reset schedule is armed whenever the watch app process starts.
 */
class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WearDailyResetWorker.scheduleDailyReset(this)
    }
}
