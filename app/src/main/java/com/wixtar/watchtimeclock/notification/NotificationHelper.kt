package com.wixtar.watchtimeclock.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wixtar.watchtimeclock.R
import com.wixtar.watchtimeclock.presentation.MainActivity
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "off_work_reminders"
        const val DEBUG_CHANNEL_ID = "debug_notifications"
        const val NOTIFICATION_ID_BEFORE = 1001
        const val NOTIFICATION_ID_NOW = 1002
        const val NOTIFICATION_ID_DEBUG = 2001
        const val ACTION_CLOCK_OUT = "com.wixtar.watchtimeclock.ACTION_CLOCK_OUT"
        const val ACTION_REMINDER_10 = "SCHEDULE_REMINDER_10"
        const val ACTION_REMINDER_NOW = "SCHEDULE_REMINDER_NOW"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val name = "下班提醒"
        val descriptionText = "提醒您即將下班或已到下班時間"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }
        notificationManager.createNotificationChannel(channel)

        val debugName = "Debug 資訊"
        val debugDesc = "顯示雲端同步的偵錯資訊"
        val debugChannel = NotificationChannel(DEBUG_CHANNEL_ID, debugName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = debugDesc
        }
        notificationManager.createNotificationChannel(debugChannel)
    }

    fun showDebugNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_DEBUG, builder.build())
    }

    fun scheduleReminders(clockInTimeStr: String) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val clockInTime = LocalTime.parse(clockInTimeStr, formatter)
        val offWorkTime = clockInTime.plusHours(8)
        val tenMinsBefore = offWorkTime.minusMinutes(10)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Schedule 10 mins before
        val intent10 = Intent(context, OffWorkReceiver::class.java).apply {
            action = ACTION_REMINDER_10
        }
        val pendingIntent10 = PendingIntent.getBroadcast(context, 10, intent10, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val time10 = ZonedDateTime.now(ZoneId.systemDefault())
            .withHour(tenMinsBefore.hour)
            .withMinute(tenMinsBefore.minute)
            .withSecond(0)
            .toInstant()
            .toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time10, pendingIntent10)

        // Schedule now
        val intentNow = Intent(context, OffWorkReceiver::class.java).apply {
            action = ACTION_REMINDER_NOW
        }
        val pendingIntentNow = PendingIntent.getBroadcast(context, 11, intentNow, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val timeNow = ZonedDateTime.now(ZoneId.systemDefault())
            .withHour(offWorkTime.hour)
            .withMinute(offWorkTime.minute)
            .withSecond(0)
            .toInstant()
            .toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeNow, pendingIntentNow)
    }

    fun showTenMinutesBeforeNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("下班提醒")
            .setContentText("再過10分鐘就可以下班囉！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BEFORE, builder.build())
    }

    fun showOffWorkNowNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val clockOutIntent = Intent(context, OffWorkReceiver::class.java).apply {
            action = ACTION_CLOCK_OUT
        }
        val clockOutPendingIntent = PendingIntent.getBroadcast(context, 1, clockOutIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("到下班時間囉")
            .setContentText("辛苦了，記得打下班卡喔！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(0, "打下班卡", clockOutPendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_NOW, builder.build())
    }
}
