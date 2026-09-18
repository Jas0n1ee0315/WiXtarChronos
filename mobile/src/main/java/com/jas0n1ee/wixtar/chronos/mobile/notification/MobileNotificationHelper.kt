package com.jas0n1ee.wixtar.chronos.mobile.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.mobile.R
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MobileNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "wixtar_off_work_channel"
        const val CHANNEL_NAME = "WiXtar 打卡下班提醒"
        
        const val ADVANCE_NOTIFICATION_ID = 101
        const val OFF_WORK_NOTIFICATION_ID = 102
        const val FORGET_CLOCK_OUT_NOTIFICATION_ID = 7777
        const val LIVE_UPDATE_NOTIFICATION_ID = 8888
        const val FISH_OIL_NOTIFICATION_ID = 9999
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
                description = "WiXtar 打卡可下班時間提醒與魚油提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("MobileNotificationHelper", "Failed to request ignore battery optimizations", e)
            }
        }
    }

    private fun setAlarm(
        triggerAtMillis: Long,
        requestCode: Int,
        title: String,
        message: String,
        useLiveUpdates: Boolean = false,
        offWorkTimeMillis: Long = 0L
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("requestCode", requestCode)
            putExtra("useLiveUpdates", useLiveUpdates)
            putExtra("offWorkTimeMillis", offWorkTimeMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d("MobileNotificationHelper", "Alarm set for request $requestCode at $triggerAtMillis")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to set alarm", e)
        }
    }

    fun scheduleReminders(
        clockInTimeStr: String,
        advanceMinutes: Int = 10,
        useLiveUpdates: Boolean = false
    ) {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val clockInTime = LocalTime.parse(clockInTimeStr.substringBefore(" "), formatter)
            val offWorkTime = clockInTime.plusHours(9)
            val advanceTime = offWorkTime.minusMinutes(advanceMinutes.toLong())

            val now = ZonedDateTime.now(ZoneId.systemDefault())

            val timeAdvance = now
                .withHour(advanceTime.hour)
                .withMinute(advanceTime.minute)
                .withSecond(0)
                .toInstant()
                .toEpochMilli()

            val timeOff = now
                .withHour(offWorkTime.hour)
                .withMinute(offWorkTime.minute)
                .withSecond(0)
                .toInstant()
                .toEpochMilli()

            if (System.currentTimeMillis() < timeAdvance) {
                setAlarm(
                    triggerAtMillis = timeAdvance,
                    requestCode = ADVANCE_NOTIFICATION_ID,
                    title = "提前通知",
                    message = "距離可下班時間還有 $advanceMinutes 分鐘！",
                    useLiveUpdates = useLiveUpdates,
                    offWorkTimeMillis = timeOff
                )
            }

            if (System.currentTimeMillis() < timeOff) {
                setAlarm(
                    triggerAtMillis = timeOff,
                    requestCode = OFF_WORK_NOTIFICATION_ID,
                    title = "可下班通知",
                    message = "您已達到 9 小時工作時間，記得打下班卡哦！"
                )
            }
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Error in scheduleReminders", e)
        }
    }

    fun scheduleForgetClockOutReminders(timeStr: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val targetTime = LocalTime.parse(timeStr.trim(), formatter)
            val now = ZonedDateTime.now(ZoneId.systemDefault())

            var targetDateTime = now
                .withHour(targetTime.hour)
                .withMinute(targetTime.minute)
                .withSecond(0)

            if (targetDateTime.isBefore(now)) {
                targetDateTime = targetDateTime.plusDays(1)
            }

            val millis = targetDateTime.toInstant().toEpochMilli()
            setAlarm(millis, FORGET_CLOCK_OUT_NOTIFICATION_ID, "你忘記打下班卡了！", "趕快打卡吧，不然要送補打卡單了")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Error scheduling forget clock out reminders", e)
        }
    }

    fun scheduleFishOilReminders(timeStr: String, daysCsv: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val targetTime = LocalTime.parse(timeStr.trim(), formatter)
            val selectedDays = daysCsv.split(",").mapNotNull { it.trim().toIntOrNull() }

            val today = LocalDate.now()
            for (i in 0..6) {
                val candidateDate = today.plusDays(i.toLong())
                val dayIso = candidateDate.dayOfWeek.value
                if (selectedDays.contains(dayIso)) {
                    val candidateDateTime = ZonedDateTime.of(candidateDate, targetTime, ZoneId.systemDefault())
                    val millis = candidateDateTime.toInstant().toEpochMilli()
                    if (millis > System.currentTimeMillis()) {
                        setAlarm(millis, FISH_OIL_NOTIFICATION_ID, "吃魚油囉", "該吃魚油了！！！")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Error scheduling fish oil reminders", e)
        }
    }

    fun postLiveUpdateNotification(offWorkTimeMillis: Long) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle("下班時間倒數")
                .setContentText("距離可下班時間即時倒數中")
                .setSubText("即時更新")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(offWorkTimeMillis)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(LIVE_UPDATE_NOTIFICATION_ID, notification)
            Log.d("MobileNotificationHelper", "Live Update notification posted")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to post Live Update notification", e)
        }
    }

    fun cancelLiveUpdateNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(LIVE_UPDATE_NOTIFICATION_ID)
    }

    fun sendManualPunchNotification(title: String, message: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_MANUAL_PUNCH_CARD
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                199,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val action = NotificationCompat.Action.Builder(
                R.drawable.ic_cloud_clock,
                "手動打卡",
                pendingIntent
            ).build()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(action)
                .build()

            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to send manual punch notification", e)
        }
    }

    fun sendNotification(title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val iconRes = if (title.contains("魚油") || notificationId == FISH_OIL_NOTIFICATION_ID) {
                R.drawable.ic_fish
            } else {
                R.drawable.ic_cloud_clock
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)

            if (title.contains("可下班通知") || title.contains("忘記打下班卡")) {
                val punchIntent = Intent(context, OffWorkNotificationActionReceiver::class.java).apply {
                    action = OffWorkNotificationActionReceiver.ACTION_OFF_WORK_PUNCH
                }
                val punchPendingIntent = PendingIntent.getBroadcast(
                    context, 1002, punchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    R.drawable.ic_cloud_clock,
                    "按下打卡",
                    punchPendingIntent
                )
            } else if (title.contains("吃魚油囉") || notificationId == FISH_OIL_NOTIFICATION_ID) {
                val eatenIntent = Intent(context, FishOilActionReceiver::class.java).apply {
                    action = FishOilActionReceiver.ACTION_FISH_OIL_EATEN
                }
                val eatenPendingIntent = PendingIntent.getBroadcast(
                    context, 9999, eatenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    R.drawable.ic_fish,
                    "我吃了",
                    eatenPendingIntent
                )
            }

            manager.notify(notificationId, builder.build())
            Log.d("MobileNotificationHelper", "Notification sent: $title - $message")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to send notification", e)
        }
    }
}

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MANUAL_PUNCH_CARD = "com.jas0n1ee.wixtar.chronos.ACTION_MANUAL_PUNCH_CARD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MANUAL_PUNCH_CARD) {
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val storage = AppStorage(context)
                    val repository = ClockRepository(storage)
                    val notificationHelper = MobileNotificationHelper(context)

                    val empId = storage.empId.first()
                    val pass = storage.password.first()
                    val cookie = storage.cookie.first()
                    val enc = storage.enc.first()
                    val gps = storage.gpsLocation.first()

                    val currentIn = storage.clockInTime.first()
                    val isClockingInAction = currentIn.isNullOrBlank()

                    val result = repository.clockIn(empId, pass, cookie, enc, gps)
                    if (result.isSuccess) {
                        val time = result.getOrNull() ?: ""
                        val typeStr = if (isClockingInAction) "上班卡" else "下班卡"
                        if (isClockingInAction) {
                            storage.saveClockIn(time)
                            notificationHelper.scheduleReminders(time)
                        } else {
                            storage.saveClockOut(time)
                        }
                        notificationHelper.sendNotification("WiXtar 手動打卡成功", "已成功打$typeStr ($time)")
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "打卡失敗"
                        notificationHelper.sendNotification("WiXtar 手動打卡失敗", errorMsg)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        } else {
            val title = intent.getStringExtra("title") ?: "WiXtar 打卡提醒"
            val message = intent.getStringExtra("message") ?: "時間到了！"
            val requestCode = intent.getIntExtra("requestCode", System.currentTimeMillis().toInt())
            val useLiveUpdates = intent.getBooleanExtra("useLiveUpdates", false)
            val offWorkTimeMillis = intent.getLongExtra("offWorkTimeMillis", 0L)
            Log.d("NotificationReceiver", "Received broadcast: $title, req: $requestCode, liveUpdates: $useLiveUpdates")

            val helper = MobileNotificationHelper(context)

            if (requestCode == MobileNotificationHelper.FORGET_CLOCK_OUT_NOTIFICATION_ID) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val storage = AppStorage(context)
                        val clockIn = storage.clockInTime.first()
                        val clockOut = storage.clockOutTime.first()
                        if (!clockIn.isNullOrBlank() && clockOut.isNullOrBlank()) {
                            helper.sendNotification(
                                "你忘記打下班卡了！",
                                "趕快打卡吧，不然要送補打卡單了",
                                MobileNotificationHelper.FORGET_CLOCK_OUT_NOTIFICATION_ID
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationReceiver", "Error checking forget clock out status", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else if (requestCode == MobileNotificationHelper.ADVANCE_NOTIFICATION_ID && useLiveUpdates && offWorkTimeMillis > 0) {
                helper.postLiveUpdateNotification(offWorkTimeMillis)
            } else if (requestCode == MobileNotificationHelper.OFF_WORK_NOTIFICATION_ID) {
                helper.cancelLiveUpdateNotification()
                helper.sendNotification(title, message, MobileNotificationHelper.OFF_WORK_NOTIFICATION_ID)
            } else {
                val targetId = when (requestCode) {
                    MobileNotificationHelper.FISH_OIL_NOTIFICATION_ID -> MobileNotificationHelper.FISH_OIL_NOTIFICATION_ID
                    else -> requestCode
                }
                helper.sendNotification(title, message, targetId)
            }
        }
    }
}
