package com.jas0n1ee.wixtar.chronos.mobile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MobileClockViewModel

/**
 * 設定 →「專案工時同步（PJM）」
 *
 * - 開關：請假單送出成功後，自動到 pjm.wixtar.com 新增「Z1150001 請假 / ADM行政事務」工時。
 * - 帳號：留空時沿用打卡用的員工工號。
 * - 密碼：以 Android Keystore 加密後儲存；畫面上不會再顯示已儲存的密碼，留空代表不變更。
 */
@Composable
fun PjmSyncSettings(
    viewModel: MobileClockViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val enabled by viewModel.pjmSyncEnabled.collectAsState()
    val savedUsername by viewModel.pjmUsername.collectAsState()
    val hasPassword by viewModel.hasPjmPassword.collectAsState()
    val empId by viewModel.empId.collectAsState()
    val isTesting by viewModel.isTestingPjm.collectAsState()
    val testResult by viewModel.pjmTestResult.collectAsState()

    var tempUsername by remember(savedUsername) { mutableStateOf(savedUsername) }
    var tempPassword by remember { mutableStateOf("") } // 只放「新輸入」的密碼，不回填已儲存的
    var isPasswordVisible by remember { mutableStateOf(false) }
    var passwordSavedHint by remember { mutableStateOf(false) }

    // 尚未設定帳號時，預設帶入打卡工號（PJM 帳號即員工工號）
    LaunchedEffect(savedUsername, empId) {
        if (savedUsername.isBlank() && tempUsername.isBlank() && empId.isNotBlank()) tempUsername = empId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSubHeader(title = "專案工時同步（PJM）", onBack = onBack)

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 啟用開關
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("請假後自動填寫 PJM 工時", fontWeight = FontWeight.Bold)
                        Text(
                            "請假單送出成功後，依請假時段新增「請假」研發工時（狀態為待審核）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.savePjmSettings(it, tempUsername, null) }
                    )
                }

                // 帳號
                OutlinedTextField(
                    value = tempUsername,
                    onValueChange = {
                        tempUsername = it.uppercase()
                        viewModel.savePjmSettings(enabled, tempUsername, null)
                    },
                    label = { Text("PJM 帳號（同員工工號）") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                // 密碼：只寫入、不回顯
                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = {
                        tempPassword = it
                        passwordSavedHint = false
                    },
                    label = { Text("PJM 密碼") },
                    placeholder = { if (hasPassword) Text("已加密儲存，留空表示不變更") },
                    supportingText = {
                        Text(
                            when {
                                passwordSavedHint -> "密碼已加密儲存"
                                hasPassword -> "目前已設定密碼（以 Android Keystore 加密，不會顯示）"
                                else -> "尚未設定密碼"
                            }
                        )
                    },
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.savePjmSettings(enabled, tempUsername, tempPassword)
                            tempPassword = ""
                            passwordSavedHint = true
                        },
                        enabled = tempPassword.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("儲存密碼") }

                    if (hasPassword) {
                        TextButton(onClick = {
                            // 清除密碼並關閉同步，避免開著同步卻沒有密碼
                            viewModel.savePjmSettings(false, tempUsername, "")
                            tempPassword = ""
                            passwordSavedHint = false
                        }) { Text("清除密碼") }
                    }
                }

                // 測試登入：有輸入新密碼就用新密碼，否則用已儲存的密碼
                OutlinedButton(
                    onClick = { viewModel.testPjmLogin(tempUsername, tempPassword) },
                    enabled = !isTesting && (tempPassword.isNotEmpty() || hasPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在登入 PJM...")
                    } else {
                        Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("測試登入")
                    }
                }

                testResult?.let { (ok, msg) ->
                    Text(
                        text = msg,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 說明
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("同步規則", fontWeight = FontWeight.Bold)
                listOf(
                    "工時寫入「Z1150001 請假」，分類「ADM行政事務」，說明「請假」。",
                    "依請假時段逐日計算：扣除 12:00–13:00 午休，每天最多 8 小時，以 0.5 小時為單位。",
                    "略過週六日、國定假日，以及當天已有請假工時的日期。",
                    "假別「工出」不會同步。",
                    "同步失敗不影響請假單；結果視窗會顯示原因，請到 PJM 網站手動補填。",
                    "PJM 為內部系統，介接方式非官方提供，網站改版時可能需要更新 App。"
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
