package com.jas0n1ee.wixtar.chronos.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.VibrationUtil
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class PunchWidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PUNCH_CARD = "com.jas0n1ee.wixtar.chronos.ACTION_PUNCH_CARD"
        const val ACTION_UPDATE_WIDGETS = "com.jas0n1ee.wixtar.chronos.UPDATE_WIDGETS"

        @JvmStatic
        fun triggerUpdateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                updateAllWidgets(context)
            }
        }

        suspend fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                val ids2x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget2x1Provider::class.java))
                if (ids2x1.isNotEmpty()) PunchWidget2x1Provider.updateAppWidgets(context, appWidgetManager, ids2x1)

                val ids2x1Trans = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget2x1TranslucentProvider::class.java))
                if (ids2x1Trans.isNotEmpty()) PunchWidget2x1TranslucentProvider.updateAppWidgets(context, appWidgetManager, ids2x1Trans)

                val idsStadium = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidgetStadiumProvider::class.java))
                if (idsStadium.isNotEmpty()) PunchWidgetStadiumProvider.updateAppWidgets(context, appWidgetManager, idsStadium)

                val idsStadiumTrans = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidgetStadiumTranslucentProvider::class.java))
                if (idsStadiumTrans.isNotEmpty()) PunchWidgetStadiumTranslucentProvider.updateAppWidgets(context, appWidgetManager, idsStadiumTrans)

                val idsCircle = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidgetCircleProvider::class.java))
                if (idsCircle.isNotEmpty()) PunchWidgetCircleProvider.updateAppWidgets(context, appWidgetManager, idsCircle)

                val idsCircleTrans = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidgetCircleTranslucentProvider::class.java))
                if (idsCircleTrans.isNotEmpty()) PunchWidgetCircleTranslucentProvider.updateAppWidgets(context, appWidgetManager, idsCircleTrans)

                val ids1x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget1x1Provider::class.java))
                if (ids1x1.isNotEmpty()) PunchWidget1x1Provider.updateAppWidgets(context, appWidgetManager, ids1x1)

                val ids1x1Trans = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget1x1TranslucentProvider::class.java))
                if (ids1x1Trans.isNotEmpty()) PunchWidget1x1TranslucentProvider.updateAppWidgets(context, appWidgetManager, ids1x1Trans)

                val ids1x1Circle = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget1x1CircleProvider::class.java))
                if (ids1x1Circle.isNotEmpty()) PunchWidget1x1CircleProvider.updateAppWidgets(context, appWidgetManager, ids1x1Circle)

                val ids1x1CircleTrans = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget1x1CircleTranslucentProvider::class.java))
                if (ids1x1CircleTrans.isNotEmpty()) PunchWidget1x1CircleTranslucentProvider.updateAppWidgets(context, appWidgetManager, ids1x1CircleTrans)
            } catch (e: Exception) {
                Log.e("PunchWidgetReceiver", "Failed to update widgets", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_UPDATE_WIDGETS || action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppStorage(context).clearClockDataIfExpired()
                    updateAllWidgets(context)
                } finally {
                    pendingResult?.finish()
                }
            }
            return
        }

        if (action == ACTION_PUNCH_CARD) {
            VibrationUtil.vibrateClick(context)
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val storage = AppStorage(context)
                    storage.clearClockDataIfExpired()
                    val repository = ClockRepository(storage)
                    val notificationHelper = MobileNotificationHelper(context)

                    val empId = storage.empId.first()
                    val pass = storage.password.first()
                    val cookie = storage.cookie.first()
                    val enc = storage.enc.first()
                    val gps = storage.gpsLocation.first()

                    val currentIn = storage.clockInTime.first()
                    val isClockingInAction = currentIn.isNullOrBlank()

                    val result = repository.clockIn(empId, pass, cookie, enc, gps)
                    if (result.isSuccess) {
                        VibrationUtil.vibrateShortSuccess(context)
                        val time = result.getOrNull() ?: ""
                        val typeStr = if (isClockingInAction) "上班卡" else "下班卡"
                        if (isClockingInAction) {
                            storage.saveClockIn(time)
                            notificationHelper.scheduleReminders(time)
                        } else {
                            storage.saveClockOut(time)
                        }

                        val enableWatchSync = storage.enableWatchSync.first()
                        if (enableWatchSync) {
                            val todayStr = LocalDate.now().toString()
                            val updatedIn = if (isClockingInAction) time else storage.clockInTime.first()
                            val updatedOut = if (!isClockingInAction) time else storage.clockOutTime.first()
                            WearableSyncManager.syncClockStatus(context, updatedIn, updatedOut, todayStr)
                        }

                        notificationHelper.sendNotification("WiXtar 桌面打卡成功", "已成功打$typeStr ($time)")
                        Log.d("PunchWidgetReceiver", "Widget punched card successfully: $time")
                    } else {
                        VibrationUtil.vibrateLongFailure(context)
                        val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                        notificationHelper.sendNotification("WiXtar 桌面打卡失敗", errorMsg)
                        Log.e("PunchWidgetReceiver", "Widget punch failed: $errorMsg")
                    }

                    // Update all widgets
                    updateAllWidgets(context)
                } catch (e: Exception) {
                    Log.e("PunchWidgetReceiver", "Error processing widget punch", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
