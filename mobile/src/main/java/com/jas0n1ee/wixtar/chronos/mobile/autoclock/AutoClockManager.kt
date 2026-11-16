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
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_SECONDS = 45L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val storage = AppStorage(context)
    private val repository = ClockRepository(storage)
    private val notificationHelper = MobileNotificationHelper(context)

    /**
     * Schedules auto clock-in alarm.
     * When flexible punch is enabled, randomizes the target punch time ONCE within
     * the flexible window [workStartTime, flexEndTime] when scheduling.
     */
    fun scheduleAutoClockIn() {
        try {
            cancelAutoClockIn()
            kotlinx.coroutines.runBlocking {
                val enabled = storage.autoClockInEnabled.first()
                if (!enabled) return@runBlocking

                val workStartStr = storage.workStartTime.first()
                val flexEnabled = storage.flexibleClockInEnabled.first()
                val flexEndStr = storage.flexibleEndTime.first()

                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val workStartTime = try {
                    LocalTime.parse(workStartStr.trim(), formatter)
                } catch (_: Exception) {
                    LocalTime.of(9, 0)
                }
                val flexEndTime = try {
                    LocalTime.parse(flexEndStr.trim(), formatter)
                } catch (_: Exception) {
                    LocalTime.of(9, 30)
                }

                val now = ZonedDateTime.now(ZoneId.systemDefault())
                val todayDateStr = LocalDate.now().toString()
                val lastClockDate = storage.lastClockDate.first()
                val clockIn = storage.clockInTime.first()

                val alreadyClockedToday = (todayDateStr == lastClockDate && !clockIn.isNullOrBlank())

                // Determine whether to schedule for today or tomorrow
                val scheduleForToday = !alreadyClockedToday && now.toLocalTime() < flexEndTime.minusMinutes(1)

                val targetDateTime: ZonedDateTime = if (scheduleForToday) {
                    val targetTime = if (flexEnabled) {
                        val startSec = maxOf(now.toLocalTime().toSecondOfDay() + 15, workStartTime.toSecondOfDay())
                        val endSec = flexEndTime.toSecondOfDay()
                        if (endSec > startSec + 10) {
                            val randomSec = Random.nextInt(startSec, endSec)
                            LocalTime.ofSecondOfDay(randomSec.toLong())
                        } else {
                            now.toLocalTime().plusSeconds(15)
                        }
                    } else {
                        workStartTime
                    }
                    now.withHour(targetTime.hour)
                        .withMinute(targetTime.minute)
                        .withSecond(targetTime.second)
                        .withNano(0)
                } else {
                    // Schedule for tomorrow
                    val tomorrow = now.plusDays(1)
                    val targetTime = if (flexEnabled) {
                        val startSec = workStartTime.toSecondOfDay()
                        val endSec = flexEndTime.toSecondOfDay()
                        if (endSec > startSec + 10) {
                            val randomSec = Random.nextInt(startSec, endSec)
                            LocalTime.ofSecondOfDay(randomSec.toLong())
                        } else {
                            workStartTime
                        }
                    } else {
                        workStartTime
                    }
                    tomorrow.withHour(targetTime.hour)
                        .withMinute(targetTime.minute)
                        .withSecond(targetTime.second)
                        .withNano(0)
                }

                val triggerMillis = targetDateTime.toInstant().toEpochMilli()
                setExactAlarm(triggerMillis, AUTO_CLOCK_IN_REQUEST_CODE, AutoClockReceiver.ACTION_AUTO_CLOCK_IN)
                Log.d("AutoClockManager", "Scheduled AUTO_CLOCK_IN at $targetDateTime (flexEnabled: $flexEnabled)")
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

    fun scheduleRetryClockIn(delaySeconds: Long = RETRY_DELAY_SECONDS, retryCount: Int) {
        try {
            val triggerMillis = System.currentTimeMillis() + delaySeconds * 1000L
            val intent = Intent(context, AutoClockReceiver::class.java).apply {
                action = AutoClockReceiver.ACTION_AUTO_CLOCK_IN
                putExtra(AutoClockReceiver.EXTRA_RETRY_COUNT, retryCount)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, AUTO_CLOCK_IN_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setExactPendingIntent(triggerMillis, pendingIntent)
            Log.d("AutoClockManager", "Scheduled RETRY #$retryCount in ${delaySeconds}s")
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to schedule retry Clock In", e)
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

    fun scheduleRetryClockOut(delaySeconds: Long = RETRY_DELAY_SECONDS, retryCount: Int) {
        try {
            val triggerMillis = System.currentTimeMillis() + delaySeconds * 1000L
            val intent = Intent(context, AutoClockReceiver::class.java).apply {
                action = AutoClockReceiver.ACTION_AUTO_CLOCK_OUT
                putExtra(AutoClockReceiver.EXTRA_RETRY_COUNT, retryCount)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, AUTO_CLOCK_OUT_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setExactPendingIntent(triggerMillis, pendingIntent)
            Log.d("AutoClockManager", "Scheduled RETRY CLOCK OUT #$retryCount in ${delaySeconds}s")
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Failed to schedule retry Clock Out", e)
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
        setExactPendingIntent(triggerAtMillis, pendingIntent)
    }

    private fun setExactPendingIntent(triggerAtMillis: Long, pendingIntent: PendingIntent) {
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
            Log.e("AutoClockManager", "Failed to set exact alarm", e)
        }
    }

    /**
     * Executes when the exact auto clock-in alarm fires.
     * Performs B4.1 attendance pre-check, checks for leaves/holidays, and punches in.
     * If network fails (e.g. elevator), automatically retries up to 3 times before giving up.
     */
    suspend fun handleAutoClockInTrigger(retryCount: Int = 0) {
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
            Log.d("AutoClockManager", "Querying B4.1 Attendance Summary for $todayDate (attempt #$retryCount)...")
            val attendanceResult = repository.getAttendanceSummary(todayDate, todayDate)
            if (attendanceResult.isFailure) {
                val errMsg = attendanceResult.exceptionOrNull()?.message ?: "連線異常"
                Log.e("AutoClockManager", "Attendance pre-check failed: $errMsg (retryCount: $retryCount)")

                // Elevator / temporary offline scenario: auto retry up to MAX_RETRIES times
                if (retryCount < MAX_RETRIES) {
                    Log.d("AutoClockManager", "Network offline, scheduling retry #${retryCount + 1} in ${RETRY_DELAY_SECONDS}s...")
                    scheduleRetryClockIn(RETRY_DELAY_SECONDS, retryCount + 1)
                    return
                }

                // If retries exhausted, send notification
                notificationHelper.sendNotification(
                    "WiXtar 自動打卡：考勤預檢失敗",
                    "無法取得今日考勤資料 ($errMsg)，已自動重試 ${MAX_RETRIES} 次。請檢查網路或開啟 App 手動打卡"
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
            }

            // Target punch time reached and pre-checks passed -> EXECUTE CLOCK IN!
            executeClockIn(todayRecord?.leave.orEmpty(), retryCount = 0)
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error in handleAutoClockInTrigger", e)
        }
    }

    private suspend fun executeClockIn(leaveText: String = "", retryCount: Int = 0) {
        val empId = storage.empId.first()
        val pass = storage.password.first()
        val cookie = storage.cookie.first()
        val enc = storage.enc.first()
        val gps = storage.gpsLocation.first()

        Log.d("AutoClockManager", "Executing Auto Clock In punch now (attempt #$retryCount)...")
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
            Log.e("AutoClockManager", "Auto Clock In failed: $errorMsg (retryCount: $retryCount)")

            if (retryCount < MAX_RETRIES && (errorMsg.contains("連線") || errorMsg.contains("網路") || errorMsg.contains("timeout") || errorMsg.contains("Unable to resolve host"))) {
                Log.d("AutoClockManager", "Punch network failed, scheduling retry #${retryCount + 1} in ${RETRY_DELAY_SECONDS}s...")
                scheduleRetryClockIn(RETRY_DELAY_SECONDS, retryCount + 1)
                return
            }

            notificationHelper.sendNotification("WiXtar 自動打卡失敗", "$errorMsg，請開啟 App 手動打卡")
        }
    }

    suspend fun handleAutoClockOutTrigger(retryCount: Int = 0) {
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

            Log.d("AutoClockManager", "Executing Auto Clock Out punch now (attempt #$retryCount)...")
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

                if (retryCount < MAX_RETRIES && (errorMsg.contains("連線") || errorMsg.contains("網路") || errorMsg.contains("timeout") || errorMsg.contains("Unable to resolve host"))) {
                    Log.d("AutoClockManager", "Auto clock out network failed, scheduling retry #${retryCount + 1} in ${RETRY_DELAY_SECONDS}s...")
                    scheduleRetryClockOut(RETRY_DELAY_SECONDS, retryCount + 1)
                    return
                }

                notificationHelper.sendNotification("WiXtar 自動打下班卡失敗", "$errorMsg，請開啟 App 手動打卡")
            }
        } catch (e: Exception) {
            Log.e("AutoClockManager", "Error in handleAutoClockOutTrigger", e)
        }
    }
}
