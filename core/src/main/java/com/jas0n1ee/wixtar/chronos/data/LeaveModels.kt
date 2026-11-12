package com.jas0n1ee.wixtar.chronos.data

import java.time.LocalDate

data class LeaveTypeOption(
    val code: String,
    val label: String,
    val requiresSpecDate: Boolean = false,
    val isBereavementDays: Boolean = false,
    val isBereavementHours: Boolean = false
) {
    val isBereavement: Boolean get() = isBereavementDays || isBereavementHours
}

data class BereavementReasonOption(
    val label: String,
    val daysCode: String,
    val hoursCode: String
)

val BEREAVEMENT_REASON_OPTIONS = listOf(
    BereavementReasonOption(
        label = "父母、養父母、繼父母、配偶喪亡",
        daysCode = "05.01",
        hoursCode = "38.10"
    ),
    BereavementReasonOption(
        label = "祖父母、子女、配偶之父母、配偶之養父母或繼父母喪亡",
        daysCode = "05.02",
        hoursCode = "38.11"
    ),
    BereavementReasonOption(
        label = "曾祖父母、兄弟姊妹、配偶之祖父母喪亡",
        daysCode = "05.03",
        hoursCode = "38.12"
    )
)

val DEFAULT_LEAVE_TYPES = listOf(
    LeaveTypeOption(code = "09", label = "特休"),
    LeaveTypeOption(code = "01", label = "事假"),
    LeaveTypeOption(code = "03", label = "半薪病假"),
    LeaveTypeOption(code = "04", label = "無薪病假"),
    LeaveTypeOption(code = "43", label = "生日假"),
    LeaveTypeOption(code = "07", label = "公假"),
    LeaveTypeOption(code = "12", label = "家庭照顧假"),
    LeaveTypeOption(code = "32", label = "外訓假"),
    LeaveTypeOption(code = "40", label = "公傷病假"),
    LeaveTypeOption(code = "02", label = "工出"),
    LeaveTypeOption(code = "15", label = "加班補休"),
    LeaveTypeOption(code = "31.09", label = "陪產檢及陪產假-給予七天", requiresSpecDate = true),
    LeaveTypeOption(code = "05", label = "喪假 (天)", requiresSpecDate = true, isBereavementDays = true),
    LeaveTypeOption(code = "38", label = "喪假 (時)", requiresSpecDate = true, isBereavementHours = true),
    LeaveTypeOption(code = "34", label = "婚假", requiresSpecDate = true),
    LeaveTypeOption(code = "77", label = "謀職假"),
    LeaveTypeOption(code = "81", label = "歲時祭儀假")
)

data class LeavePageInfo(
    val empId: String,
    val empName: String,
    val empEngName: String,
    val deptNo: String,
    val cpnyId: String,
    val enc: String,
    val vacationSummaries: List<String>
)

data class SavedAgent(
    val empId: String,
    val name: String,
    val deptName: String = ""
)

data class LeaveAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class LeaveConfirmData(
    val empId: String,
    val empName: String,
    val empEngName: String,
    val cpnyId: String,
    val deptNo: String,
    val agentEmpId: String,
    val agentDisplay: String,
    val leaveCode: String,
    val leaveLabel: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTimeHHmm: String,
    val endTimeHHmm: String,
    val specDate: LocalDate?,
    val overdueReason: String,
    val note: String,
    val amt: String,
    val amt0: String,
    val amt1: String,
    val unit: String,
    val add1Enc: String,
    val add1Inputs: Map<String, String>
)
