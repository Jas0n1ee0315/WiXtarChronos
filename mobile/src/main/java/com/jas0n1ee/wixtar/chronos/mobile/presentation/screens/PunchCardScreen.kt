package com.jas0n1ee.wixtar.chronos.mobile.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jas0n1ee.wixtar.chronos.data.VibrationUtil
import com.jas0n1ee.wixtar.chronos.mobile.R
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MobileClockViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunchCardScreen(
    viewModel: MobileClockViewModel,
    modifier: Modifier = Modifier
) {
    val clockInTime by viewModel.clockInTime.collectAsState()
    val clockOutTime by viewModel.clockOutTime.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isClockingIn by viewModel.isClockingIn.collectAsState()
    val isHrSystemNormal by viewModel.isHrSystemNormal.collectAsState()
    val hrRetryCountdown by viewModel.hrRetryCountdown.collectAsState()

    val autoClockInEnabled by viewModel.autoClockInEnabled.collectAsState()
    val workStartTime by viewModel.workStartTime.collectAsState()
    val flexibleClockInEnabled by viewModel.flexibleClockInEnabled.collectAsState()
    val flexibleStartTime by viewModel.flexibleStartTime.collectAsState()
    val flexibleEndTime by viewModel.flexibleEndTime.collectAsState()
    val autoClockOutEnabled by viewModel.autoClockOutEnabled.collectAsState()
    val offWorkTimeStr by viewModel.expectedOffWorkTime.collectAsState()

    var showTimePickerDialog by remember { mutableStateOf(false) }
    var activeTimePickerTarget by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndClearExpiredClockData()
                viewModel.checkHrHealth()
                viewModel.fetchCloudData()
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.stopHrHealthRetryLoop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopHrHealthRetryLoop()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndClearExpiredClockData()
    }

    val actionButtonText = remember(clockInTime, clockOutTime) {
        when {
            clockInTime.isNullOrBlank() -> "打上班卡"
            clockOutTime.isNullOrBlank() -> "打下班卡"
            else -> "再次打下班卡"
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top HR System Health Status Card (Green for Normal, Red for Down)
            val cardColors = when (isHrSystemNormal) {
                true -> CardDefaults.cardColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
                false -> CardDefaults.cardColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                )
                null -> CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val statusText = when (isHrSystemNormal) {
                true -> "HR系統正常運行中"
                false -> {
                    if (hrRetryCountdown in 1..3) {
                        "HR系統掛了，$hrRetryCountdown 秒後將再檢查"
                    } else {
                        "HR系統掛了，檢查中..."
                    }
                }
                null -> "檢查 HR 系統狀態中..."
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.checkHrHealth() },
                shape = RoundedCornerShape(24.dp),
                colors = cardColors
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cardColors.contentColor
                    )
                }
            }

            // Punch Card Status Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日打卡時間",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { viewModel.fetchCloudData() },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Clock In Status Block
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "上班時間",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = clockInTime ?: "--:--",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Clock Out Status Block
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "下班時間",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = clockOutTime ?: "--:--",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (!clockInTime.isNullOrBlank() && clockOutTime.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Expected Off-Work Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "預計可下班時間",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = offWorkTimeStr,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Auto Clock Settings Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "自動打卡",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 1. 自動打上班卡 (Switch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動打上班卡",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "打卡前自動預檢考勤表與請假單",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoClockInEnabled,
                            onCheckedChange = { checked ->
                                viewModel.saveAutoClockSettings(autoIn = checked)
                            }
                        )
                    }

                    // 2. 上班卡時間 (Only shown when autoClockInEnabled is true)
                    if (autoClockInEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "上班卡時間",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "排程觸發打卡的時間點 (預設 09:00)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                onClick = {
                                    activeTimePickerTarget = "WORK_START"
                                    showTimePickerDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = workStartTime,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // 3. 彈性打上班卡 (Switch, only shown when autoClockInEnabled is true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "彈性打上班卡",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "在彈性區間內隨機時間觸發打卡",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = flexibleClockInEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.saveAutoClockSettings(flexIn = checked)
                                }
                            )
                        }

                        // 4. 彈性上班區間 (Only shown when flexibleClockInEnabled is true)
                        if (flexibleClockInEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "彈性上班區間",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "起始時間固定為上班卡時間，可點擊調整結束時間",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    onClick = {
                                        activeTimePickerTarget = "FLEXIBLE_END"
                                        showTimePickerDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "$workStartTime ~ $flexibleEndTime",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 5. 自動下班卡 (Switch)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自動打下班卡",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "當日已打上班卡，於可下班時間後 5 秒自動打卡",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoClockOutEnabled,
                            onCheckedChange = { checked ->
                                viewModel.saveAutoClockSettings(autoOut = checked)
                            }
                        )
                    }
                }
            }
        }

        if (showTimePickerDialog && activeTimePickerTarget != null) {
            val currentTimeStr = when (activeTimePickerTarget) {
                "WORK_START" -> workStartTime
                "FLEXIBLE_END" -> flexibleEndTime
                else -> "09:00"
            }
            val parts = currentTimeStr.split(":")
            val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
            val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = true
            )

            AlertDialog(
                onDismissRequest = {
                    showTimePickerDialog = false
                    activeTimePickerTarget = null
                },
                title = {
                    Text(
                        text = if (activeTimePickerTarget == "WORK_START") "設定上班卡時間" else "設定彈性結束時間",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val formatted = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                            if (activeTimePickerTarget == "WORK_START") {
                                viewModel.saveAutoClockSettings(workStart = formatted, flexStart = formatted)
                            } else if (activeTimePickerTarget == "FLEXIBLE_END") {
                                viewModel.saveAutoClockSettings(flexEnd = formatted)
                            }
                            showTimePickerDialog = false
                            activeTimePickerTarget = null
                        }
                    ) {
                        Text("確定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showTimePickerDialog = false
                            activeTimePickerTarget = null
                        }
                    ) {
                        Text("取消")
                    }
                },
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimePicker(state = timePickerState)
                    }
                }
            )
        }

        val context = LocalContext.current

        // Fixed Floating Punch Card Action Button (Pinned right above the Tab Bar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    VibrationUtil.vibrateClick(context)
                    viewModel.doClockAction()
                },
                enabled = !isClockingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isClockingIn) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("打卡處理中...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cloud_clock),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = actionButtonText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
