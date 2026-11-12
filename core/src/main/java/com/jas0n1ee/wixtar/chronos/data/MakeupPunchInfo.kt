package com.jas0n1ee.wixtar.chronos.data

/**
 * Form metadata and daily punch context loaded from B3.7.線上補刷卡.
 */
data class MakeupPunchPageInfo(
    val enc: String,
    val cpnyId: String,
    val deptNo: String,
    val companyName: String,
    val deptName: String,
    val empId: String,
    val punchHistoryText: String,
    val classTime: String,
    val shiftClass: String
)
