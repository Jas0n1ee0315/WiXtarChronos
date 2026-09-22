package com.jas0n1ee.wixtar.chronoswear.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronoswear.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TileActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TILE_PUNCH = "com.jas0n1ee.wixtar.chronoswear.ACTION_TILE_PUNCH"
        const val ACTION_TILE_RETRY = "com.jas0n1ee.wixtar.chronoswear.ACTION_TILE_RETRY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("TileActionReceiver", "Received action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = AppStorage(context)
                val repository = ClockRepository(storage)
                val notificationHelper = NotificationHelper(context)

                if (action == ACTION_TILE_RETRY) {
                    MainTileService.forceUpdate(context)
                } else if (action == ACTION_TILE_PUNCH) {
                    storage.clearClockDataIfExpired()
                    val hasNet = isNetworkAvailable(context)
                    val hasPhone = checkPhoneConnected(context)

                    val empId = storage.empId.first()
                    val pass = storage.password.first()
                    val cookie = storage.cookie.first()
                    val enc = storage.enc.first()
                    val gps = storage.gpsLocation.first()

                    val currentIn = storage.clockInTime.first()
                    val isClockingIn = currentIn.isNullOrBlank()
                    val typeStr = if (isClockingIn) "上班卡" else "下班卡"

                    if (hasNet && empId.isNotBlank() && pass.isNotBlank()) {
                        val result = repository.clockIn(empId, pass, cookie, enc, gps)
                        if (result.isSuccess) {
                            val time = result.getOrNull() ?: ""
                            if (isClockingIn) {
                                storage.saveClockInWithSource(time, "雲端")
                            } else {
                                storage.saveClockOutWithSource(time, "雲端")
                            }
                            notificationHelper.showPunchSuccessNotification(typeStr, time)
                            MainTileService.forceUpdate(context)
                        } else {
                            Log.e("TileActionReceiver", "Watch direct punch failed: ${result.exceptionOrNull()?.message}")
                            if (hasPhone) {
                                sendPhonePunchRequest(context, isClockingIn)
                            }
                        }
                    } else if (hasPhone) {
                        sendPhonePunchRequest(context, isClockingIn)
                    } else {
                        Log.w("TileActionReceiver", "Tile punch failed: No network & no phone connection")
                    }
                }
            } catch (e: Exception) {
                Log.e("TileActionReceiver", "Error handling Tile action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkPhoneConnected(context: Context): Boolean {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            nodes.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendPhonePunchRequest(context: Context, isClockingIn: Boolean) {
        val actionStr = if (isClockingIn) "CLOCK_IN" else "CLOCK_OUT"
        WearableSyncManager.sendActionToNodes(context, actionStr)
    }
}
