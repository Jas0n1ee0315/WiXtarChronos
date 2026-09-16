package com.jas0n1ee.wixtar.chronos.mobile.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MobileNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "wixtar_off_work_channel"
        const val CHANNEL_NAME = "WiXtar 打卡下班提醒"
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
                description = "WiXtar 打卡可下班時間提醒"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminders(clockInTimeStr: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val clockInTime = LocalTime.parse(clockInTimeStr.substringBefore(" "), formatter)
            val offWorkTime = clockInTime.plusHours(9)
            val tenMinsBefore = offWorkTime.minusMinutes(10)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val time10 = ZonedDateTime.now(ZoneId.systemDefault())
                .withHour(tenMinsBefore.hour)
                .withMinute(tenMinsBefore.minute)
                .withSecond(0)
                .toInstant()
                .toEpochMilli()

            val timeOff = ZonedDateTime.now(ZoneId.systemDefault())
                .withHour(offWorkTime.hour)
                .withMinute(offWorkTime.minute)
                .withSecond(0)
                .toInstant()
                .toEpochMilli()

            if (System.currentTimeMillis() < time10) {
                val intent10 = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("title", "下班預警提醒")
                    putExtra("message", "距離可下班時間還有 10 分鐘！")
                }
                val pendingIntent10 = PendingIntent.getBroadcast(
                    context, 101, intent10, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time10, pendingIntent10)
            }

            if (System.currentTimeMillis() < timeOff) {
                val intentOff = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("title", "可以下班囉！")
                    putExtra("message", "您已達到 9 小時工作時間，記得打下班卡哦！")
                }
                val pendingIntentOff = PendingIntent.getBroadcast(
                    context, 102, intentOff, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeOff, pendingIntentOff)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

class NotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "WiXtar 打卡提醒"
        val message = intent.getStringExtra("message") ?: "時間到了！"
        MobileNotificationHelper(context).sendNotification(title, message)
    }
}
