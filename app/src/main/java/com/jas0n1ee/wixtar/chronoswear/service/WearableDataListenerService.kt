package com.jas0n1ee.wixtar.chronoswear.service

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronoswear.tile.MainTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private val _messageEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
        val messageEvents: SharedFlow<Pair<String, String>> = _messageEvents
    }

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
                    Log.d("WearableDataListener", "Received & saved settings on Watch")
                }
            } else if (uri.path == WearableSyncManager.CLOCK_STATUS_PATH) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val clockIn = dataMap.getString(WearableSyncManager.KEY_CLOCK_IN) ?: ""
                val clockOut = dataMap.getString(WearableSyncManager.KEY_CLOCK_OUT) ?: ""

                scope.launch {
                    if (clockIn.isNotBlank() || clockOut.isNotBlank()) {
                        storage.saveClockDataWithSources(clockIn, "來自手機", clockOut, "來自手機")
                    } else {
                        storage.clearClockData()
                    }
                    MainTileService.forceUpdate(applicationContext)
                    Log.d("WearableDataListener", "Received & saved clock status on Watch: in=$clockIn, out=$clockOut")
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val path = messageEvent.path
        val text = String(messageEvent.data)
        Log.d("WearableDataListener", "Message received on Watch: path=$path, text=$text")
        scope.launch {
            _messageEvents.emit(path to text)
        }
    }
}
