package com.jas0n1ee.wixtar.chronos.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared helper for scheduling exact 00:00:01 midnight reset alarms across mobile and wear apps.
 */
object MidnightAlarmHelper {
    private const val TAG = "MidnightAlarmHelper"

    fun millisUntilNextMidnight(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).plusSeconds(1)
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)
    }

    fun scheduleNextMidnightAlarm(
        context: Context,
        receiverClass: Class<out BroadcastReceiver>,
        action: String,
        requestCode: Int
    ) {
        try {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appContext, receiverClass).apply {
                this.action = action
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val zone = ZoneId.systemDefault()
            val nextMidnightMillis = ZonedDateTime.now(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .plusSeconds(1)
                .toInstant()
                .toEpochMilli()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled midnight alarm (${receiverClass.simpleName}) at $nextMidnightMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule midnight alarm for ${receiverClass.simpleName}", e)
        }
    }
}
