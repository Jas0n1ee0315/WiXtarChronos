package com.jas0n1ee.wixtar.chronos.watch.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jas0n1ee.wixtar.chronos.watch.presentation.MainActivity
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "wixtar_clock_channel"
        const val CHANNEL_NAME = "WiXtar 打卡提醒"
        const val ACTION_REMINDER_10 = "SCHEDULE_REMINDER_10"
        const val ACTION_REMINDER_NOW = "SCHEDULE_REMINDER_NOW"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "星益欣打卡時間提醒"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminders(clockInTimeStr: String) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val clockInTime = LocalTime.parse(clockInTimeStr, formatter)
        val offWorkTime = clockInTime.plusHours(9)
        val tenMinsBefore = offWorkTime.minusMinutes(10)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("下班提醒")
            .setContentText("再過10分鐘就可以下班囉！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
    }

    fun showOffWorkNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("下班提醒")
            .setContentText("已滿 9 小時，記得打卡哦！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1002, builder.build())
    }
}
