package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.mobile.worker.DailyResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager at 00:00 each day to reset the previous day's clock record.
 */
class DailyResetReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DAILY_MIDNIGHT_RESET = "com.jas0n1ee.wixtar.chronos.ACTION_DAILY_MIDNIGHT_RESET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_MIDNIGHT_RESET) return

        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyResetWorker.performDailyReset(context)
            } catch (e: Exception) {
                Log.e("DailyResetReceiver", "Error performing daily midnight reset", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
