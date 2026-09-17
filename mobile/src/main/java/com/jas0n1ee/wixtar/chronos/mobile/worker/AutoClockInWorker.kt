package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.TaiwanCalendarRepository
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.service.WiXtarTileService
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget1x1CircleProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget1x1CircleTranslucentProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget1x1Provider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget1x1TranslucentProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget2x1Provider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidget2x1TranslucentProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetCircleProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetCircleTranslucentProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetStadiumProvider
import com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetStadiumTranslucentProvider
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class AutoClockInWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = AppStorage(applicationContext)
        val repository = ClockRepository(storage)
        val notificationHelper = MobileNotificationHelper(applicationContext)
        val calendarRepo = TaiwanCalendarRepository()

        val autoIn = storage.autoClockInEnabled.first()
        if (!autoIn) return Result.success()

        // Check Auto-Punch Pause Days
        val pauseDaysCsv = storage.autoPunchPauseDays.first()
        val todayStr = LocalDate.now().toString()
        val pauseDaysSet = pauseDaysCsv.split(",").map { it.trim() }.toSet()
        if (pauseDaysSet.contains(todayStr)) {
            Log.d("AutoClockInWorker", "Today ($todayStr) is an auto-punch pause day. Auto clock-in skipped.")
            return Result.success()
        }

        val currentIn = storage.clockInTime.first()
        if (!currentIn.isNullOrBlank()) return Result.success() // Already clocked in today

        // 1. Check Taiwan Holiday Calendar
        val calendarResult = calendarRepo.isWorkDay(LocalDate.now())
        if (calendarResult.isFailure) {
            Log.e("AutoClockInWorker", "Failed to check Taiwan calendar", calendarResult.exceptionOrNull())
            notificationHelper.sendManualPunchNotification(
                title = "本日尚未自動打卡",
                message = "不確定今日是否為工作日，請手動打卡"
            )
            return Result.success()
        }

        val isWorkDay = calendarResult.getOrNull() == true
        if (!isWorkDay) {
            Log.d("AutoClockInWorker", "Today is a holiday or weekend. Auto clock-in skipped.")
            return Result.success()
        }

        // 2. Check Flexible Time Randomizing
        val flexibleIn = storage.flexibleClockInEnabled.first()
        val isRandomExecution = inputData.getBoolean("is_random_execution", false)

        if (flexibleIn && !isRandomExecution) {
            val workStartStr = storage.workStartTime.first()
            val flexibleEndStr = storage.flexibleEndTime.first()

            val startTime = parseLocalTime(workStartStr, 8, 30)
            val endTime = parseLocalTime(flexibleEndStr, 9, 30)

            val nowTime = LocalTime.now()
            if (nowTime.isBefore(endTime)) {
                val startMinute = if (nowTime.isAfter(startTime)) nowTime.hour * 60 + nowTime.minute else startTime.hour * 60 + startTime.minute
                val endMinute = (endTime.hour * 60 + endTime.minute).coerceAtLeast(startMinute)

                val randomMinute = Random.nextInt(startMinute, endMinute + 1)
                val targetLocalTime = LocalTime.of(randomMinute / 60, randomMinute % 60)

                val nowDateTime = LocalDateTime.now()
                val targetDateTime = nowDateTime.withHour(targetLocalTime.hour).withMinute(targetLocalTime.minute).withSecond(0).withNano(0)

                if (targetDateTime.isAfter(nowDateTime)) {
                    val delaySeconds = Duration.between(nowDateTime, targetDateTime).seconds.coerceAtLeast(1)
                    Log.d("AutoClockInWorker", "Flexible clock-in scheduled random time at $targetLocalTime (delay ${delaySeconds}s)")

                    val randomData = Data.Builder().putBoolean("is_random_execution", true).build()
                    val randomRequest = OneTimeWorkRequestBuilder<AutoClockInWorker>()
                        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                        .setInputData(randomData)
                        .build()

                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "AutoClockInRandomWork",
                        ExistingWorkPolicy.REPLACE,
                        randomRequest
                    )
                    return Result.success()
                }
            }
        }

        // 3. Execute Punch In
        val empId = storage.empId.first()
        val pass = storage.password.first()
        val cookie = storage.cookie.first()
        val enc = storage.enc.first()
        val gps = storage.gpsLocation.first()

        val punchResult = repository.clockIn(empId, pass, cookie, enc, gps)
        if (punchResult.isSuccess) {
            val time = punchResult.getOrNull() ?: ""
            storage.saveClockIn(time)
            notificationHelper.scheduleReminders(time)
            notificationHelper.sendNotification("WiXtar 自動打卡成功", "已成功打上班卡 ($time)")
            updateAllWidgets(applicationContext)

            // Schedule Auto Clock-Out if enabled
            AutoPunchScheduler.scheduleAutoPunch(applicationContext)
        } else {
            val errorMsg = punchResult.exceptionOrNull()?.message ?: "自動打卡失敗"
            notificationHelper.sendManualPunchNotification("WiXtar 自動打卡失敗", errorMsg)
        }

        return Result.success()
    }

    private fun parseLocalTime(str: String, defaultH: Int, defaultM: Int): LocalTime {
        return try {
            val parts = str.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (_: Exception) {
            LocalTime.of(defaultH, defaultM)
        }
    }

    private suspend fun updateAllWidgets(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(context, ComponentName(context, WiXtarTileService::class.java))
            } catch (_: Exception) {}
        }
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
        } catch (_: Exception) {}
    }
}
