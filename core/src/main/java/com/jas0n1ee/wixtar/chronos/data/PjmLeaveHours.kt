package com.jas0n1ee.wixtar.chronos.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** 同步到 PJM 的單日請假工時 */
data class PjmLeaveDay(val date: LocalDate, val hours: Double)

/**
 * 把一張請假單（起訖日期 + 起訖時間）拆成「每天幾小時」，供 PJM 研發工時使用。
 *
 * 規則：
 * - 中間的整天：視為 09:00–18:00。
 * - 第一天從請假起始時間開始、最後一天到請假結束時間為止。
 * - 扣掉與午休 12:00–13:00 重疊的部分，每天上限 8 小時。
 * - 以 0.5 小時為單位「無條件捨去」（PJM 介面以 0.5 為單位），例如 09:00–13:00 → 3 小時。
 * - 這裡不判斷週末與國定假日，由呼叫端（PjmRepository）過濾。
 */
object PjmLeaveHoursCalculator {
    val WORK_START: LocalTime = LocalTime.of(9, 0)
    val WORK_END: LocalTime = LocalTime.of(18, 0)
    val LUNCH_START: LocalTime = LocalTime.of(12, 0)
    val LUNCH_END: LocalTime = LocalTime.of(13, 0)
    const val DAILY_MAX_HOURS = 8.0

    /** 不同步到 PJM 的假別代碼：02 工出（出差／外出公務，不算請假） */
    val EXCLUDED_LEAVE_CODES: Set<String> = setOf("02")

    fun shouldSync(leaveCode: String): Boolean = leaveCode.trim() !in EXCLUDED_LEAVE_CODES

    /** 解析 "HHmm"（例如 "0900"）或 "HH:mm"；格式錯誤回傳 null */
    fun parseTime(text: String): LocalTime? {
        val digits = text.filter { it.isDigit() }
        if (digits.length != 4) return null
        val h = digits.substring(0, 2).toIntOrNull() ?: return null
        val m = digits.substring(2, 4).toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    /** 單日請假時數：start～end 扣午休、上限 8 小時、以 0.5 小時捨去 */
    fun hoursForDay(start: LocalTime, end: LocalTime): Double {
        if (!end.isAfter(start)) return 0.0
        val totalMinutes = Duration.between(start, end).toMinutes()
        val lunchFrom = maxOf(start, LUNCH_START)
        val lunchTo = minOf(end, LUNCH_END)
        val lunchMinutes = if (lunchTo.isAfter(lunchFrom)) Duration.between(lunchFrom, lunchTo).toMinutes() else 0L
        val hours = (totalMinutes - lunchMinutes) / 60.0
        val floored = kotlin.math.floor(hours * 2) / 2 // 以 0.5 小時為單位捨去
        return floored.coerceIn(0.0, DAILY_MAX_HOURS)
    }

    /**
     * 拆分整張請假單；時數為 0 的日期不列入。
     * 時間格式錯誤時，第一天／最後一天以上班起訖時間代替。
     */
    fun split(
        startDate: LocalDate,
        endDate: LocalDate,
        startHHmm: String,
        endHHmm: String
    ): List<PjmLeaveDay> {
        if (endDate.isBefore(startDate)) return emptyList()
        val firstStart = parseTime(startHHmm) ?: WORK_START
        val lastEnd = parseTime(endHHmm) ?: WORK_END
        val result = mutableListOf<PjmLeaveDay>()
        var day = startDate
        while (!day.isAfter(endDate)) {
            val from = if (day == startDate) firstStart else WORK_START
            val to = if (day == endDate) lastEnd else WORK_END
            val hours = hoursForDay(from, to)
            if (hours > 0) result += PjmLeaveDay(day, hours)
            day = day.plusDays(1)
        }
        return result
    }
}
