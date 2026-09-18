package com.jas0n1ee.wixtar.chronos.mobile.presentation.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WatchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MobileClockViewModel

enum class SettingsSubCategory(val title: String) {
    MAIN("系統設定"),
    AUTH("打卡驗證資訊"),
    NOTIFICATIONS("推播通知"),
    AUTO_CLOCK("自動打卡功能"),
    WEAR_OS("WearOS 整合")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    viewModel: MobileClockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSubCategory by remember { mutableStateOf(SettingsSubCategory.MAIN) }

    BackHandler(enabled = currentSubCategory != SettingsSubCategory.MAIN) {
        currentSubCategory = SettingsSubCategory.MAIN
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasExactAlarmPermission by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    var isIgnoringBattery by remember { mutableStateOf(viewModel.isIgnoringBatteryOptimizations()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasExactAlarmPermission = viewModel.canScheduleExactAlarms()
                isIgnoringBattery = viewModel.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val empId by viewModel.empId.collectAsState()
    val password by viewModel.password.collectAsState()
    val cookie by viewModel.cookie.collectAsState()
    val enc by viewModel.enc.collectAsState()
    val gpsLocation by viewModel.gpsLocation.collectAsState()
    val notifyBefore by viewModel.notifyBeforeOffWork.collectAsState()
    val notifyOn by viewModel.notifyOnOffWork.collectAsState()
    val notifyAdvanceMinutes by viewModel.notifyAdvanceMinutes.collectAsState()
    val useLiveUpdates by viewModel.useLiveUpdates.collectAsState()
    val enableWatchSync by viewModel.enableWatchSync.collectAsState()
    val connectedNodesCount by viewModel.connectedNodesCount.collectAsState()

    val forgetClockOutEnabled by viewModel.forgetClockOutNotifyEnabled.collectAsState()
    val forgetClockOutTime by viewModel.forgetClockOutNotifyTime.collectAsState()

    val fishOilEnabled by viewModel.fishOilNotifyEnabled.collectAsState()
    val fishOilTime by viewModel.fishOilNotifyTime.collectAsState()
    val fishOilDays by viewModel.fishOilNotifyDays.collectAsState()

    val autoClockIn by viewModel.autoClockInEnabled.collectAsState()
    val workStart by viewModel.workStartTime.collectAsState()
    val flexibleClockIn by viewModel.flexibleClockInEnabled.collectAsState()
    val flexibleEnd by viewModel.flexibleEndTime.collectAsState()
    val autoClockOut by viewModel.autoClockOutEnabled.collectAsState()

    val lunchDndEnabled by viewModel.lunchDndEnabled.collectAsState()
    val lunchDndStartTime by viewModel.lunchDndStartTime.collectAsState()
    val lunchDndEndTime by viewModel.lunchDndEndTime.collectAsState()

    val mockOffWorkTime by viewModel.mockOffWorkTime.collectAsState()

    var tempEmpId by remember(empId) { mutableStateOf(empId) }
    var tempPassword by remember(password) { mutableStateOf(password) }
    var tempCookie by remember(cookie) { mutableStateOf(cookie) }
    var tempEnc by remember(enc) { mutableStateOf(enc) }
    var tempGps by remember(gpsLocation) { mutableStateOf(gpsLocation) }
    var tempNotifyBefore by remember(notifyBefore) { mutableStateOf(notifyBefore) }
    var tempNotifyOn by remember(notifyOn) { mutableStateOf(notifyOn) }
    var tempAdvanceMinutes by remember(notifyAdvanceMinutes) { mutableIntStateOf(notifyAdvanceMinutes) }
    var tempUseLiveUpdates by remember(useLiveUpdates) { mutableStateOf(useLiveUpdates) }

    var tempForgetClockOutEnabled by remember(forgetClockOutEnabled) { mutableStateOf(forgetClockOutEnabled) }
    var tempForgetClockOutTime by remember(forgetClockOutTime) { mutableStateOf(forgetClockOutTime) }

    var tempFishOilEnabled by remember(fishOilEnabled) { mutableStateOf(fishOilEnabled) }
    var tempFishOilTime by remember(fishOilTime) { mutableStateOf(fishOilTime) }
    var tempFishOilDays by remember(fishOilDays) { mutableStateOf(fishOilDays) }

    var tempAutoClockIn by remember(autoClockIn) { mutableStateOf(autoClockIn) }
    var tempWorkStart by remember(workStart) { mutableStateOf(workStart) }
    var tempFlexibleClockIn by remember(flexibleClockIn) { mutableStateOf(flexibleClockIn) }
    var tempFlexibleEnd by remember(flexibleEnd) { mutableStateOf(flexibleEnd) }
    var tempAutoClockOut by remember(autoClockOut) { mutableStateOf(autoClockOut) }

    var tempLunchDndEnabled by remember(lunchDndEnabled) { mutableStateOf(lunchDndEnabled) }
    var tempLunchDndStartTime by remember(lunchDndStartTime) { mutableStateOf(lunchDndStartTime) }
    var tempLunchDndEndTime by remember(lunchDndEndTime) { mutableStateOf(lunchDndEndTime) }

    var tempMockOffWorkTime by remember(mockOffWorkTime) { mutableStateOf(mockOffWorkTime) }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var advanceDropdownExpanded by remember { mutableStateOf(false) }
    
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var activeTimePickerTarget by remember { mutableStateOf("FISH_OIL") }

    var showPowerWarningDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var showDndPermissionDialog by remember { mutableStateOf(false) }
    var pendingToggleTarget by remember { mutableStateOf<String?>(null) }

    fun autoSave(
        empIdVal: String = tempEmpId,
        passVal: String = tempPassword,
        cookieVal: String = tempCookie,
        encVal: String = tempEnc,
        gpsVal: String = tempGps,
        notifyBeforeVal: Boolean = tempNotifyBefore,
        notifyOnVal: Boolean = tempNotifyOn,
        advanceMinutesVal: Int = tempAdvanceMinutes,
        useLiveUpdatesVal: Boolean = tempUseLiveUpdates,
        forgetClockOutEnabledVal: Boolean = tempForgetClockOutEnabled,
        forgetClockOutTimeVal: String = tempForgetClockOutTime,
        fishOilEnabledVal: Boolean = tempFishOilEnabled,
        fishOilTimeVal: String = tempFishOilTime,
        fishOilDaysVal: String = tempFishOilDays,
        autoClockInVal: Boolean = tempAutoClockIn,
        workStartVal: String = tempWorkStart,
        flexibleClockInVal: Boolean = tempFlexibleClockIn,
        flexibleEndVal: String = tempFlexibleEnd,
        autoClockOutVal: Boolean = tempAutoClockOut,
        lunchDndEnabledVal: Boolean = tempLunchDndEnabled,
        lunchDndStartTimeVal: String = tempLunchDndStartTime,
        lunchDndEndTimeVal: String = tempLunchDndEndTime,
        mockOffWorkTimeVal: String = tempMockOffWorkTime
    ) {
        viewModel.saveSettings(
            empIdVal, passVal, cookieVal, encVal, gpsVal,
            notifyBeforeVal, notifyOnVal, advanceMinutesVal, useLiveUpdatesVal,
            forgetClockOutEnabledVal, forgetClockOutTimeVal,
            fishOilEnabledVal, fishOilTimeVal, fishOilDaysVal,
            autoClockInVal, workStartVal, flexibleClockInVal, workStartVal, flexibleEndVal, autoClockOutVal,
            lunchDndEnabledVal, lunchDndStartTimeVal, lunchDndEndTimeVal,
            mockOffWorkTimeVal
        )
    }

    fun applyAndSaveParsedConfig(content: String) {
        if (content.isNotBlank()) {
            val result = viewModel.importConfigFromText(content)
            val newCookie = if (result.cookie.isNotBlank()) result.cookie else tempCookie
            val newEnc = if (result.enc.isNotBlank()) result.enc else tempEnc
            val newEmpId = if (result.empId.isNotBlank()) result.empId else tempEmpId
            val newPassword = if (result.password.isNotBlank()) result.password else tempPassword
            val newGps = if (result.gps.isNotBlank()) result.gps else tempGps

            tempCookie = newCookie
            tempEnc = newEnc
            tempEmpId = newEmpId
            tempPassword = newPassword
            tempGps = newGps

            autoSave(
                empIdVal = newEmpId,
                passVal = newPassword,
                cookieVal = newCookie,
                encVal = newEnc,
                gpsVal = newGps
            )
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                applyAndSaveParsedConfig(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // TimePicker Dialog
    if (showTimePickerDialog) {
        val currentTimeStr = when (activeTimePickerTarget) {
            "FISH_OIL" -> tempFishOilTime
            "FORGET_CLOCK_OUT" -> tempForgetClockOutTime
            "WORK_START" -> tempWorkStart
            "FLEXIBLE_END" -> tempFlexibleEnd
            "LUNCH_DND_START" -> tempLunchDndStartTime
            "LUNCH_DND_END" -> tempLunchDndEndTime
            "MOCK_OFF_WORK" -> tempMockOffWorkTime
            else -> "08:30"
        }
        val initialParts = currentTimeStr.split(":")
        val defaultHour = when (activeTimePickerTarget) {
            "FISH_OIL" -> 13
            "FORGET_CLOCK_OUT" -> 18
            "WORK_START" -> 8
            "FLEXIBLE_END" -> 9
            "LUNCH_DND_START" -> 12
            "LUNCH_DND_END" -> 13
            "MOCK_OFF_WORK" -> 17
            else -> 8
        }
        val defaultMinute = when (activeTimePickerTarget) {
            "FISH_OIL" -> 10
            "FORGET_CLOCK_OUT" -> 30
            "WORK_START" -> 30
            "FLEXIBLE_END" -> 30
            "LUNCH_DND_START" -> 0
            "LUNCH_DND_END" -> 0
            "MOCK_OFF_WORK" -> 30
            else -> 30
        }
        val initialHour = initialParts.getOrNull(0)?.toIntOrNull() ?: defaultHour
        val initialMinute = initialParts.getOrNull(1)?.toIntOrNull() ?: defaultMinute
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePickerDialog = false
                    var hour = timePickerState.hour
                    var minute = timePickerState.minute

                    if (activeTimePickerTarget == "FLEXIBLE_END") {
                        if (hour > 9 || (hour == 9 && minute > 30)) {
                            hour = 9
                            minute = 30
                        }
                    }

                    val formatted = String.format("%02d:%02d", hour, minute)
                    when (activeTimePickerTarget) {
                        "FISH_OIL" -> {
                            tempFishOilTime = formatted
                            autoSave(fishOilTimeVal = formatted)
                        }
                        "FORGET_CLOCK_OUT" -> {
                            tempForgetClockOutTime = formatted
                            autoSave(forgetClockOutTimeVal = formatted)
                        }
                        "WORK_START" -> {
                            tempWorkStart = formatted
                            autoSave(workStartVal = formatted)
                        }
                        "FLEXIBLE_END" -> {
                            tempFlexibleEnd = formatted
                            autoSave(flexibleEndVal = formatted)
                        }
                        "LUNCH_DND_START" -> {
                            tempLunchDndStartTime = formatted
                            autoSave(lunchDndStartTimeVal = formatted)
                        }
                        "LUNCH_DND_END" -> {
                            tempLunchDndEndTime = formatted
                            autoSave(lunchDndEndTimeVal = formatted)
                        }
                        "MOCK_OFF_WORK" -> {
                            tempMockOffWorkTime = formatted
                            autoSave(mockOffWorkTimeVal = formatted)
                        }
                    }
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    if (showPowerWarningDialog) {
        val dialogTitle = if (hasExactAlarmPermission) "提醒通知與耗電說明" else "需要精確鬧鐘權限"
        val dialogMessage = if (hasExactAlarmPermission) {
            "開啟精確下班提醒功能需要在背景排定精確鬧鐘，可能會稍微增加背景耗電量。\n\n是否確認開啟？"
        } else {
            "開啟精確下班提醒功能需要在背景排定精確鬧鐘，可能會稍微增加背景耗電量。\n\n為確保提醒能在精確時間觸發，請授權「鬧鐘和提醒事項」權限。\n\n點擊「前往設定」將自動開啟系統設定中的「鬧鐘和提醒事項」頁面。"
        }
        val confirmButtonText = if (hasExactAlarmPermission) "確認開啟" else "前往設定並開啟"

        AlertDialog(
            onDismissRequest = { showPowerWarningDialog = false },
            title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
            text = { Text(dialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showPowerWarningDialog = false
                        if (pendingToggleTarget == "BEFORE") tempNotifyBefore = true
                        if (pendingToggleTarget == "ON") tempNotifyOn = true
                        
                        autoSave(
                            notifyBeforeVal = tempNotifyBefore,
                            notifyOnVal = tempNotifyOn
                        )

                        if (!hasExactAlarmPermission) {
                            viewModel.openExactAlarmSettings()
                        } else if (!isIgnoringBattery) {
                            showBatteryOptimizationDialog = true
                        }
                    }
                ) {
                    Text(confirmButtonText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPowerWarningDialog = false
                        pendingToggleTarget = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            title = { Text("允許在背景持續運行", fontWeight = FontWeight.Bold) },
            text = {
                Text("為確保重開機後或手機深層休眠時依然能準時推播提醒，建議允許 App 在背景持續運行。\n\n點擊「前往允許」將會顯示系統權限要求。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        viewModel.requestIgnoreBatteryOptimizations()
                    }
                ) {
                    Text("前往允許")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                    Text("稍後設定")
                }
            }
        )
    }

    if (showDndPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showDndPermissionDialog = false },
            title = { Text("需要勿擾模式存取權限", fontWeight = FontWeight.Bold) },
            text = {
                Text("開啟「上班日午休勿擾」功能需要允許 App 存取系統勿擾模式 (Do Not Disturb)。\n\n點擊「前往授權」將會開啟系統設定中的「勿擾模式存取權限」頁面。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDndPermissionDialog = false
                        viewModel.openNotificationPolicySettings()
                    }
                ) {
                    Text("前往授權")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDndPermissionDialog = false }) {
                    Text("稍後設定")
                }
            }
        )
    }

    AnimatedContent(
        targetState = currentSubCategory,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "SettingsNavigation"
    ) { category ->
        when (category) {
            SettingsSubCategory.MAIN -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "系統設定選單",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    SettingsMenuItemCard(
                        title = "打卡驗證資訊",
                        subtitle = "員工工號、密碼、Cookie、enc、GPS 與設定檔匯入",
                        icon = Icons.Default.VpnKey,
                        onClick = { currentSubCategory = SettingsSubCategory.AUTH }
                    )

                    SettingsMenuItemCard(
                        title = "推播通知",
                        subtitle = "提前通知、可下班通知、補救提醒與魚油提醒",
                        icon = Icons.Default.Notifications,
                        onClick = { currentSubCategory = SettingsSubCategory.NOTIFICATIONS }
                    )

                    SettingsMenuItemCard(
                        title = "自動打卡功能",
                        subtitle = "定時自動打卡與上班日午休勿擾設定",
                        icon = Icons.Default.AutoMode,
                        onClick = { currentSubCategory = SettingsSubCategory.AUTO_CLOCK }
                    )

                    SettingsMenuItemCard(
                        title = "WearOS 整合",
                        subtitle = "手錶連動開關、裝置狀態與資料同步",
                        icon = Icons.Default.Watch,
                        onClick = { currentSubCategory = SettingsSubCategory.WEAR_OS }
                    )
                }
            }

            SettingsSubCategory.AUTH -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsSubHeader(
                        title = "打卡驗證資訊",
                        onBack = { currentSubCategory = SettingsSubCategory.MAIN },
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                        applyAndSaveParsedConfig(clipText)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("貼上", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("匯入", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = tempEmpId,
                                onValueChange = {
                                    tempEmpId = it
                                    autoSave(empIdVal = it)
                                },
                                label = { Text("員工工號 (EMPID)") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )

                            OutlinedTextField(
                                value = tempPassword,
                                onValueChange = {
                                    tempPassword = it
                                    autoSave(passVal = it)
                                },
                                label = { Text("密碼 (PASS)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "切換密碼顯示"
                                        )
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )

                            OutlinedTextField(
                                value = tempCookie,
                                onValueChange = {
                                    tempCookie = it
                                    autoSave(cookieVal = it)
                                },
                                label = { Text("Cookie") },
                                leadingIcon = { Icon(Icons.Default.Cookie, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )

                            OutlinedTextField(
                                value = tempEnc,
                                onValueChange = {
                                    tempEnc = it
                                    autoSave(encVal = it)
                                },
                                label = { Text("enc (加密金鑰)") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )

                            OutlinedTextField(
                                value = tempGps,
                                onValueChange = {
                                    tempGps = it
                                    autoSave(gpsVal = it)
                                },
                                label = { Text("GPS 座標 (緯度,經度)") },
                                leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                                trailingIcon = {
                                    TextButton(onClick = {
                                        tempGps = "22.987929,120.197210"
                                        autoSave(gpsVal = "22.987929,120.197210")
                                    }) {
                                        Text("重置", style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }

            SettingsSubCategory.NOTIFICATIONS -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsSubHeader(
                        title = "打卡提醒通知",
                        onBack = { currentSubCategory = SettingsSubCategory.MAIN }
                    )

                    // Show Exact Alarm Permission Warning Card ONLY if NOT granted
                    if (!hasExactAlarmPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "精確鬧鐘權限未開啟",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "建議開啟鬧鐘和提醒事項特殊存取權限以確保準時推播",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                TextButton(onClick = { viewModel.openExactAlarmSettings() }) {
                                    Text("前往設定")
                                }
                            }
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. 提前通知
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("提前通知", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("接近可下班時間時發送預警推播", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempNotifyBefore,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            pendingToggleTarget = "BEFORE"
                                            showPowerWarningDialog = true
                                        } else {
                                            tempNotifyBefore = false
                                            autoSave(notifyBeforeVal = false)
                                        }
                                    }
                                )
                            }

                            // 1 to 30 minutes dropdown
                            ExposedDropdownMenuBox(
                                expanded = advanceDropdownExpanded && tempNotifyBefore,
                                onExpandedChange = { if (tempNotifyBefore) advanceDropdownExpanded = !advanceDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "$tempAdvanceMinutes 分鐘",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = tempNotifyBefore,
                                    label = { Text("提前提醒時間") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = advanceDropdownExpanded && tempNotifyBefore)
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = advanceDropdownExpanded && tempNotifyBefore,
                                    onDismissRequest = { advanceDropdownExpanded = false }
                                ) {
                                    (1..30).forEach { minute ->
                                        DropdownMenuItem(
                                            text = { Text("$minute 分鐘") },
                                            onClick = {
                                                tempAdvanceMinutes = minute
                                                advanceDropdownExpanded = false
                                                autoSave(advanceMinutesVal = minute)
                                            }
                                        )
                                    }
                                }
                            }

                            // Live Updates switch (nested inside 提前通知)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("通知使用 Live Updates", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("開啟後於通知欄及狀態列提供即時動態時間倒數", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempUseLiveUpdates && tempNotifyBefore,
                                    enabled = tempNotifyBefore,
                                    onCheckedChange = { checked ->
                                        tempUseLiveUpdates = checked
                                        autoSave(useLiveUpdatesVal = checked)
                                    }
                                )
                            }

                            HorizontalDivider()

                            // 2. 可下班通知
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("可下班通知", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("工作時間滿 9 小時當下發送下班提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("可於通知直接打卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempNotifyOn,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            pendingToggleTarget = "ON"
                                            showPowerWarningDialog = true
                                        } else {
                                            tempNotifyOn = false
                                            autoSave(notifyOnVal = false)
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            // 3. 忘記打卡補救提醒
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("忘記打卡補救提醒", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("每日檢查若有上班卡卻未打下班卡時發送補救提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempForgetClockOutEnabled,
                                    onCheckedChange = { checked ->
                                        tempForgetClockOutEnabled = checked
                                        autoSave(forgetClockOutEnabledVal = checked)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "檢查時間",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (tempForgetClockOutEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Surface(
                                    onClick = {
                                        if (tempForgetClockOutEnabled) {
                                            activeTimePickerTarget = "FORGET_CLOCK_OUT"
                                            showTimePickerDialog = true
                                        }
                                    },
                                    enabled = tempForgetClockOutEnabled,
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (tempForgetClockOutEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (tempForgetClockOutEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tempForgetClockOutTime,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tempForgetClockOutEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            // 4. 挪銘專屬吃魚油通知
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("挪銘專屬吃魚油通知", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("再不吃魚油啊，身體壞光光", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempFishOilEnabled,
                                    onCheckedChange = { checked ->
                                        tempFishOilEnabled = checked
                                        autoSave(fishOilEnabledVal = checked)
                                        if (checked && !isIgnoringBattery) {
                                            showBatteryOptimizationDialog = true
                                        }
                                    }
                                )
                            }

                            AnimatedVisibility(visible = tempFishOilEnabled) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Time Display & Edit Pill
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("提醒時間", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Surface(
                                            onClick = {
                                                activeTimePickerTarget = "FISH_OIL"
                                                showTimePickerDialog = true
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = tempFishOilTime,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }

                                    Text("選擇重複日期：", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Google Clock Alarm Style Day-of-Week Selector Pills
                                    val selectedDaysSet = remember(tempFishOilDays) { 
                                        tempFishOilDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet() 
                                    }
                                    val dayLabels = listOf("日", "一", "二", "三", "四", "五", "六")
                                    val dayIsoValues = listOf(7, 1, 2, 3, 4, 5, 6)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        dayLabels.forEachIndexed { index, label ->
                                            val isoVal = dayIsoValues[index]
                                            val isSelected = selectedDaysSet.contains(isoVal)

                                            Surface(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clickable {
                                                        val newSet = if (isSelected) {
                                                            selectedDaysSet - isoVal
                                                        } else {
                                                            selectedDaysSet + isoVal
                                                        }
                                                        tempFishOilDays = newSet.sorted().joinToString(",")
                                                        autoSave(fishOilDaysVal = tempFishOilDays)
                                                    },
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            // 5. 模擬下班時間 (測試 Live Update 用)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("模擬下班時間 (測試 Live Update)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("僅用於測試 Live Update 倒數推播效果，即便開啟自動打卡亦絕不發起真實打卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = {
                                            activeTimePickerTarget = "MOCK_OFF_WORK"
                                            showTimePickerDialog = true
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = tempMockOffWorkTime,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.triggerMockLiveUpdateNotification(tempMockOffWorkTime) },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("發送測試通知")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsSubCategory.AUTO_CLOCK -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsSubHeader(
                        title = "自動打卡功能",
                        onBack = { currentSubCategory = SettingsSubCategory.MAIN }
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. 自動打上班卡 Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("自動打上班卡", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("於指定工作日時間進行背景自動簽到打卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempAutoClockIn,
                                    onCheckedChange = { checked ->
                                        tempAutoClockIn = checked
                                        autoSave(autoClockInVal = checked)
                                        if (checked && !isIgnoringBattery) {
                                            showBatteryOptimizationDialog = true
                                        }
                                    }
                                )
                            }

                            // 2. 設定上班時間 TimePicker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "設定上班時間",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (tempAutoClockIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Surface(
                                    onClick = {
                                        if (tempAutoClockIn) {
                                            activeTimePickerTarget = "WORK_START"
                                            showTimePickerDialog = true
                                        }
                                    },
                                    enabled = tempAutoClockIn,
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (tempAutoClockIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (tempAutoClockIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tempWorkStart,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tempAutoClockIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            // 3. 彈性時間打上班卡 Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "彈性時間打上班卡",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tempAutoClockIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "可設定彈性時間來打卡，模擬每天不固定時間上班",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (tempAutoClockIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = tempFlexibleClockIn && tempAutoClockIn,
                                    enabled = tempAutoClockIn,
                                    onCheckedChange = { checked ->
                                        tempFlexibleClockIn = checked
                                        autoSave(flexibleClockInVal = checked)
                                    }
                                )
                            }

                            // 4. 彈性時間範圍 TimePickers (Range)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "彈性時間範圍",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (tempAutoClockIn && tempFlexibleClockIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Start Time (read-only, mirrors tempWorkStart)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = tempWorkStart,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = if (tempAutoClockIn && tempFlexibleClockIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    Text("~", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                    // End Time
                                    Surface(
                                        onClick = {
                                            if (tempAutoClockIn && tempFlexibleClockIn) {
                                                activeTimePickerTarget = "FLEXIBLE_END"
                                                showTimePickerDialog = true
                                            }
                                        },
                                        enabled = tempAutoClockIn && tempFlexibleClockIn,
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (tempAutoClockIn && tempFlexibleClockIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Text(
                                            text = tempFlexibleEnd,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = if (tempAutoClockIn && tempFlexibleClockIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            // 5. 自動打下班卡 Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("自動打下班卡", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("根據上班卡時間自動加9小時來打下班卡，將關閉「可下班通知」", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempAutoClockOut,
                                    onCheckedChange = { checked ->
                                        tempAutoClockOut = checked
                                        if (checked) {
                                            tempNotifyOn = false
                                        }
                                        autoSave(autoClockOutVal = checked, notifyOnVal = tempNotifyOn)
                                        if (checked && !isIgnoringBattery) {
                                            showBatteryOptimizationDialog = true
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            // 6. 上班日午休勿擾 Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("上班日午休勿擾", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("每日 12:00 自動檢查若今日有打上班卡，則自動開啟勿擾模式，並於 13:00 自動關閉", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = tempLunchDndEnabled,
                                    onCheckedChange = { checked ->
                                        tempLunchDndEnabled = checked
                                        autoSave(lunchDndEnabledVal = checked)
                                        if (checked) {
                                            if (!viewModel.isNotificationPolicyAccessGranted()) {
                                                showDndPermissionDialog = true
                                            } else if (!isIgnoringBattery) {
                                                showBatteryOptimizationDialog = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SettingsSubCategory.WEAR_OS -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsSubHeader(
                        title = "WearOS 整合",
                        onBack = { currentSubCategory = SettingsSubCategory.MAIN }
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (enableWatchSync) Icons.Default.Watch else Icons.Default.WatchOff,
                                        contentDescription = null,
                                        tint = if (enableWatchSync) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "啟用 Wear OS 手錶連動",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Switch(
                                    checked = enableWatchSync,
                                    onCheckedChange = { viewModel.setEnableWatchSync(it) }
                                )
                            }

                            Text(
                                text = if (enableWatchSync) 
                                    "已開啟手錶連動功能。系統會將您的打卡設定與時間狀態同步至已配對的 Wear OS 手錶。" 
                                else 
                                    "若您擁有 Wear OS 手錶，開啟此開關即可進行雙向同步與打卡連動。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = enableWatchSync) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (connectedNodesCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (connectedNodesCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("手錶連線狀態", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = if (connectedNodesCount > 0) "已連線 $connectedNodesCount 個裝置" else "尚未偵測到已連線手錶",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(onClick = { viewModel.checkConnectedWearables() }) {
                                        Icon(Icons.Default.Sync, contentDescription = "重新偵測")
                                    }
                                }
                            }

                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "手錶同步動作",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Button(
                                        onClick = { viewModel.syncAllToWearable() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("條即與 Wear OS 手錶同步", style = MaterialTheme.typography.labelLarge)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.fetchCloudData() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("重新載入並推送至手錶", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenuItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSubHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        actions()
    }
}
