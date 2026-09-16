package com.jas0n1ee.wixtar.chronos.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WearableSyncManager {
    const val SETTINGS_PATH = "/settings"
    const val CLOCK_STATUS_PATH = "/clock_status"
    const val ACTION_PATH = "/action"

    const val KEY_EMP_ID = "emp_id"
    const val KEY_PASSWORD = "password"
    const val KEY_COOKIE = "cookie"
    const val KEY_ENC = "enc"
    const val KEY_GPS = "gps_location"
    const val KEY_NOTIFY_BEFORE = "notify_before"
    const val KEY_NOTIFY_ON = "notify_on"

    const val KEY_CLOCK_IN = "clock_in_time"
    const val KEY_CLOCK_OUT = "clock_out_time"
    const val KEY_LAST_DATE = "last_clock_date"
    const val KEY_TIMESTAMP = "timestamp"

    suspend fun syncSettings(
        context: Context,
        empId: String,
        pass: String,
        cookie: String,
        enc: String,
        gps: String,
        notifyBefore: Boolean,
        notifyOn: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val putDataMapReq = PutDataMapRequest.create(SETTINGS_PATH)
            val map = putDataMapReq.dataMap
            map.putString(KEY_EMP_ID, empId)
            map.putString(KEY_PASSWORD, pass)
            map.putString(KEY_COOKIE, cookie)
            map.putString(KEY_ENC, enc)
            map.putString(KEY_GPS, gps)
            map.putBoolean(KEY_NOTIFY_BEFORE, notifyBefore)
            map.putBoolean(KEY_NOTIFY_ON, notifyOn)
            map.putLong(KEY_TIMESTAMP, System.currentTimeMillis())

            val request = putDataMapReq.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            Log.d("WearableSyncManager", "Synced settings to Wearable DataClient")
        } catch (e: Exception) {
            Log.e("WearableSyncManager", "Failed to sync settings", e)
        }
    }

    suspend fun syncClockStatus(
        context: Context,
        clockIn: String?,
        clockOut: String?,
        lastDate: String?
    ) = withContext(Dispatchers.IO) {
        try {
            val putDataMapReq = PutDataMapRequest.create(CLOCK_STATUS_PATH)
            val map = putDataMapReq.dataMap
            map.putString(KEY_CLOCK_IN, clockIn ?: "")
            map.putString(KEY_CLOCK_OUT, clockOut ?: "")
            map.putString(KEY_LAST_DATE, lastDate ?: "")
            map.putLong(KEY_TIMESTAMP, System.currentTimeMillis())

            val request = putDataMapReq.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            Log.d("WearableSyncManager", "Synced clock status to Wearable DataClient")
        } catch (e: Exception) {
            Log.e("WearableSyncManager", "Failed to sync clock status", e)
        }
    }

    suspend fun sendActionToNodes(context: Context, action: String) = withContext(Dispatchers.IO) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            val messageClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, ACTION_PATH, action.toByteArray()))
            }
            Log.d("WearableSyncManager", "Sent action '$action' to ${nodes.size} nodes")
        } catch (e: Exception) {
            Log.e("WearableSyncManager", "Failed to send action to nodes", e)
        }
    }
}
