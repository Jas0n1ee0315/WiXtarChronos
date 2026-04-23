package com.wixtar.watchtimeclock.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import com.wixtar.watchtimeclock.presentation.theme.星益欣打卡Theme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    )
                }
            }

            星益欣打卡Theme {
                val viewModel: ClockViewModel = viewModel()
                val isLoading by viewModel.isLoading.collectAsState()

                if (isLoading) {
                    LoadingScreen()
                } else {
                    AppContent(viewModel)
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AppContent(viewModel: ClockViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    AppScaffold {
        HorizontalPager(state = pagerState) { page ->
            if (page == 0) {
                MainScreen(viewModel)
            } else {
                SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ClockViewModel) {
    val clockInTime by viewModel.clockInTime.collectAsState()
    val clockOutTime by viewModel.clockOutTime.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionStatus by viewModel.actionStatus.collectAsState()

    val offWorkTimeStr = remember(clockInTime, clockOutTime) {
        if (clockInTime == null || clockInTime == "") {
            "尚無進卡時間"
        } else if (clockOutTime != null && clockOutTime != "") {
            "已過下班時間"
        } else {
            try {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val time = LocalTime.parse(clockInTime, formatter)
                time.plusHours(8).format(formatter)
            } catch (e: Exception) {
                "尚無進卡時間"
            }
        }
    }

    val buttonText = remember(clockInTime, clockOutTime, actionStatus) {
        when (actionStatus) {
            ClockViewModel.ActionStatus.SUCCESS_IN -> "打上班卡成功"
            ClockViewModel.ActionStatus.SUCCESS_OUT -> "打下班卡成功"
            ClockViewModel.ActionStatus.SUCCESS_OVERTIME -> "打加班卡成功"
            ClockViewModel.ActionStatus.FAILURE_IN -> "打上班卡失敗"
            ClockViewModel.ActionStatus.FAILURE_OUT -> "打下班卡失敗"
            ClockViewModel.ActionStatus.FAILURE_OVERTIME -> "打加班卡失敗"
            null -> {
                if (clockInTime == null || clockInTime == "") "打上班卡"
                else if (clockOutTime == null || clockOutTime == "") "打下班卡"
                else "打加班卡"
            }
        }
    }

    val buttonColors = if (actionStatus != null) {
        val isSuccess = actionStatus?.name?.startsWith("SUCCESS") == true
        ButtonDefaults.buttonColors(
            containerColor = if (isSuccess) Color.Green else Color.Red,
            contentColor = Color.White
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    val listState = rememberTransformingLazyColumnState()
    
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { viewModel.doClockAction() },
                colors = buttonColors
            ) {
                Text(
                    text = if (error != null && actionStatus == null) "重試" else buttonText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Text(
                    text = "上班卡時間: ${clockInTime ?: "無"}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            item {
                Text(
                    text = "下班卡時間: ${clockOutTime ?: "無"}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                Text(
                    text = "可下班時間:",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            item {
                Text(
                    text = offWorkTimeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (error != null && actionStatus == null) {
                item {
                    Text(
                        text = error!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ClockViewModel) {
    val empId by viewModel.empId.collectAsState()
    val password by viewModel.password.collectAsState()
    val cookie by viewModel.cookie.collectAsState()
    val enc by viewModel.enc.collectAsState()
    val gpsLocation by viewModel.gpsLocation.collectAsState()
    val notifyBefore by viewModel.notifyBeforeOffWork.collectAsState()
    val notifyOn by viewModel.notifyOnOffWork.collectAsState()

    var tempEmpId by remember(empId) { mutableStateOf(empId) }
    var tempPassword by remember(password) { mutableStateOf(password) }
    var tempCookie by remember(cookie) { mutableStateOf(cookie) }
    var tempEnc by remember(enc) { mutableStateOf(enc) }
    var tempGps by remember(gpsLocation) { mutableStateOf(gpsLocation) }
    var tempNotifyBefore by remember(notifyBefore) { mutableStateOf(notifyBefore) }
    var tempNotifyOn by remember(notifyOn) { mutableStateOf(notifyOn) }

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "設定頁",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item { SettingItem("員工工號", tempEmpId) { tempEmpId = it } }
            item { SettingItem("密碼", tempPassword) { tempPassword = it } }
            item { SettingItem("Cookie", tempCookie) { tempCookie = it } }
            item { SettingItem("enc", tempEnc) { tempEnc = it } }
            item { SettingItem("GPS位置", tempGps) { tempGps = it } }
            
            item {
                ToggleSettingItem("下班前十分通知", tempNotifyBefore) { tempNotifyBefore = it }
            }
            item {
                ToggleSettingItem("可下班時通知", tempNotifyOn) { tempNotifyOn = it }
            }

            item {
                Button(
                    onClick = { viewModel.saveSettings(tempEmpId, tempPassword, tempCookie, tempEnc, tempGps, tempNotifyBefore, tempNotifyOn) },
                    label = { Text("儲存設定") },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun ToggleSettingItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    )
}

@Composable
fun SettingItem(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().background(Color.DarkGray).padding(4.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White)
        )
    }
}
