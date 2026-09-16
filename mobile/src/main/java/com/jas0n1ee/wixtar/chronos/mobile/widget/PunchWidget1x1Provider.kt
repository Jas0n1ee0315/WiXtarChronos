package com.jas0n1ee.wixtar.chronos.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jas0n1ee.wixtar.chronos.mobile.R

class PunchWidget1x1Provider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAppWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAppWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            layoutId: Int = R.layout.widget_punch_1x1
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, layoutId)

                val intent = Intent(context, PunchWidgetActionReceiver::class.java).apply {
                    action = PunchWidgetActionReceiver.ACTION_PUNCH_CARD
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                views.setOnClickPendingIntent(R.id.widget_1x1_root, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
