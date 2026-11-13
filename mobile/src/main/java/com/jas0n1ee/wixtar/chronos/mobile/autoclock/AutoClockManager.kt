package com.jas0n1ee.wixtar.chronos.mobile.autoclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WorkShiftPolicy
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.receiver.AutoClockReceiver
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class AutoClockManager(private val context: Context) {
    companion object {
        const val AUTO_CLOCK_IN_REQUEST_CODE = 9001
        const val AUTO_CLOCK_OUT_REQUEST_CODE = 9002
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val storage = AppStorage(context)
    private val repository = ClockRepository(storage)
    private val notificationHelper = MobileNotificationHelper(context)

    fun scheduleAutoClockIn() {
        try {
            cancelAutoClockIn()
            kotlinx.coroutines.runBlocking {
                val enabled = storage.autoClockInEnabled.first()
                if (!enabled) return@runBlocking

                val workStartStr = storage.workStartTime.first()
                val flexEnabled = storage.flexibleClockInEnabled.first()
                val flexStartStr = storage.flexibleStartTime.first()
                val timeStr = if (flexEnabled && flexStartStr.isNotBlank()) flexStartStr else workStartStr

                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val targetTime = LocalTime.parse(timeStr.trim(), formatter)
                val now = ZonedDateTime.now(ZoneId.systemDefault())

                var targetDateTime = now
                    .withHour(targetTime.hour)
                    .withMinute(targetTime.minute)
                    .withSecond(0)
                    .withNano(0)

                val todayDateStr = LocalDate.now().toString()
                val lastClockDate = storage.lastClockDate.first()
                val clockIn = storage.clockInTime.first()

                // If already clocked in today or target time already passed by > 1 hour, schedule for tomorrow
                if ((todayDateStr == lastClockDate && !clockIn.isNullOrBlank()) || targetDateTime.isBefore(now.minusMinutes(10))) {
                    targetDateTime = targetDateTime.plusDays(1)
                }

                val triggerMillis = targetDateTime.toInstant().toEpochMilli()
                setExactAlarm(triggerMillis, AUTO_CLOCK_IN_REQUEST_CODE, AutoClockReceiver.ACTION_AUTO_CLOCK_IN)
                Log.d("AutoClockManager", "Scheduled AUTO_CLOCK_IN at $targetDateTime")
            }
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to schedule Auto Clock In", e)
        }
    }

    fun scheduleSpecificClockIn(targetTime: LocalTime) {
        try {
            cancelAutoClockIn()
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            var targetDateTime = now
                .withHour(targetTime.hour)
                .withMinute(targetTime.minute)
                .withSecond(targetTime.second)
                .withNano(0)

            if (targetDateTime.isBefore(now)) {
                targetDateTime = targetDateTime.plusDays(1)
            }

            val triggerMillis = targetDateTime.toInstant().toEpochMilli()
            setExactAlarm(triggerMillis, AUTO_CLOCK_IN_REQUEST_CODE, AutoClockReceiver.ACTION_AUTO_CLOCK_IN)
            Log.d("AutoClockManager", "Scheduled Specific AUTO_CLOCK_IN at $targetDateTime")
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to schedule specific Clock In", e)
        }
    }

    fun cancelAutoClockIn() {
        try {
            val intent = Intent(context, AutoClockReceiver::class.java).apply {
                action = AutoClockReceiver.ACTION_AUTO_CLOCK_IN
            }
            val pending = PendingIntent.getBroadcast(
                context, AUTO_CLOCK_IN_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pending?.let { alarmManager.cancel(it) }
            Log.d("AutoClockManager", "Cancelled AUTO_CLOCK_IN alarm")
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error cancelling Auto Clock In", e)
        }
    }

    fun scheduleAutoClockOut(offWorkTime: LocalTime, bufferSeconds: Int = 5) {
        try {
            cancelAutoClockOut()
            kotlinx.coroutines.runBlocking {
                val enabled = storage.autoClockOutEnabled.first()
                if (!enabled) return@runBlocking

                val clockOut = storage.clockOutTime.first()
                if (!clockOut.isNullOrBlank()) {
                    Log.d("AutoClockManager", "Already clocked out today, skip auto clock out scheduling")
                    return@runBlocking
                }

                val now = ZonedDateTime.now(ZoneId.systemDefault())
                val targetTimeWithBuffer = offWorkTime.plusSeconds(bufferSeconds.toLong())

                var targetDateTime = now
                    .withHour(targetTimeWithBuffer.hour)
                    .withMinute(targetTimeWithBuffer.minute)
                    .withSecond(targetTimeWithBuffer.second)
                    .withNano(0)

                if (targetDateTime.isBefore(now)) {
                    Log.d("AutoClockManager", "Target off work time $targetDateTime has already passed, triggering immediately")
                }

                val triggerMillis = targetDateTime.toInstant().toEpochMilli()
                setExactAlarm(triggerMillis, AUTO_CLOCK_OUT_REQUEST_CODE, AutoClockReceiver.ACTION_AUTO_CLOCK_OUT)
                Log.d("AutoClockManager", "Scheduled AUTO_CLOCK_OUT at $targetDateTime (buffer: ${bufferSeconds}s)")
            }
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to schedule Auto Clock Out", e)
        }
    }

    fun cancelAutoClockOut() {
        try {
            val intent = Intent(context, AutoClockReceiver::class.java).apply {
                action = AutoClockReceiver.ACTION_AUTO_CLOCK_OUT
            }
            val pending = PendingIntent.getBroadcast(
                context, AUTO_CLOCK_OUT_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            pending?.let { alarmManager.cancel(it) }
            Log.d("AutoClockManager", "Cancelled AUTO_CLOCK_OUT alarm")
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error cancelling Auto Clock Out", e)
        }
    }

    private fun setExactAlarm(triggerAtMillis: Long, requestCode: Int, action: String) {
        val intent = Intent(context, AutoClockReceiver::class.java).apply {
            this.action = action
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
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to set exact alarm for $action", e)
        }
    }

    suspend fun handleAutoClockInTrigger() {
        try {
            val enabled = storage.autoClockInEnabled.first()
            if (!enabled) return

            val todayDate = LocalDate.now()
            val todayDateStr = todayDate.toString()
            val lastClockDate = storage.lastClockDate.first()
            val localClockIn = storage.clockInTime.first()

            if (todayDateStr == lastClockDate && !localClockIn.isNullOrBlank()) {
                Log.d("AutoClockManager", "Today already clocked in ($localClockIn). Rescheduling for tomorrow.")
                scheduleAutoClockIn()
                return
            }

            // 1. Query B4.1 Attendance Table for today
            Log.d("AutoClockManager", "Querying B4.1 Attendance Summary for $todayDate...")
            val attendanceResult = repository.getAttendanceSummary(todayDate, todayDate)
            if (attendanceResult.isFailure) {
                val errMsg = attendanceResult.exceptionOrNull()?.message ?: "連線異常"
                Log.e("AutoClockManager", "Attendance pre-check failed: $errMsg")
                notificationHelper.sendNotification(
                    "WiXtar 自動打卡：考勤預檢失敗",
                    "無法取得今日考勤資料 ($errMsg)，請檢查網路或手動打卡"
                )
                return
            }

            val records = attendanceResult.getOrNull().orEmpty()
            val todayRecord = records.firstOrNull()

            if (todayRecord != null) {
                // If cloud already recorded a punch-in
                if (todayRecord.clockIn.isNotBlank()) {
                    Log.d("AutoClockManager", "Cloud already has clock-in: ${todayRecord.clockIn}")
                    storage.saveClockIn(todayRecord.clockIn)
                    scheduleAutoClockIn()
                    return
                }

                // Check shiftName: only proceed if 常日班
                if (todayRecord.shiftName.isNotBlank() && !todayRecord.shiftName.contains("常日班")) {
                    Log.d("AutoClockManager", "Today is not 常日班 (${todayRecord.shiftName}). Skipping punch.")
                    scheduleAutoClockIn()
                    return
                }

                // Parse leave intervals
                val leaveIntervals = WorkShiftPolicy.parseLeaveIntervals(todayRecord.leave)
                val workStartStr = storage.workStartTime.first()
                val workStartTime = try {
                    LocalTime.parse(workStartStr.trim(), DateTimeFormatter.ofPattern("HH:mm"))
                } catch (_: Exception) {
                    LocalTime.of(9, 0)
                }

                val effectiveTime = WorkShiftPolicy.determineEffectiveClockInTime(
                    todayRecord.shiftName,
                    workStartTime,
                    leaveIntervals
                )

                if (effectiveTime == null) {
                    Log.d("AutoClockManager", "Full-day leave today. Skipping punch.")
                    scheduleAutoClockIn()
                    return
                }

                val nowTime = LocalTime.now()

                // If morning leave pushed work start to a later time (e.g. 10:00, 11:00, 13:00, 14:00)
                if (effectiveTime > nowTime.plusMinutes(1)) {
                    Log.d("AutoClockManager", "Morning leave detected until $effectiveTime. Rescheduling alarm.")
                    scheduleSpecificClockIn(effectiveTime.plusSeconds(5))
                    notificationHelper.sendNotification(
                        "WiXtar 自動打卡已排程",
                        "偵測到您請假至 $effectiveTime，將於 $effectiveTime 自動打上班卡"
                    )
                    return
                }

                // If regular morning start, check flexible punch
                val flexEnabled = storage.flexibleClockInEnabled.first()
                if (flexEnabled && effectiveTime == workStartTime) {
                    val flexEndStr = storage.flexibleEndTime.first()
                    val flexEndTime = try {
                        LocalTime.parse(flexEndStr.trim(), DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (_: Exception) {
                        LocalTime.of(9, 30)
                    }

                    if (nowTime < flexEndTime.minusMinutes(1)) {
                        // Pick a random minute between now and flexEndTime
                        val startSec = nowTime.toSecondOfDay()
                        val endSec = flexEndTime.toSecondOfDay()
                        if (endSec > startSec + 60) {
                            val randomSec = Random.nextInt(startSec + 30, endSec)
                            val randomTime = LocalTime.ofSecondOfDay(randomSec.toLong())
                            Log.d("AutoClockManager", "Flexible punch randomized to $randomTime")
                            scheduleSpecificClockIn(randomTime)
                            notificationHelper.sendNotification(
                                "WiXtar 彈性打卡已排程",
                                "彈性上班區間內已為您排程於 $randomTime 自動打上班卡"
                            )
                            return
                        }
                    }
                }
            }

            // Execute the Clock In Punch!
            executeClockIn(todayRecord?.leave.orEmpty())
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error in handleAutoClockInTrigger", e)
        }
    }

    private suspend fun executeClockIn(leaveText: String = "") {
        val empId = storage.empId.first()
        val pass = storage.password.first()
        val cookie = storage.cookie.first()
        val enc = storage.enc.first()
        val gps = storage.gpsLocation.first()

        Log.d("AutoClockManager", "Executing Auto Clock In punch now...")
        val punchResult = repository.clockIn(empId, pass, cookie, enc, gps)
        if (punchResult.isSuccess) {
            val time = punchResult.getOrNull() ?: ""
            Log.d("AutoClockManager", "Auto Clock In succeeded: $time")
            storage.saveClockIn(time)
            notificationHelper.sendNotification("WiXtar 自動打卡成功", "時間 $time，今日已為您自動打上班卡！")

            // Calculate expected off-work time and schedule auto clock out & reminders
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val cleanTime = time.substringBefore(" ").substringBefore("(").trim()
            val parsedInTime = try { LocalTime.parse(cleanTime, formatter) } catch (_: Exception) { LocalTime.of(9, 0) }

            val leaveIntervals = WorkShiftPolicy.parseLeaveIntervals(leaveText)
            val offWorkTime = WorkShiftPolicy.calculateExpectedOffWorkTime(parsedInTime, leaveIntervals)

            val notifyBefore = storage.notifyBeforeOffWork.first()
            val notifyOn = storage.notifyOnOffWork.first()
            val advanceMinutes = storage.notifyAdvanceMinutes.first()
            val useLive = storage.useLiveUpdates.first()
            if (notifyBefore || notifyOn) {
                notificationHelper.scheduleReminders(
                    clockInTimeStr = cleanTime,
                    advanceMinutes = advanceMinutes,
                    useLiveUpdates = useLive,
                    mockOffWorkTimeStr = offWorkTime.format(formatter),
                    notifyBefore = notifyBefore,
                    notifyOn = notifyOn
                )
            }

            // Schedule Auto Clock Out
            val autoOutEnabled = storage.autoClockOutEnabled.first()
            if (autoOutEnabled) {
                scheduleAutoClockOut(offWorkTime, bufferSeconds = 5)
            }

            // Schedule next day's auto clock in
            scheduleAutoClockIn()
        } else {
            val errorMsg = punchResult.exceptionOrNull()?.message ?: "打卡失敗"
            Log.e("AutoClockManager", "Auto Clock In failed: $errorMsg")
            notificationHelper.sendNotification("WiXtar 自動打卡失敗", "$errorMsg，請開啟 App 手動打卡")
        }
    }

    suspend fun handleAutoClockOutTrigger() {
        try {
            val enabled = storage.autoClockOutEnabled.first()
            if (!enabled) return

            val clockIn = storage.clockInTime.first()
            if (clockIn.isNullOrBlank()) {
                Log.d("AutoClockManager", "No clock-in record today. Skip auto clock out.")
                return
            }

            val clockOut = storage.clockOutTime.first()
            if (!clockOut.isNullOrBlank()) {
                Log.d("AutoClockManager", "Already clocked out ($clockOut). Skip auto clock out.")
                return
            }

            val empId = storage.empId.first()
            val pass = storage.password.first()
            val cookie = storage.cookie.first()
            val enc = storage.enc.first()
            val gps = storage.gpsLocation.first()

            Log.d("AutoClockManager", "Executing Auto Clock Out punch now...")
            val punchResult = repository.clockIn(empId, pass, cookie, enc, gps)
            if (punchResult.isSuccess) {
                val time = punchResult.getOrNull() ?: ""
                Log.d("AutoClockManager", "Auto Clock Out succeeded: $time")
                storage.saveClockOut(time)
                notificationHelper.cancelReminders()
                notificationHelper.cancelLiveUpdateNotification()
                notificationHelper.sendNotification("WiXtar 自動打下班卡成功", "時間 $time，辛苦了，可以放心下班囉！")
            } else {
                val errorMsg = punchResult.exceptionOrNull()?.message ?: "打卡失敗"
                Log.e("AutoClockManager", "Auto Clock Out failed: $errorMsg")
                notificationHelper.sendNotification("WiXtar 自動打下班卡失敗", "$errorMsg，請開啟 App 手動打卡")
            }
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error in handleAutoClockOutTrigger", e)
        }
    }
}
