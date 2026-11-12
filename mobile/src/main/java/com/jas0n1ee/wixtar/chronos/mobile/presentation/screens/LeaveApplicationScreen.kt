package com.jas0n1ee.wixtar.chronos.mobile.presentation.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jas0n1ee.wixtar.chronos.data.BEREAVEMENT_REASON_OPTIONS
import com.jas0n1ee.wixtar.chronos.data.BereavementReasonOption
import com.jas0n1ee.wixtar.chronos.data.DEFAULT_LEAVE_TYPES
import com.jas0n1ee.wixtar.chronos.data.LeaveAttachment
import com.jas0n1ee.wixtar.chronos.data.LeaveConfirmData
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MobileClockViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveApplicationScreen(
    viewModel: MobileClockViewModel,
    modifier: Modifier = Modifier,
    onNavigateToAgentSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val leavePageInfo by viewModel.leavePageInfo.collectAsState()
    val isLoadingLeaveInfo by viewModel.isLoadingLeaveInfo.collectAsState()
    val isPreparingLeaveConfirm by viewModel.isPreparingLeaveConfirm.collectAsState()
    val isSubmittingLeave by viewModel.isSubmittingLeave.collectAsState()
    val pendingLeaveConfirm by viewModel.pendingLeaveConfirm.collectAsState()
    val leaveResultDialog by viewModel.leaveResultDialog.collectAsState()

    val savedAgents by viewModel.savedAgents.collectAsState()
    val savedAgentEmpId by viewModel.agentEmpId.collectAsState()

    var selectedLeaveType by remember { mutableStateOf(DEFAULT_LEAVE_TYPES.first()) } // 預設 特休
    var leaveDropdownExpanded by remember { mutableStateOf(false) }

    var selectedBereavementReason by remember { mutableStateOf<BereavementReasonOption?>(null) }
    var bereavementDropdownExpanded by remember { mutableStateOf(false) }

    var specDate by remember { mutableStateOf<LocalDate?>(null) }

    var selectedAgent by remember(savedAgents, savedAgentEmpId) {
        mutableStateOf(savedAgents.find { it.empId == savedAgentEmpId } ?: savedAgents.firstOrNull())
    }
    var agentDropdownExpanded by remember { mutableStateOf(false) }

    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(18, 0)) }

    var overdueReason by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }

    val dateDisplayFmt = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.TAIWAN) }
    val timeDisplayFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN) }
    val timePayloadFmt = remember { DateTimeFormatter.ofPattern("HHmm", Locale.TAIWAN) }

    val today = LocalDate.now()
    val isOverdue = startDate.isBefore(today)

    LaunchedEffect(Unit) {
        viewModel.loadLeavePageInfo()
    }

    val currentConfirm = pendingLeaveConfirm
    if (currentConfirm != null) {
        LeaveConfirmationReviewScreen(
            confirmData = currentConfirm,
            isSubmitting = isSubmittingLeave,
            modifier = modifier,
            onBack = { viewModel.cancelLeaveConfirmation() },
            onConfirmSubmit = { attachments ->
                viewModel.confirmLeaveApplication(attachments)
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 假別餘額資訊
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "假別餘額資訊",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoadingLeaveInfo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    val summaries = leavePageInfo?.vacationSummaries.orEmpty()
                    if (summaries.isEmpty() && !isLoadingLeaveInfo) {
                        Text(
                            text = "尚未取得假別餘額（登入後將自動載入）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summaries.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 請假資料填寫
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "請假資料填寫",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // 假別下拉選單
                    ExposedDropdownMenuBox(
                        expanded = leaveDropdownExpanded,
                        onExpandedChange = { leaveDropdownExpanded = !leaveDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLeaveType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("假別") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = leaveDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = leaveDropdownExpanded,
                            onDismissRequest = { leaveDropdownExpanded = false }
                        ) {
                            DEFAULT_LEAVE_TYPES.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedLeaveType = option
                                        if (!option.isBereavement) {
                                            selectedBereavementReason = null
                                        }
                                        if (!option.requiresSpecDate) {
                                            specDate = null
                                        }
                                        leaveDropdownExpanded = false
                                        validationError = null
                                    }
                                )
                            }
                        }
                    }

                    // 喪假「說明」必填下拉選單
                    if (selectedLeaveType.isBereavement) {
                        ExposedDropdownMenuBox(
                            expanded = bereavementDropdownExpanded,
                            onExpandedChange = { bereavementDropdownExpanded = !bereavementDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedBereavementReason?.label ?: "請選擇喪假說明（必選）",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("說明（必選）") },
                                isError = selectedBereavementReason == null && validationError != null,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = bereavementDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = bereavementDropdownExpanded,
                                onDismissRequest = { bereavementDropdownExpanded = false }
                            ) {
                                BEREAVEMENT_REASON_OPTIONS.forEach { reason ->
                                    DropdownMenuItem(
                                        text = { Text(reason.label) },
                                        onClick = {
                                            selectedBereavementReason = reason
                                            bereavementDropdownExpanded = false
                                            validationError = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 特殊日期（置於職務代理人上方，僅陪產檢及陪產假、喪假、婚假時顯示）
                    if (selectedLeaveType.requiresSpecDate) {
                        val initSpecDate = specDate ?: startDate
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            specDate = LocalDate.of(y, m + 1, d)
                                            validationError = null
                                        },
                                        initSpecDate.year,
                                        initSpecDate.monthValue - 1,
                                        initSpecDate.dayOfMonth
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "特殊日期（必填）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = specDate?.format(dateDisplayFmt) ?: "點擊選擇特殊日期",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (specDate == null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 職務代理人
                    if (savedAgents.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAgentSettings() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "職務代理人",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "點擊設定代理人",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = agentDropdownExpanded,
                            onExpandedChange = { agentDropdownExpanded = !agentDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedAgent?.let { "${it.empId} ${it.name}" }.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("職務代理人") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = agentDropdownExpanded,
                                onDismissRequest = { agentDropdownExpanded = false }
                            ) {
                                savedAgents.forEach { agent ->
                                    DropdownMenuItem(
                                        text = { Text("${agent.empId} ${agent.name}") },
                                        onClick = {
                                            selectedAgent = agent
                                            agentDropdownExpanded = false
                                            validationError = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 起始日期 & 時間
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val picked = LocalDate.of(y, m + 1, d)
                                            startDate = picked
                                            if (endDate.isBefore(picked)) {
                                                endDate = picked
                                            }
                                            validationError = null
                                        },
                                        startDate.year,
                                        startDate.monthValue - 1,
                                        startDate.dayOfMonth
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "起始日期",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = startDate.format(dateDisplayFmt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            startTime = LocalTime.of(h, m)
                                            validationError = null
                                        },
                                        startTime.hour,
                                        startTime.minute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "起始時間",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = startTime.format(timeDisplayFmt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // 結束日期 & 時間
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            endDate = LocalDate.of(y, m + 1, d)
                                            validationError = null
                                        },
                                        endDate.year,
                                        endDate.monthValue - 1,
                                        endDate.dayOfMonth
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "結束日期",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = endDate.format(dateDisplayFmt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            endTime = LocalTime.of(h, m)
                                            validationError = null
                                        },
                                        endTime.hour,
                                        endTime.minute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "結束時間",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = endTime.format(timeDisplayFmt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // 逾期請假原因（起始日期小於今天時必填）
                    if (isOverdue) {
                        OutlinedTextField(
                            value = overdueReason,
                            onValueChange = {
                                overdueReason = it
                                validationError = null
                            },
                            label = { Text("逾期請假原因（必填）") },
                            placeholder = { Text("起始日期早於今日，請說明補請原因") },
                            isError = overdueReason.isBlank() && validationError != null,
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // 說明
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            note = it
                            validationError = null
                        },
                        label = { Text("請假說明（必填）") },
                        placeholder = { Text("請輸入請假原因或備註") },
                        isError = note.isBlank() && validationError != null,
                        minLines = 1,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val bereavement = selectedBereavementReason
                            when {
                                selectedLeaveType.isBereavement && bereavement == null -> {
                                    validationError = "請選擇喪假說明"
                                }
                                selectedLeaveType.requiresSpecDate && specDate == null -> {
                                    validationError = "請選擇特殊日期"
                                }
                                selectedAgent == null -> {
                                    validationError = "請先設定職務代理人"
                                }
                                endDate.isBefore(startDate) -> {
                                    validationError = "結束日期不可早於起始日期"
                                }
                                isOverdue && overdueReason.isBlank() -> {
                                    validationError = "起始日期早於今日，請填寫逾期請假原因"
                                }
                                note.isBlank() -> {
                                    validationError = "請填寫請假說明"
                                }
                                else -> {
                                    validationError = null
                                    val resolvedCode = when {
                                        selectedLeaveType.isBereavementDays -> bereavement!!.daysCode
                                        selectedLeaveType.isBereavementHours -> bereavement!!.hoursCode
                                        else -> selectedLeaveType.code
                                    }
                                    val resolvedLabel = if (selectedLeaveType.isBereavement && bereavement != null) {
                                        "${selectedLeaveType.label} - ${bereavement.label}"
                                    } else {
                                        selectedLeaveType.label
                                    }
                                    val agent = selectedAgent!!
                                    viewModel.prepareLeaveConfirmation(
                                        agentEmpId = agent.empId,
                                        agentDisplay = "${agent.empId} ${agent.name}",
                                        leaveCode = resolvedCode,
                                        leaveLabel = resolvedLabel,
                                        startDate = startDate,
                                        endDate = endDate,
                                        startTimeHHmm = startTime.format(timePayloadFmt),
                                        endTimeHHmm = endTime.format(timePayloadFmt),
                                        specDate = if (selectedLeaveType.requiresSpecDate) specDate else null,
                                        overdueReason = if (isOverdue) overdueReason.trim() else "",
                                        note = note.trim()
                                    )
                                }
                            }
                        },
                        enabled = !isPreparingLeaveConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPreparingLeaveConfirm) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("檢核與計算時數中...")
                        } else {
                            Text("下一步：檢視與上傳附件", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    leaveResultDialog?.let { (isSuccess, msg) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearLeaveResultDialog() },
            icon = {
                Icon(
                    imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (isSuccess) "請假申請結果" else "請假送出提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLeaveResultDialog() }) {
                    Text("確定")
                }
            }
        )
    }
}

@Composable
private fun LeaveConfirmationReviewScreen(
    confirmData: LeaveConfirmData,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onConfirmSubmit: (List<LeaveAttachment>) -> Unit
) {
    val context = LocalContext.current
    var attachments by remember(confirmData) { mutableStateOf<List<LeaveAttachment>>(emptyList()) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = !isSubmitting) {
        onBack()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val remainingSlots = 5 - attachments.size
        if (remainingSlots <= 0) {
            attachmentError = "最多僅可上傳 5 個附件"
            return@rememberLauncherForActivityResult
        }
        val picked = uris.take(remainingSlots).mapNotNull { uri ->
            readAttachmentFromUri(context, uri)
        }
        if (uris.size > remainingSlots) {
            attachmentError = "最多僅可上傳 5 個附件，已選取前 $remainingSlots 個"
        } else {
            attachmentError = null
        }
        attachments = attachments + picked
    }

    val dateDisplayFmt = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.TAIWAN) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "確認請假明細",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "明細資料如下，如無問題，請上傳附件（選填）並按下方確認送出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                ConfirmDetailRow("假別", confirmData.leaveLabel)
                ConfirmDetailRow("職務代理人", confirmData.agentDisplay)
                ConfirmDetailRow(
                    "起始時間",
                    "${confirmData.startDate.format(dateDisplayFmt)} ${formatHHmm(confirmData.startTimeHHmm)}"
                )
                ConfirmDetailRow(
                    "結束時間",
                    "${confirmData.endDate.format(dateDisplayFmt)} ${formatHHmm(confirmData.endTimeHHmm)}"
                )
                if (confirmData.amt0.isNotBlank()) {
                    ConfirmDetailRow(
                        label = "本次請假時間合計",
                        value = confirmData.amt0,
                        highlight = true
                    )
                }
                confirmData.specDate?.let { spec ->
                    ConfirmDetailRow("特殊日期", spec.format(dateDisplayFmt))
                }
                if (confirmData.overdueReason.isNotBlank()) {
                    ConfirmDetailRow("逾期請假原因", confirmData.overdueReason)
                }
                if (confirmData.note.isNotBlank()) {
                    ConfirmDetailRow("說明", confirmData.note)
                }
            }
        }

        // 附件上傳區塊（最多 5 個）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "附件上傳（${attachments.size}/5）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "如訃聞、診斷證明等相關證明文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isSubmitting && attachments.size < 5,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("選擇檔案", maxLines = 1, softWrap = false)
                    }
                }

                if (attachmentError != null) {
                    Text(
                        text = attachmentError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                attachments.forEachIndexed { idx, file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = file.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    attachments = attachments.toMutableList().also { it.removeAt(idx) }
                                    attachmentError = null
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "移除附件",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("返回修改")
            }

            Button(
                onClick = { onConfirmSubmit(attachments) },
                enabled = !isSubmitting,
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("送出中...")
                } else {
                    Text("確認送出請假單", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDetailRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun formatHHmm(hhmm: String): String {
    return if (hhmm.length == 4) "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}" else hhmm
}

private fun readAttachmentFromUri(context: Context, uri: Uri): LeaveAttachment? {
    return try {
        val resolver = context.contentResolver
        var fileName = "attachment"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    fileName = cursor.getString(idx) ?: fileName
                }
            }
        }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        LeaveAttachment(fileName = fileName, mimeType = mimeType, bytes = bytes)
    } catch (_: Exception) {
        null
    }
}
