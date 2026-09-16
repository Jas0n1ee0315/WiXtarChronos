package com.jas0n1ee.wixtar.chronos.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PunchWidgetCircleProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateAppWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        suspend fun updateAppWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val storage = AppStorage(context)
            val clockIn = storage.clockInTime.first()
            val clockOut = storage.clockOutTime.first()

            val inStr = if (!clockIn.isNullOrBlank()) "上班: ${clockIn.substringBefore(" ")}" else "上班: --:--"
            val outStr = if (!clockOut.isNullOrBlank()) "下班: ${clockOut.substringBefore(" ")}" else "下班: --:--"

            val intent = Intent(context, PunchWidgetActionReceiver::class.java).apply {
                action = PunchWidgetActionReceiver.ACTION_PUNCH_CARD
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_punch_circle)
                views.setTextViewText(R.id.widget_text_clock_in, inStr)
                views.setTextViewText(R.id.widget_text_clock_out, outStr)
                views.setOnClickPendingIntent(R.id.widget_circle_root, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
