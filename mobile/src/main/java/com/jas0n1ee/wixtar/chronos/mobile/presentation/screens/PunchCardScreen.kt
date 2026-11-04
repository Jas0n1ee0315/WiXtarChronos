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

    val offWorkTimeStr = remember(clockInTime) {
        if (!clockInTime.isNullOrBlank()) {
            try {
                val rawTime = clockInTime?.substringBefore(" ") ?: ""
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val time = LocalTime.parse(rawTime, formatter)
                time.plusHours(9).format(formatter)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
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
