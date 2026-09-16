package com.jas0n1ee.wixtar.chronos.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PunchWidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PUNCH_CARD = "com.jas0n1ee.wixtar.chronos.ACTION_PUNCH_CARD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PUNCH_CARD) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val storage = AppStorage(context)
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
                        val time = result.getOrNull() ?: ""
                        val typeStr = if (isClockingInAction) "上班卡" else "下班卡"
                        if (isClockingInAction) {
                            storage.saveClockIn(time)
                            notificationHelper.scheduleReminders(time)
                        } else {
                            storage.saveClockOut(time)
                        }

                        notificationHelper.sendNotification("WiXtar 桌面打卡成功", "已成功打$typeStr ($time)")
                        Log.d("PunchWidgetReceiver", "Widget punched card successfully: $time")
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                        notificationHelper.sendNotification("WiXtar 桌面打卡失敗", errorMsg)
                        Log.e("PunchWidgetReceiver", "Widget punch failed: $errorMsg")
                    }

                    // Update all widgets
                    updateAllWidgets(context)
                } catch (e: Exception) {
                    Log.e("PunchWidgetReceiver", "Error processing widget punch", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update 1x1
        val ids1x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget1x1Provider::class.java))
        if (ids1x1.isNotEmpty()) {
            PunchWidget1x1Provider().onUpdate(context, appWidgetManager, ids1x1)
        }

        // Update 4x1
        val ids4x1 = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidget4x1Provider::class.java))
        if (ids4x1.isNotEmpty()) {
            PunchWidget4x1Provider().onUpdate(context, appWidgetManager, ids4x1)
        }
    }
}
