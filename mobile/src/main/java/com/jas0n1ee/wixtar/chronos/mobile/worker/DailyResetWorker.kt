package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.service.WiXtarTileService
import kotlinx.coroutines.flow.first
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
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = AppStorage(applicationContext)
        storage.clearClockDataIfExpired()

        // Re-schedule Auto Punch and Lunch DND for the new day
        AutoPunchScheduler.scheduleAutoPunch(applicationContext)

        val lunchDndEnabled = storage.lunchDndEnabled.first()
        if (lunchDndEnabled) {
            val dndStart = storage.lunchDndStartTime.first()
            val dndEnd = storage.lunchDndEndTime.first()
            MobileNotificationHelper(applicationContext).scheduleLunchDndReminders(dndStart, dndEnd)
        }

        // Update Quick Settings Tile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    applicationContext,
                    ComponentName(applicationContext, WiXtarTileService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update App Widgets
        try {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

            val ids2x1 = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget2x1Provider::class.java))
            if (ids2x1.isNotEmpty()) PunchWidget2x1Provider.updateAppWidgets(applicationContext, appWidgetManager, ids2x1)

            val ids2x1Trans = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget2x1TranslucentProvider::class.java))
            if (ids2x1Trans.isNotEmpty()) PunchWidget2x1TranslucentProvider.updateAppWidgets(applicationContext, appWidgetManager, ids2x1Trans)

            val idsStadium = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidgetStadiumProvider::class.java))
            if (idsStadium.isNotEmpty()) PunchWidgetStadiumProvider.updateAppWidgets(applicationContext, appWidgetManager, idsStadium)

            val idsStadiumTrans = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidgetStadiumTranslucentProvider::class.java))
            if (idsStadiumTrans.isNotEmpty()) PunchWidgetStadiumTranslucentProvider.updateAppWidgets(applicationContext, appWidgetManager, idsStadiumTrans)

            val idsCircle = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidgetCircleProvider::class.java))
            if (idsCircle.isNotEmpty()) PunchWidgetCircleProvider.updateAppWidgets(applicationContext, appWidgetManager, idsCircle)

            val idsCircleTrans = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidgetCircleTranslucentProvider::class.java))
            if (idsCircleTrans.isNotEmpty()) PunchWidgetCircleTranslucentProvider.updateAppWidgets(applicationContext, appWidgetManager, idsCircleTrans)

            val ids1x1 = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget1x1Provider::class.java))
            if (ids1x1.isNotEmpty()) PunchWidget1x1Provider.updateAppWidgets(applicationContext, appWidgetManager, ids1x1)

            val ids1x1Trans = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget1x1TranslucentProvider::class.java))
            if (ids1x1Trans.isNotEmpty()) PunchWidget1x1TranslucentProvider.updateAppWidgets(applicationContext, appWidgetManager, ids1x1Trans)

            val ids1x1Circle = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget1x1CircleProvider::class.java))
            if (ids1x1Circle.isNotEmpty()) PunchWidget1x1CircleProvider.updateAppWidgets(applicationContext, appWidgetManager, ids1x1Circle)

            val ids1x1CircleTrans = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, PunchWidget1x1CircleTranslucentProvider::class.java))
            if (ids1x1CircleTrans.isNotEmpty()) PunchWidget1x1CircleTranslucentProvider.updateAppWidgets(applicationContext, appWidgetManager, ids1x1CircleTrans)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }

    companion object {
        fun scheduleDailyReset(context: Context) {
            try {
                val now = LocalDateTime.now()
                val midnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                val initialDelayMinutes = Duration.between(now, midnight).toMinutes().coerceAtLeast(1)

                val resetRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "DailyMidnightReset",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    resetRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
