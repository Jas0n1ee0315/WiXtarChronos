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
 * BroadcastReceiver triggered at 00:00 on Wear OS to clear yesterday's clock record and refresh the Tile.
 */
class WearDailyResetReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_WEAR_DAILY_MIDNIGHT_RESET = "com.jas0n1ee.wixtar.chronoswear.ACTION_DAILY_MIDNIGHT_RESET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WEAR_DAILY_MIDNIGHT_RESET) return

        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WearDailyResetWorker.performDailyReset(context)
            } catch (e: Exception) {
                Log.e("WearDailyResetReceiver", "Error performing wear daily midnight reset", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
