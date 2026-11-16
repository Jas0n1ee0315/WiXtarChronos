package com.jas0n1ee.wixtar.chronos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** PjmLeaveHoursCalculator：請假單拆分成每日 PJM 工時的規則 */
class PjmLeaveHoursCalculatorTest {

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val mon = LocalDate.of(2026, 11, 16)

    @Test
    fun fullDay_0900to1800_is8Hours() {
        assertEquals(8.0, PjmLeaveHoursCalculator.hoursForDay(t(9), t(18)), 0.0)
    }

    @Test
    fun morning_0900to1200_is3Hours() {
        assertEquals(3.0, PjmLeaveHoursCalculator.hoursForDay(t(9), t(12)), 0.0)
    }

    @Test
    fun morningIntoLunch_0900to1300_excludesLunch() {
        assertEquals(3.0, PjmLeaveHoursCalculator.hoursForDay(t(9), t(13)), 0.0)
    }

    @Test
    fun afternoon_1300to1800_is5Hours() {
        assertEquals(5.0, PjmLeaveHoursCalculator.hoursForDay(t(13), t(18)), 0.0)
    }

    @Test
    fun overlongDay_isCappedAt8() {
        assertEquals(8.0, PjmLeaveHoursCalculator.hoursForDay(t(8), t(20)), 0.0)
    }

    @Test
    fun partialHalfHour_isFlooredToHalf() {
        // 14:00–15:40 = 1h40m → 1.5h
        assertEquals(1.5, PjmLeaveHoursCalculator.hoursForDay(t(14), t(15, 40)), 0.0)
    }

    @Test
    fun endBeforeStart_isZero() {
        assertEquals(0.0, PjmLeaveHoursCalculator.hoursForDay(t(15), t(14)), 0.0)
    }

    @Test
    fun parseTime_acceptsHHmmAndColon() {
        assertEquals(t(9), PjmLeaveHoursCalculator.parseTime("0900"))
        assertEquals(t(13, 30), PjmLeaveHoursCalculator.parseTime("13:30"))
        assertNull(PjmLeaveHoursCalculator.parseTime("930"))
        assertNull(PjmLeaveHoursCalculator.parseTime("2500"))
    }

    @Test
    fun split_singleFullDay() {
        val days = PjmLeaveHoursCalculator.split(mon, mon, "0900", "1800")
        assertEquals(listOf(PjmLeaveDay(mon, 8.0)), days)
    }

    @Test
    fun split_multiDay_partialFirstAndLast() {
        // 週一 14:00 起 ～ 週三 12:00 止：週一 4h、週二 8h、週三 3h
        val days = PjmLeaveHoursCalculator.split(mon, mon.plusDays(2), "1400", "1200")
        assertEquals(
            listOf(PjmLeaveDay(mon, 4.0), PjmLeaveDay(mon.plusDays(1), 8.0), PjmLeaveDay(mon.plusDays(2), 3.0)),
            days
        )
    }

    @Test
    fun split_dropsZeroHourDays() {
        // 結束時間 09:00：最後一天 0 小時，不列入
        val days = PjmLeaveHoursCalculator.split(mon, mon.plusDays(1), "0900", "0900")
        assertEquals(listOf(PjmLeaveDay(mon, 8.0)), days)
    }

    @Test
    fun split_endBeforeStart_isEmpty() {
        assertTrue(PjmLeaveHoursCalculator.split(mon, mon.minusDays(1), "0900", "1800").isEmpty())
    }

    @Test
    fun businessTrip_isNotSynced() {
        assertFalse(PjmLeaveHoursCalculator.shouldSync("02"))  // 工出
        assertTrue(PjmLeaveHoursCalculator.shouldSync("09"))   // 特休
    }
}
