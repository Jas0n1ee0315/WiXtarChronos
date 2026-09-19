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
import com.jas0n1ee.wixtar.chronos.mobile.receiver.LunchDndReceiver
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
        const val LUNCH_DND_START_NOTIFICATION_ID = 5551
        const val LUNCH_DND_END_NOTIFICATION_ID = 5552
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

    fun isNotificationPolicyAccessGranted(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun openNotificationPolicySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("MobileNotificationHelper", "Failed to open notification policy settings", e)
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
            
            val offWorkTime = if (clockInTimeStr.isNotBlank()) {
                val clockInTime = LocalTime.parse(clockInTimeStr.substringBefore(" ").trim(), formatter)
                clockInTime.plusHours(9)
            } else {
                return
            }

            val advanceTime = offWorkTime.minusMinutes(advanceMinutes.toLong())
            val now = ZonedDateTime.now(ZoneId.systemDefault())

            var startAdvanceDateTime = now
                .withHour(advanceTime.hour)
                .withMinute(advanceTime.minute)
                .withSecond(0)

            if (startAdvanceDateTime.isBefore(now) && startAdvanceDateTime.plusMinutes(1).isBefore(now)) {
                startAdvanceDateTime = startAdvanceDateTime.plusDays(1)
            }

            var startOffDateTime = now
                .withHour(offWorkTime.hour)
                .withMinute(offWorkTime.minute)
                .withSecond(0)

            if (startOffDateTime.isBefore(now) && startOffDateTime.plusMinutes(1).isBefore(now)) {
                startOffDateTime = startOffDateTime.plusDays(1)
            }

            val timeAdvance = startAdvanceDateTime.toInstant().toEpochMilli()
            val timeOff = startOffDateTime.toInstant().toEpochMilli()

            if (System.currentTimeMillis() < timeAdvance) {
                setAlarm(
                    triggerAtMillis = timeAdvance,
                    requestCode = ADVANCE_NOTIFICATION_ID,
                    title = "提前通知",
                    message = "距離可下班時間還有 $advanceMinutes 分鐘！",
                    useLiveUpdates = useLiveUpdates,
                    offWorkTimeMillis = timeOff
                )
                Log.d("MobileNotificationHelper", "Scheduled ADVANCE_NOTIFICATION at $startAdvanceDateTime for offWork $startOffDateTime")
            }

            if (System.currentTimeMillis() < timeOff) {
                setAlarm(
                    triggerAtMillis = timeOff,
                    requestCode = OFF_WORK_NOTIFICATION_ID,
                    title = "可下班通知",
                    message = "您已達到 9 小時工作時間，記得打下班卡哦！"
                )
                Log.d("MobileNotificationHelper", "Scheduled OFF_WORK_NOTIFICATION at $startOffDateTime")
            }
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Error in scheduleReminders", e)
        }
    }

    fun scheduleLunchDndReminders(startTimeStr: String = "12:00", endTimeStr: String = "13:00") {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val startTime = LocalTime.parse(startTimeStr.trim(), formatter)
            val endTime = LocalTime.parse(endTimeStr.trim(), formatter)
            val now = ZonedDateTime.now(ZoneId.systemDefault())

            var startDateTime = now.withHour(startTime.hour).withMinute(startTime.minute).withSecond(0)
            if (startDateTime.isBefore(now)) {
                startDateTime = startDateTime.plusDays(1)
            }

            var endDateTime = now.withHour(endTime.hour).withMinute(endTime.minute).withSecond(0)
            if (endDateTime.isBefore(now)) {
                endDateTime = endDateTime.plusDays(1)
            }

            val startIntent = Intent(context, LunchDndReceiver::class.java).apply {
                action = LunchDndReceiver.ACTION_LUNCH_DND_START
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context, LUNCH_DND_START_NOTIFICATION_ID, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val endIntent = Intent(context, LunchDndReceiver::class.java).apply {
                action = LunchDndReceiver.ACTION_LUNCH_DND_END
            }
            val endPendingIntent = PendingIntent.getBroadcast(
                context, LUNCH_DND_END_NOTIFICATION_ID, endIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(startDateTime.toInstant().toEpochMilli(), startPendingIntent), startPendingIntent)
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(endDateTime.toInstant().toEpochMilli(), endPendingIntent), endPendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startDateTime.toInstant().toEpochMilli(), startPendingIntent)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endDateTime.toInstant().toEpochMilli(), endPendingIntent)
            }
            Log.d("MobileNotificationHelper", "Lunch DND alarms set for $startTimeStr and $endTimeStr")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to schedule Lunch DND reminders", e)
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

    fun postLiveUpdateNotification(
        offWorkTimeMillis: Long,
        title: String = "下班時間倒數",
        text: String = "再過不久就可以下班囉"
    ) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Live Update")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(offWorkTimeMillis)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(pendingIntent)
                .setGroupSummary(false)
                .setColorized(false)

            if (Build.VERSION.SDK_INT >= 35) {
                builder.setRequestPromotedOngoing(true)
            } else {
                builder.extras.putBoolean("android.requestPromotedOngoing", true)
            }

            manager.notify(LIVE_UPDATE_NOTIFICATION_ID, builder.build())
            Log.d("MobileNotificationHelper", "Promoted Live Update notification posted with dynamic Chronometer Status Chip")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to post Promoted Live Update notification", e)
        }
    }

    fun transformLiveUpdateToOffWork() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val punchIntent = Intent(context, OffWorkNotificationActionReceiver::class.java).apply {
                action = OffWorkNotificationActionReceiver.ACTION_OFF_WORK_PUNCH
            }
            val punchPendingIntent = PendingIntent.getBroadcast(
                context, 1002, punchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle("可以下班囉，辛苦了")
                .setContentText("要記得打卡呦")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOngoing(false)
                .setUsesChronometer(false)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_cloud_clock,
                    "打卡",
                    punchPendingIntent
                )

            manager.notify(LIVE_UPDATE_NOTIFICATION_ID, builder.build())
            Log.d("MobileNotificationHelper", "Transformed Live Update into off-work prompt notification in-place")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to transform Live Update notification", e)
        }
    }

    fun sendOffWorkPromptNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val punchIntent = Intent(context, OffWorkNotificationActionReceiver::class.java).apply {
                action = OffWorkNotificationActionReceiver.ACTION_OFF_WORK_PUNCH
            }
            val punchPendingIntent = PendingIntent.getBroadcast(
                context, 1002, punchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle("可以下班囉，辛苦了")
                .setContentText("要記得打卡呦")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_cloud_clock,
                    "打卡",
                    punchPendingIntent
                )

            manager.notify(OFF_WORK_NOTIFICATION_ID, builder.build())
            Log.d("MobileNotificationHelper", "Sent off-work prompt notification")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to send off-work prompt notification", e)
        }
    }

    fun sendClockOutSuccessNotification(time: String) {
        try {
            cancelLiveUpdateNotification()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle("打卡成功")
                .setContentText("時間 $time，可以放心下班")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            manager.notify(OFF_WORK_NOTIFICATION_ID, builder.build())
            Log.d("MobileNotificationHelper", "Sent clock out success notification: $time")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to send clock out success notification", e)
        }
    }

    fun sendAutoClockOutSuccessNotification(time: String) {
        try {
            cancelLiveUpdateNotification()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cloud_clock)
                .setContentTitle("可以下班囉，辛苦了")
                .setContentText("已在 $time 幫您打卡，可以放心下班")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            manager.notify(OFF_WORK_NOTIFICATION_ID, builder.build())
            Log.d("MobileNotificationHelper", "Sent auto clock out success notification: $time")
        } catch (e: Exception) {
            Log.e("MobileNotificationHelper", "Failed to send auto clock out success notification", e)
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
                        pendingResult?.finish()
                    }
                }
            } else if (requestCode == MobileNotificationHelper.ADVANCE_NOTIFICATION_ID && useLiveUpdates && offWorkTimeMillis > 0) {
                helper.postLiveUpdateNotification(offWorkTimeMillis)
            } else if (requestCode == MobileNotificationHelper.OFF_WORK_NOTIFICATION_ID) {
                val pendingResult = try { goAsync() } catch (_: Exception) { null }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val storage = AppStorage(context)
                        val clockOut = storage.clockOutTime.first()
                        val autoOut = storage.autoClockOutEnabled.first()
                        val notifyBefore = storage.notifyBeforeOffWork.first()
                        val useLiveUpdates = storage.useLiveUpdates.first()
                        val notifyOn = storage.notifyOnOffWork.first()

                        if (!clockOut.isNullOrBlank()) {
                            helper.cancelLiveUpdateNotification()
                            return@launch
                        }

                        if (autoOut) {
                            // Scenarios 3 & 4: AutoClockOutWorker will handle auto clock-out and notify "已在 xx:xx 幫您打卡，可以放心下班"
                            return@launch
                        }

                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val activeNotifications = try { manager.activeNotifications } catch (_: Exception) { emptyArray() }
                        val isLiveUpdateActive = activeNotifications.any { it.id == MobileNotificationHelper.LIVE_UPDATE_NOTIFICATION_ID }

                        Log.d("NotificationReceiver", "OFF_WORK_NOTIFICATION_ID triggered: notifyBefore=$notifyBefore, useLiveUpdates=$useLiveUpdates, notifyOn=$notifyOn, isLiveUpdateActive=$isLiveUpdateActive")

                        if (notifyBefore && useLiveUpdates && isLiveUpdateActive) {
                            // Scenario 2 with Live Update active: transform in-place
                            helper.transformLiveUpdateToOffWork()
                        } else if (notifyOn) {
                            // Scenario 2 without Live Update active
                            helper.cancelLiveUpdateNotification()
                            helper.sendOffWorkPromptNotification()
                        } else {
                            // Scenario 1: notifyOn == false & autoOut == false
                            helper.cancelLiveUpdateNotification()
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationReceiver", "Error handling off work alarm", e)
                    } finally {
                        pendingResult?.finish()
                    }
                }
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
