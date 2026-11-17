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

    @Test
    fun testCleanHtmlTags() {
        val rawInput = "<value>補登單新增完成且進入流程等待簽核，謝謝<br></value><value></value>"
        val cleaned = WorkShiftPolicy.cleanHtmlTags(rawInput)
        assertEquals("補登單新增完成且進入流程等待簽核，謝謝", cleaned)

        val rawWithEntities = "&lt;value&gt;測試 &amp; 成功&lt;br&gt;&lt;/value&gt;"
        val cleanedEntities = WorkShiftPolicy.cleanHtmlTags(rawWithEntities)
        assertEquals("測試 & 成功", cleanedEntities)
    }

    @Test
    fun testIsClockInAction() {
        // Morning with no clock-in -> Clock-in action (true)
        org.junit.Assert.assertTrue(WorkShiftPolicy.isClockInAction(null, LocalTime.of(8, 30)))
        org.junit.Assert.assertTrue(WorkShiftPolicy.isClockInAction("", LocalTime.of(9, 12)))
        org.junit.Assert.assertTrue(WorkShiftPolicy.isClockInAction(null, LocalTime.of(17, 29)))

        // At or after 17:30 with no clock-in -> Clock-out action (false)
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction(null, LocalTime.of(17, 30)))
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction(null, LocalTime.of(18, 36)))
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction("", LocalTime.of(18, 48)))

        // Already clocked in -> Always Clock-out action (false)
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction("09:00", LocalTime.of(9, 30)))
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction("09:00", LocalTime.of(18, 0)))

        // With afternoon leave (15:20 - 18:20) -> Clock-out action starts at 15:20
        val afternoonLeave = WorkShiftPolicy.parseLeaveIntervals("1520-1820 特休")
        org.junit.Assert.assertTrue(WorkShiftPolicy.isClockInAction(null, LocalTime.of(15, 10), afternoonLeave))
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction(null, LocalTime.of(15, 20), afternoonLeave))
        org.junit.Assert.assertFalse(WorkShiftPolicy.isClockInAction(null, LocalTime.of(16, 0), afternoonLeave))
    }

    @Test
    fun testNormalizeCloudClockTimes() {
        // Normal morning punch
        val (in1, out1) = WorkShiftPolicy.normalizeCloudClockTimes("09:05", null)
        assertEquals("09:05", in1)
        assertNull(out1)

        // Normal full day
        val (in2, out2) = WorkShiftPolicy.normalizeCloudClockTimes("08:45", "17:45")
        assertEquals("08:45", in2)
        assertEquals("17:45", out2)

        // Only evening punch (forgot morning punch)
        val (in3, out3) = WorkShiftPolicy.normalizeCloudClockTimes("18:36", null)
        assertNull(in3)
        assertEquals("18:36", out3)

        // Only evening punch with (雲端) suffix
        val (in4, out4) = WorkShiftPolicy.normalizeCloudClockTimes("18:48 (雲端)", null)
        assertNull(in4)
        assertEquals("18:48 (雲端)", out4)

        // Both recorded at 18:48 (the bug from yesterday)
        val (in5, out5) = WorkShiftPolicy.normalizeCloudClockTimes("18:48", "18:48")
        assertNull(in5)
        assertEquals("18:48", out5)

        // With afternoon leave (15:20 - 18:20)
        val afternoonLeave = WorkShiftPolicy.parseLeaveIntervals("1520-1820 特休")
        val (in6, out6) = WorkShiftPolicy.normalizeCloudClockTimes("15:30", null, afternoonLeave)
        assertNull(in6)
        assertEquals("15:30", out6)
    }
}
