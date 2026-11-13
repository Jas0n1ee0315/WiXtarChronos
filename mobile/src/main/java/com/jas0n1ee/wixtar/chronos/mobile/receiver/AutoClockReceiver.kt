package com.jas0n1ee.wixtar.chronos.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.jas0n1ee.wixtar.chronos.mobile.autoclock.AutoClockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoClockReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_AUTO_CLOCK_IN = "com.jas0n1ee.wixtar.chronos.ACTION_AUTO_CLOCK_IN"
        const val ACTION_AUTO_CLOCK_OUT = "com.jas0n1ee.wixtar.chronos.ACTION_AUTO_CLOCK_OUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("AutoClockReceiver", "Received action: $action")

        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiXtar:AutoClockReceiver")
        try {
            wakeLock?.acquire(45_000L) // Hold CPU wake lock for up to 45s for network requests
        } catch (e: Exception) {
            Log.e("AutoClockReceiver", "Failed to acquire wakelock", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = AutoClockManager(context)
                when (action) {
                    ACTION_AUTO_CLOCK_IN -> {
                        manager.handleAutoClockInTrigger()
                    }
                    ACTION_AUTO_CLOCK_OUT -> {
                        manager.handleAutoClockOutTrigger()
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoClockReceiver", "Error processing $action", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult?.finish()
            }
        }
    }
}
