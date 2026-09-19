package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class AutoClockOutWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = AppStorage(applicationContext)
        val repository = ClockRepository(storage)
        val notificationHelper = MobileNotificationHelper(applicationContext)

        val autoOut = storage.autoClockOutEnabled.first()
        if (!autoOut) return Result.success()

        val currentIn = storage.clockInTime.first()
        val currentOut = storage.clockOutTime.first()

        if (currentIn.isNullOrBlank() || !currentOut.isNullOrBlank()) {
            return Result.success()
        }

        val empId = storage.empId.first()
        val pass = storage.password.first()
        val cookie = storage.cookie.first()
        val enc = storage.enc.first()
        val gps = storage.gpsLocation.first()

        // Delay 5 seconds to prevent clock skew with HR server
        delay(5000)

        val punchResult = repository.clockIn(empId, pass, cookie, enc, gps)
        if (punchResult.isSuccess) {
            val time = punchResult.getOrNull() ?: ""
            storage.saveClockOut(time)
            notificationHelper.sendAutoClockOutSuccessNotification(time)
            updateAllWidgets(applicationContext)
        } else {
            val errorMsg = punchResult.exceptionOrNull()?.message ?: "自動打下班卡失敗"
            notificationHelper.sendManualPunchNotification("WiXtar 自動打下班卡失敗", errorMsg)
        }

        return Result.success()
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
