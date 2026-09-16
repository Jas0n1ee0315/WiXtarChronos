package com.jas0n1ee.wixtar.chronos.mobile.service

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MobileWearableListenerService : WearableListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        val storage = AppStorage(applicationContext)

        for (event in dataEvents) {
            val uri = event.dataItem.uri
            if (uri.path == WearableSyncManager.SETTINGS_PATH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val empId = dataMap.getString(WearableSyncManager.KEY_EMP_ID) ?: ""
                val pass = dataMap.getString(WearableSyncManager.KEY_PASSWORD) ?: ""
                val cookie = dataMap.getString(WearableSyncManager.KEY_COOKIE) ?: ""
                val enc = dataMap.getString(WearableSyncManager.KEY_ENC) ?: ""
                val gps = dataMap.getString(WearableSyncManager.KEY_GPS) ?: "22.987929,120.197210"
                val notifyBefore = dataMap.getBoolean(WearableSyncManager.KEY_NOTIFY_BEFORE, true)
                val notifyOn = dataMap.getBoolean(WearableSyncManager.KEY_NOTIFY_ON, true)

                scope.launch {
                    storage.saveSettings(empId, pass, cookie, enc, gps, notifyBefore, notifyOn)
                    Log.d("MobileWearableListener", "Received & saved updated settings from Wear OS Watch")
                }
            } else if (uri.path == WearableSyncManager.CLOCK_STATUS_PATH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val clockIn = dataMap.getString(WearableSyncManager.KEY_CLOCK_IN) ?: ""
                val clockOut = dataMap.getString(WearableSyncManager.KEY_CLOCK_OUT) ?: ""

                scope.launch {
                    if (clockIn.isNotBlank()) storage.saveClockIn(clockIn)
                    if (clockOut.isNotBlank()) storage.saveClockOut(clockOut)
                    Log.d("MobileWearableListener", "Received & saved updated clock status from Wear OS Watch")
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val storage = AppStorage(applicationContext)
        val repository = ClockRepository(storage)

        if (messageEvent.path == WearableSyncManager.ACTION_PATH) {
            val action = String(messageEvent.data)
            Log.d("MobileWearableListener", "Received action message from Watch: $action")

            scope.launch {
                when (action) {
                    "CLOCK_IN", "CLOCK_OUT", "CLOCK_ACTION" -> {
                        val empId = storage.empId.first()
                        val pass = storage.password.first()
                        val cookie = storage.cookie.first()
                        val enc = storage.enc.first()
                        val gps = storage.gpsLocation.first()

                        val currentIn = storage.clockInTime.first()
                        val isClockingIn = currentIn.isNullOrBlank()

                        val result = repository.clockIn(empId, pass, cookie, enc, gps)
                        if (result.isSuccess) {
                            val time = result.getOrNull() ?: ""
                            if (isClockingIn) {
                                storage.saveClockIn(time)
                            } else {
                                storage.saveClockOut(time)
                            }
                            val todayStr = java.time.LocalDate.now().toString()
                            val updatedIn = if (isClockingIn) time else currentIn
                            val updatedOut = if (!isClockingIn) time else storage.clockOutTime.first()
                            WearableSyncManager.syncClockStatus(applicationContext, updatedIn, updatedOut, todayStr)
                        }
                    }
                    "FETCH_CLOUD" -> {
                        val cookie = storage.cookie.first()
                        if (cookie.isNotBlank()) {
                            repository.getCloudClockInfo(cookie).onSuccess { (cloudIn, cloudOut, _) ->
                                cloudIn?.let { storage.saveClockIn(it) }
                                cloudOut?.let { storage.saveClockOut(it) }
                                val todayStr = java.time.LocalDate.now().toString()
                                WearableSyncManager.syncClockStatus(applicationContext, cloudIn, cloudOut, todayStr)
                            }
                        }
                    }
                }
            }
        }
    }
}
