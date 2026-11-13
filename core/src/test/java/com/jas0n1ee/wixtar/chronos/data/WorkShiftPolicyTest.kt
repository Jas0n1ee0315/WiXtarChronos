package com.jas0n1ee.wixtar.chronos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class WorkShiftPolicyTest {

    @Test
    fun testParseLeaveIntervals() {
        val rawHtml1 = "<SPAN style=\"white-space:nowrap;\">0900-1100 特休</SPAN>"
        val intervals1 = WorkShiftPolicy.parseLeaveIntervals(rawHtml1)
        assertEquals(1, intervals1.size)
        assertEquals(LocalTime.of(9, 0), intervals1[0].start)
        assertEquals(LocalTime.of(11, 0), intervals1[0].end)
        assertEquals("特休", intervals1[0].type)

        val rawText2 = "0900-1800 特休"
        val intervals2 = WorkShiftPolicy.parseLeaveIntervals(rawText2)
        assertEquals(1, intervals2.size)
        assertEquals(LocalTime.of(9, 0), intervals2[0].start)
        assertEquals(LocalTime.of(18, 0), intervals2[0].end)

        val rawColon = "09:00 ~ 12:00 事假"
        val intervals3 = WorkShiftPolicy.parseLeaveIntervals(rawColon)
        assertEquals(1, intervals3.size)
        assertEquals(LocalTime.of(9, 0), intervals3[0].start)
        assertEquals(LocalTime.of(12, 0), intervals3[0].end)
    }

    @Test
    fun testDetermineEffectiveClockInTime_NonWorkday() {
        val result = WorkShiftPolicy.determineEffectiveClockInTime(
            shiftName = "國定假日",
            workStartTime = LocalTime.of(9, 0),
            leaveIntervals = emptyList()
        )
        assertNull(result)
    }

    @Test
    fun testDetermineEffectiveClockInTime_FullDayLeave() {
        val leaves = WorkShiftPolicy.parseLeaveIntervals("0900-1800 特休")
        val result = WorkShiftPolicy.determineEffectiveClockInTime(
            shiftName = "常日班",
            workStartTime = LocalTime.of(9, 0),
            leaveIntervals = leaves
        )
        assertNull(result)
    }

    @Test
    fun testDetermineEffectiveClockInTime_MorningLeaves() {
        // 09:00 - 10:00 leave -> clock in at 10:00
        val l1 = WorkShiftPolicy.parseLeaveIntervals("0900-1000 特休")
        assertEquals(
            LocalTime.of(10, 0),
            WorkShiftPolicy.determineEffectiveClockInTime("常日班", LocalTime.of(9, 0), l1)
        )

        // 09:00 - 11:00 leave -> clock in at 11:00
        val l2 = WorkShiftPolicy.parseLeaveIntervals("0900-1100 特休")
        assertEquals(
            LocalTime.of(11, 0),
            WorkShiftPolicy.determineEffectiveClockInTime("常日班", LocalTime.of(9, 0), l2)
        )

        // 09:00 - 12:00 leave (lunch is 12:00-13:00) -> clock in at 13:00
        val l3 = WorkShiftPolicy.parseLeaveIntervals("0900-1200 事假")
        assertEquals(
            LocalTime.of(13, 0),
            WorkShiftPolicy.determineEffectiveClockInTime("常日班", LocalTime.of(9, 0), l3)
        )

        // 09:00 - 14:00 leave -> clock in at 14:00
        val l4 = WorkShiftPolicy.parseLeaveIntervals("0900-1400 事假")
        assertEquals(
            LocalTime.of(14, 0),
            WorkShiftPolicy.determineEffectiveClockInTime("常日班", LocalTime.of(9, 0), l4)
        )

        // Afternoon leave only (13:00 - 18:00) -> morning clock in remains 09:00
        val l5 = WorkShiftPolicy.parseLeaveIntervals("1300-1800 特休")
        assertEquals(
            LocalTime.of(9, 0),
            WorkShiftPolicy.determineEffectiveClockInTime("常日班", LocalTime.of(9, 0), l5)
        )
    }

    @Test
    fun testCalculateExpectedOffWorkTime() {
        // Earlier than 08:30 -> 17:30
        assertEquals(LocalTime.of(17, 30), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(8, 20)))
        assertEquals(LocalTime.of(17, 30), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(8, 30)))

        // Flexible interval 08:30 ~ 09:30 (+9 hours)
        assertEquals(LocalTime.of(17, 43), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(8, 43)))
        assertEquals(LocalTime.of(18, 12), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(9, 12)))
        assertEquals(LocalTime.of(18, 29), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(9, 29)))
        assertEquals(LocalTime.of(18, 30), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(9, 30)))

        // Later than 09:30 -> capped at 18:00
        assertEquals(LocalTime.of(18, 0), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(9, 41)))
        assertEquals(LocalTime.of(18, 0), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(10, 0)))
        assertEquals(LocalTime.of(18, 0), WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(13, 0)))

        // Afternoon leave ending at or after 18:00 (e.g. 15:20 - 18:20) -> off work at leave start (15:20)
        val afternoonLeave = WorkShiftPolicy.parseLeaveIntervals("1520-1820 特休")
        assertEquals(
            LocalTime.of(15, 20),
            WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(9, 15), afternoonLeave)
        )

        // Morning leave (09:00 - 11:00) -> ends at 18:00
        val morningLeave = WorkShiftPolicy.parseLeaveIntervals("0900-1100 特休")
        assertEquals(
            LocalTime.of(18, 0),
            WorkShiftPolicy.calculateExpectedOffWorkTime(LocalTime.of(11, 0), morningLeave)
        )
    }
}
