package com.jas0n1ee.wixtar.chronoswear.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronoswear.worker.WearDailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the Wear OS midnight reset schedule and clears expired records on boot, update, or time changes.
 */
class WearBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            Log.d("WearBootReceiver", "System event detected ($action), syncing daily reset...")
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WearDailyResetWorker.performDailyReset(context)
                    WearDailyResetWorker.scheduleDailyReset(context)
                } catch (e: Exception) {
                    Log.e("WearBootReceiver", "Error handling $action", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
