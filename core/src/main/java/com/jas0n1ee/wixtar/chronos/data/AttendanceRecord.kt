package com.jas0n1ee.wixtar.chronos.data

/**
 * Represents one daily row from B4.1 attendance summary table.
 */
data class AttendanceRecord(
    val department: String,
    val empId: String,
    val empName: String,
    val date: String,
    val shiftName: String,
    val shiftTime: String,
    val clockIn: String,
    val clockOut: String,
    val workHours: String,
    val abnormal: String,
    val leave: String,
    val overtime: String,
    val remark: String,
    val plannedOvertime: String,
    val makeupForm: String,
    val link1: String,
    val link2: String,
    val pendingApproval: String,
    val punchDetail: String
) {
    val hasAbnormal: Boolean
        get() = abnormal.isNotBlank()

    /**
     * 出勤明細視窗顯示的欄位。
     * 不列出部門名稱、員工編號、員工姓名（都是自己，資訊多餘），
     * 以及日期（已顯示在視窗標題「{日期} 明細」）。
     */
    fun toDetailPairs(): List<Pair<String, String>> = listOf(
        "班別" to shiftName,
        "班別時間" to shiftTime,
        "進卡" to clockIn,
        "出卡" to clockOut,
        "工時" to workHours,
        "異常" to abnormal,
        "請假" to leave,
        "加班" to overtime,
        "備註" to remark,
        "預定加班" to plannedOvertime,
        "補登單" to makeupForm,
        "連結一" to link1,
        "連結二" to link2,
        "未簽核完成單據" to pendingApproval,
        "刷卡明細" to punchDetail
    )
}
