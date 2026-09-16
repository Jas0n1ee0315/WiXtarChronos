package com.jas0n1ee.wixtar.chronos.mobile.presentation.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MobileClockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    viewModel: MobileClockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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

    var isPasswordVisible by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotBlank()) {
                    val result = viewModel.importConfigFromText(content)
                    if (result.cookie.isNotBlank()) tempCookie = result.cookie
                    if (result.enc.isNotBlank()) tempEnc = result.enc
                    if (result.empId.isNotBlank()) tempEmpId = result.empId
                    if (result.password.isNotBlank()) tempPassword = result.password
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & File Import
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "系統設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("匯入設定檔", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Credentials Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "員工驗證資訊",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = tempEmpId,
                    onValueChange = { tempEmpId = it },
                    label = { Text("員工工號 (EMPID)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
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
                    onValueChange = { tempCookie = it },
                    label = { Text("Cookie") },
                    leadingIcon = { Icon(Icons.Default.Cookie, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = tempEnc,
                    onValueChange = { tempEnc = it },
                    label = { Text("enc (加密金鑰)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = tempGps,
                    onValueChange = { tempGps = it },
                    label = { Text("GPS 座標 (緯度,經度)") },
                    leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { tempGps = "22.987929,120.197210" }) {
                            Text("重置", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Notification Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下班提醒通知", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("下班前 10 分鐘通知", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = tempNotifyBefore,
                        onCheckedChange = { tempNotifyBefore = it }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("滿 9 小時可下班通知", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = tempNotifyOn,
                        onCheckedChange = { tempNotifyOn = it }
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                viewModel.saveSettings(
                    tempEmpId,
                    tempPassword,
                    tempCookie,
                    tempEnc,
                    tempGps,
                    tempNotifyBefore,
                    tempNotifyOn
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("儲存設定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
