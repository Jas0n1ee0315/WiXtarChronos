package com.jas0n1ee.wixtar.chronos.mobile.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object AutoPunchScheduler {
    fun scheduleAutoPunch(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val storage = AppStorage(context)
                val autoIn = storage.autoClockInEnabled.first()
                val autoOut = storage.autoClockOutEnabled.first()
                val workStartStr = storage.workStartTime.first()
                val flexibleIn = storage.flexibleClockInEnabled.first()
                val flexibleEndStr = storage.flexibleEndTime.first()

                val workManager = WorkManager.getInstance(context)

                // 1. Auto Clock In Scheduling
                if (!autoIn) {
                    workManager.cancelUniqueWork("AutoClockInWork")
                } else {
                    val clockIn = storage.clockInTime.first()
                    if (clockIn.isNullOrBlank()) {
                        val now = LocalDateTime.now()
                        val startTime = parseLocalTime(workStartStr, 8, 30)
                        var targetDateTime = now.withHour(startTime.hour).withMinute(startTime.minute).withSecond(0).withNano(0)

                        if (targetDateTime.isBefore(now)) {
                            val endTime = parseLocalTime(flexibleEndStr, 9, 30)
                            val endDateTime = now.withHour(endTime.hour).withMinute(endTime.minute).withSecond(0).withNano(0)

                            if (flexibleIn && now.isBefore(endDateTime)) {
                                targetDateTime = now.plusSeconds(5)
                            } else {
                                targetDateTime = targetDateTime.plusDays(1)
                            }
                        }

                        val delaySeconds = Duration.between(now, targetDateTime).seconds.coerceAtLeast(1)

                        val inRequest = OneTimeWorkRequestBuilder<AutoClockInWorker>()
                            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                            .build()

                        workManager.enqueueUniqueWork(
                            "AutoClockInWork",
                            ExistingWorkPolicy.REPLACE,
                            inRequest
                        )
                    }
                }

                // 2. Auto Clock Out Scheduling
                if (!autoOut) {
                    workManager.cancelUniqueWork("AutoClockOutWork")
                } else {
                    val clockIn = storage.clockInTime.first()
                    val clockOut = storage.clockOutTime.first()

                    if (!clockIn.isNullOrBlank() && clockOut.isNullOrBlank()) {
                        val rawIn = clockIn.substringBefore(" ")
                        val formatter = DateTimeFormatter.ofPattern("HH:mm")
                        val inTime = LocalTime.parse(rawIn, formatter)
                        val outTime = inTime.plusHours(9)

                        val now = LocalDateTime.now()
                        var targetOutDateTime = now.withHour(outTime.hour).withMinute(outTime.minute).withSecond(0).withNano(0).plusSeconds(5)

                        if (targetOutDateTime.isBefore(now)) {
                            targetOutDateTime = now.plusSeconds(5)
                        }

                        val delaySeconds = Duration.between(now, targetOutDateTime).seconds.coerceAtLeast(1)

                        val outRequest = OneTimeWorkRequestBuilder<AutoClockOutWorker>()
                            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                            .build()

                        workManager.enqueueUniqueWork(
                            "AutoClockOutWork",
                            ExistingWorkPolicy.REPLACE,
                            outRequest
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseLocalTime(str: String, defaultH: Int, defaultM: Int): LocalTime {
        return try {
            val parts = str.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            LocalTime.of(defaultH, defaultM)
        }
    }
}
