package com.jas0n1ee.wixtar.chronos.mobile

import android.app.Application
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.worker.DailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ensures the daily 00:00 reset schedule is armed whenever the app process starts.
 * 同時把舊版明文 HR 密碼遷移為 Android Keystore 加密儲存。
 */
class WiXtarApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DailyResetWorker.scheduleDailyReset(this)
        appScope.launch { runCatching { AppStorage(this@WiXtarApplication).migrateLegacyPassword() } }
    }
}
