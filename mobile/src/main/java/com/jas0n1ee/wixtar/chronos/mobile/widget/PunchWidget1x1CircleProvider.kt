package com.jas0n1ee.wixtar.chronos.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.jas0n1ee.wixtar.chronos.mobile.R

class PunchWidget1x1CircleProvider : AppWidgetProvider() {

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
            appWidgetIds: IntArray
        ) {
            PunchWidget1x1Provider.updateAppWidgets(
                context,
                appWidgetManager,
                appWidgetIds,
                R.layout.widget_punch_1x1_circle
            )
        }
    }
}
