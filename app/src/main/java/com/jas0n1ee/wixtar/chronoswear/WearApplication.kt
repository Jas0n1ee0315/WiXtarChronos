package com.jas0n1ee.wixtar.chronoswear

import android.app.Application
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronoswear.worker.WearDailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ensures the Wear OS daily 00:00 reset schedule is armed whenever the watch app process starts.
 * 同時把手錶上舊版明文 HR 密碼遷移為 Android Keystore 加密儲存（手錶有自己的 Keystore 金鑰）。
 */
class WearApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        WearDailyResetWorker.scheduleDailyReset(this)
        appScope.launch { runCatching { AppStorage(this@WearApplication).migrateLegacyPassword() } }
    }
}
