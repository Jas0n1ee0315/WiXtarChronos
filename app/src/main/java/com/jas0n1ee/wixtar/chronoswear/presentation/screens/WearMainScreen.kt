package com.jas0n1ee.wixtar.chronoswear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.jas0n1ee.wixtar.chronoswear.presentation.PunchButtonStatus
import com.jas0n1ee.wixtar.chronoswear.presentation.WearClockViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun WearMainScreen(
    viewModel: WearClockViewModel,
    modifier: Modifier = Modifier
) {
    val clockInTime by viewModel.clockInTime.collectAsState()
    val clockOutTime by viewModel.clockOutTime.collectAsState()
    val clockInSource by viewModel.clockInSource.collectAsState()
    val clockOutSource by viewModel.clockOutSource.collectAsState()
    val buttonStatus by viewModel.buttonStatus.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResume()
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppPause()
        }
    }

    val expectedOffWorkTimeStr = remember(clockInTime) {
        if (!clockInTime.isNullOrBlank()) {
            try {
                val rawTime = clockInTime?.substringBefore(" ") ?: ""
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val time = LocalTime.parse(rawTime, formatter)
                time.plusHours(9).format(formatter)
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    val normalButtonText = remember(clockInTime, clockOutTime) {
        when {
            clockInTime.isNullOrBlank() -> "打上班卡"
            clockOutTime.isNullOrBlank() -> "打下班卡"
            else -> "再打下班卡"
        }
    }

    val buttonText = when (buttonStatus) {
        PunchButtonStatus.NORMAL -> normalButtonText
        PunchButtonStatus.LOADING -> "打卡處理中..."
        PunchButtonStatus.SUCCESS -> "打卡成功"
        PunchButtonStatus.FAILURE -> "失敗，請重試"
        PunchButtonStatus.NO_NETWORK -> "沒有網路"
        PunchButtonStatus.HR_ERROR -> "HR系統異常\n重試中..."
    }

    // Material 3 Dynamic Color scheme support
    val buttonColors = when (buttonStatus) {
        PunchButtonStatus.NORMAL -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        PunchButtonStatus.LOADING -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
        PunchButtonStatus.SUCCESS -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White
        )
        PunchButtonStatus.FAILURE, PunchButtonStatus.NO_NETWORK, PunchButtonStatus.HR_ERROR -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    }

    val buttonEnabled = when (buttonStatus) {
        PunchButtonStatus.SUCCESS, PunchButtonStatus.LOADING -> false
        else -> true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        TimeText()

        // Center Content Container (Shifted up with bottom = 44.dp, top = 18.dp to avoid cramped layout above EdgeButton)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (clockInTime.isNullOrBlank()) {
                    // State A: 未打卡
                    Text(
                        text = "今日尚無打卡",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (clockOutTime.isNullOrBlank()) {
                    // State B: 已打上班卡
                    Text(
                        text = "上班時間",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$clockInTime ($clockInSource)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(1.dp)
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Text(
                        text = "預計可下班時間",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = expectedOffWorkTimeStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // State C: 已打下班卡
                    Text(
                        text = "上班時間",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$clockInTime ($clockInSource)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(1.dp)
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Text(
                        text = "下班時間",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$clockOutTime ($clockOutSource)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Material 3 Expressive EdgeButton (Hugs bottom screen edge seamlessly!)
        EdgeButton(
            onClick = {
                if (buttonStatus == PunchButtonStatus.HR_ERROR) {
                    viewModel.startHrHealthCheckLoop()
                } else {
                    viewModel.doPunchAction()
                }
            },
            enabled = buttonEnabled,
            colors = buttonColors,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
