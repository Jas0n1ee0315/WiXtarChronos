package com.jas0n1ee.wixtar.chronos.mobile.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WiXtarTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()

        scope.launch {
            val storage = AppStorage(applicationContext)
            val repository = ClockRepository(storage)
            val notificationHelper = MobileNotificationHelper(applicationContext)

            val empId = storage.empId.first()
            val pass = storage.password.first()
            val cookie = storage.cookie.first()
            val enc = storage.enc.first()
            val gps = storage.gpsLocation.first()

            val currentIn = storage.clockInTime.first()
            val isClockingInAction = currentIn.isNullOrBlank()

            val result = repository.clockIn(empId, pass, cookie, enc, gps)
            if (result.isSuccess) {
                val time = result.getOrNull() ?: ""
                val actionTypeStr = if (isClockingInAction) "上班卡" else "下班卡"
                if (isClockingInAction) {
                    storage.saveClockIn(time)
                    notificationHelper.scheduleReminders(time)
                } else {
                    storage.saveClockOut(time)
                }

                notificationHelper.sendNotification("WiXtar 打卡成功", "已成功打$actionTypeStr ($time)")
                Log.d("WiXtarTileService", "Quick Settings Tile punched card successfully: $time")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                notificationHelper.sendNotification("WiXtar 打卡失敗", errorMsg)
                Log.e("WiXtarTileService", "Quick Settings Tile punch failed: $errorMsg")
            }

            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "WiXtar 打卡"
            updateTile()
        }
    }
}
