package com.jas0n1ee.wixtar.chronos.mobile.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class WiXtarTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()

        scope.launch {
            val storage = AppStorage(applicationContext)
            storage.clearClockDataIfExpired()
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
                    val notifyBefore = storage.notifyBeforeOffWork.first()
                    val notifyOn = storage.notifyOnOffWork.first()
                    val advanceMinutes = storage.notifyAdvanceMinutes.first()
                    val useLive = storage.useLiveUpdates.first()
                    if (notifyBefore || notifyOn) {
                        notificationHelper.scheduleReminders(time, advanceMinutes, useLive)
                    }
                } else {
                    storage.saveClockOut(time)
                    notificationHelper.cancelReminders()
                }

                val enableWatchSync = storage.enableWatchSync.first()
                if (enableWatchSync) {
                    val todayStr = LocalDate.now().toString()
                    val updatedIn = if (isClockingInAction) time else storage.clockInTime.first()
                    val updatedOut = if (!isClockingInAction) time else storage.clockOutTime.first()
                    WearableSyncManager.syncClockStatus(applicationContext, updatedIn, updatedOut, todayStr)
                }

                notificationHelper.sendNotification("WiXtar 打卡成功", "已成功打$actionTypeStr ($time)")
                Log.d("WiXtarTileService", "Quick Settings Tile punched card successfully: $time")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                notificationHelper.sendNotification("WiXtar 打卡失敗", errorMsg)
                Log.e("WiXtarTileService", "Quick Settings Tile punch failed: $errorMsg")
            }

            updateTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            updateTileState()
        }
    }

    private suspend fun updateTileState() {
        val tile = qsTile ?: return
        val storage = AppStorage(applicationContext)
        storage.clearClockDataIfExpired()

        val clockIn = storage.clockInTime.first()
        val clockOut = storage.clockOutTime.first()

        val rawTime = when {
            !clockOut.isNullOrBlank() -> clockOut
            !clockIn.isNullOrBlank() -> clockIn
            else -> null
        }

        val subtitleText = formatSubtitleTime(rawTime)

        tile.state = Tile.STATE_ACTIVE
        tile.label = "打卡"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        tile.updateTile()
    }

    private fun formatSubtitleTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "-- : --"
        val clean = raw.substringBefore(" ").trim()
        if (clean.contains(":")) {
            val parts = clean.split(":")
            if (parts.size >= 2) {
                val hh = parts[0].padStart(2, '0')
                val mm = parts[1].padStart(2, '0')
                return "$hh : $mm"
            }
        }
        return "-- : --"
    }
}
