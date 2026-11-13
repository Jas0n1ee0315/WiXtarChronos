package com.jas0n1ee.wixtar.chronos.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object WorkShiftPolicy {
    data class LeaveInterval(
        val start: LocalTime,
        val end: LocalTime,
        val type: String
    )

    private val LEAVE_PATTERN = Pattern.compile(
        """(\d{2}):?(\d{2})\s*[-~至到]\s*(\d{2}):?(\d{2})\s*([^\d\s<]*)"""
    )

    /**
     * Parses leave string from B4.1 (e.g. "0900-1100 特休", "<SPAN>0900-1400 事假</SPAN>")
     */
    fun parseLeaveIntervals(leaveText: String): List<LeaveInterval> {
        if (leaveText.isBlank()) return emptyList()
        val cleaned = leaveText
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val list = mutableListOf<LeaveInterval>()
        val matcher = LEAVE_PATTERN.matcher(cleaned)
        while (matcher.find()) {
            try {
                val startH = matcher.group(1)?.toIntOrNull() ?: continue
                val startM = matcher.group(2)?.toIntOrNull() ?: continue
                val endH = matcher.group(3)?.toIntOrNull() ?: continue
                val endM = matcher.group(4)?.toIntOrNull() ?: continue
                val type = matcher.group(5)?.trim() ?: ""

                val start = LocalTime.of(startH, startM)
                val end = LocalTime.of(endH, endM)
                list.add(LeaveInterval(start, end, type))
            } catch (_: Exception) {}
        }
        return list
    }

    /**
     * Determines whether the user should clock in today, and at what time.
     * Returns:
     * - null if full day leave or non-workday (skip punch)
     * - LocalTime of the effective punch time (e.g. 09:00, 10:00, 11:00, 13:00, 14:00)
     */
    fun determineEffectiveClockInTime(
        shiftName: String,
        workStartTime: LocalTime,
        leaveIntervals: List<LeaveInterval>
    ): LocalTime? {
        val trimmedShift = shiftName.trim()
        if (trimmedShift.isNotBlank() && !trimmedShift.contains("常日班")) {
            // Not a regular workday (e.g. 休息日, 例假日, 國定假日)
            return null
        }

        // Check for full-day leave (e.g. 09:00 to 18:00 or duration >= 8 hours)
        for (leave in leaveIntervals) {
            val isFullDay = (leave.start <= LocalTime.of(9, 0) && leave.end >= LocalTime.of(18, 0)) ||
                    (leave.start.toSecondOfDay() <= LocalTime.of(9, 0).toSecondOfDay() &&
                            (leave.end.toSecondOfDay() - leave.start.toSecondOfDay()) >= 8 * 3600)
            if (isFullDay) {
                return null
            }
        }

        // Check if there is a leave starting at or before workStartTime (e.g. 09:00)
        var latestMorningLeaveEnd: LocalTime? = null
        for (leave in leaveIntervals) {
            if (leave.start <= workStartTime && leave.end > workStartTime) {
                if (latestMorningLeaveEnd == null || leave.end > latestMorningLeaveEnd) {
                    latestMorningLeaveEnd = leave.end
                }
            }
        }

        if (latestMorningLeaveEnd != null) {
            val lunchStart = LocalTime.of(12, 0)
            val lunchEnd = LocalTime.of(13, 0)
            val shiftEnd = LocalTime.of(18, 0)

            return when {
                latestMorningLeaveEnd >= shiftEnd -> null // Leaves rest of day
                latestMorningLeaveEnd > lunchStart && latestMorningLeaveEnd <= lunchEnd -> lunchEnd // Ends during lunch break -> 13:00
                latestMorningLeaveEnd == lunchStart -> lunchEnd // Ends at 12:00 -> 13:00
                else -> latestMorningLeaveEnd // e.g. 10:00, 11:00, 14:00
            }
        }

        return workStartTime
    }

    /**
     * Calculates expected off-work time based on actual clockInTime and leave intervals.
     * Rules:
     * 1. If afternoon leave covers end of day (e.g. 15:20 ~ 18:20) -> leave start time (15:20)
     * 2. If morning leave was present (or late arrival after 09:30) -> 18:00
     * 3. If clock in between 08:30 and 09:30 -> clockIn + 9 hours
     * 4. If clock in before 08:30 -> 17:30
     */
    fun calculateExpectedOffWorkTime(
        clockInTime: LocalTime,
        leaveIntervals: List<LeaveInterval> = emptyList()
    ): LocalTime {
        val shiftEnd = LocalTime.of(18, 0)

        // Check Rule 1: Afternoon early departure leave (ends at or after 18:00)
        for (leave in leaveIntervals) {
            if (leave.start >= LocalTime.of(12, 0) && leave.end >= shiftEnd) {
                return leave.start // e.g. 15:20
            }
        }

        // Check Rule 2: Did user have morning leave?
        val hadMorningLeave = leaveIntervals.any { it.start <= LocalTime.of(9, 0) && it.end > LocalTime.of(9, 0) }
        if (hadMorningLeave) {
            return shiftEnd // Morning covered by leave, required shift finishes at standard 18:00
        }

        // Check Rule 3: Flexible work hours between 08:30 and 09:30
        val flexEarliest = LocalTime.of(8, 30)
        val flexLatest = LocalTime.of(9, 30)

        return when {
            clockInTime < flexEarliest -> LocalTime.of(17, 30)
            clockInTime <= flexLatest -> clockInTime.plusHours(9) // e.g. 08:43 -> 17:43, 09:12 -> 18:12, 09:30 -> 18:30
            else -> shiftEnd // After 09:30 (e.g. 09:41, 10:00) -> capped at standard 18:00
        }
    }
}
