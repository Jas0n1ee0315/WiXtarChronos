package com.jas0n1ee.wixtar.chronos.mobile.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MobileClockViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val repository = ClockRepository(storage)
    private val notificationHelper = MobileNotificationHelper(application)

    val empId = storage.empId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val password = storage.password.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val cookie = storage.cookie.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val enc = storage.enc.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val gpsLocation = storage.gpsLocation.stateIn(viewModelScope, SharingStarted.Eagerly, "22.987929,120.197210")
    val notifyBeforeOffWork = storage.notifyBeforeOffWork.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyOnOffWork = storage.notifyOnOffWork.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyAdvanceMinutes = storage.notifyAdvanceMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 10)
    val useLiveUpdates = storage.useLiveUpdates.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val enableWatchSync = storage.enableWatchSync.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val forgetClockOutNotifyEnabled = storage.forgetClockOutNotifyEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val forgetClockOutNotifyTime = storage.forgetClockOutNotifyTime.stateIn(viewModelScope, SharingStarted.Eagerly, "18:30")

    val fishOilNotifyEnabled = storage.fishOilNotifyEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val fishOilNotifyTime = storage.fishOilNotifyTime.stateIn(viewModelScope, SharingStarted.Eagerly, "13:10")
    val fishOilNotifyDays = storage.fishOilNotifyDays.stateIn(viewModelScope, SharingStarted.Eagerly, "1,2,3,4,5,6,7")

    private val _cloudInTime = MutableStateFlow<String?>(null)
    private val _cloudOutTime = MutableStateFlow<String?>(null)

    private val _isHrSystemNormal = MutableStateFlow<Boolean?>(null)
    val isHrSystemNormal: StateFlow<Boolean?> = _isHrSystemNormal

    val cloudInTime: StateFlow<String?> = _cloudInTime
    val cloudOutTime: StateFlow<String?> = _cloudOutTime

    val clockInTime: StateFlow<String?> = combine(_cloudInTime, storage.clockInTime) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clockOutTime: StateFlow<String?> = combine(_cloudOutTime, storage.clockOutTime) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isClockingIn = MutableStateFlow(false)
    val isClockingIn: StateFlow<Boolean> = _isClockingIn

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _connectedNodesCount = MutableStateFlow(0)
    val connectedNodesCount: StateFlow<Int> = _connectedNodesCount

    init {
        viewModelScope.launch {
            storage.clearClockDataIfExpired()
            checkConnectedWearables()
            checkHrHealth()
            
            cookie.collect { ck ->
                if (ck.isNotBlank()) {
                    fetchCloudData()
                }
            }
        }
    }

    fun checkHrHealth() {
        viewModelScope.launch {
            _isHrSystemNormal.value = repository.checkHrSystemHealth()
        }
    }

    fun setEnableWatchSync(enable: Boolean) {
        viewModelScope.launch {
            storage.saveEnableWatchSync(enable)
            if (enable) {
                syncAllToWearable()
            }
        }
    }

    fun checkConnectedWearables() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(getApplication<Application>()).connectedNodes)
                _connectedNodesCount.value = nodes.size
            } catch (e: Exception) {
                Log.e("MobileClockViewModel", "Failed to check connected nodes", e)
                _connectedNodesCount.value = 0
            }
        }
    }

    fun fetchCloudData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            checkHrHealth()
            val ck = storage.cookie.first().trim()
            if (ck.isNotBlank()) {
                repository.getCloudClockInfo(ck).onSuccess { (cloudIn, cloudOut, _) ->
                    _cloudInTime.value = cloudIn
                    _cloudOutTime.value = cloudOut

                    cloudIn?.let { storage.saveClockIn(it) }
                    cloudOut?.let { storage.saveClockOut(it) }

                    if (enableWatchSync.value) {
                        val todayStr = java.time.LocalDate.now().toString()
                        WearableSyncManager.syncClockStatus(getApplication(), cloudIn, cloudOut, todayStr)
                    }
                }.onFailure { e ->
                    _error.value = "雲端同步失敗: ${e.message}"
                }
            }
            _isRefreshing.value = false
        }
    }

    fun saveSettings(
        empIdVal: String,
        passVal: String,
        cookieVal: String,
        encVal: String,
        gpsVal: String,
        notifyBeforeVal: Boolean,
        notifyOnVal: Boolean,
        advanceMinutesVal: Int = 10,
        useLiveUpdatesVal: Boolean = false,
        forgetClockOutEnabledVal: Boolean = false,
        forgetClockOutTimeVal: String = "18:30",
        fishOilEnabledVal: Boolean = false,
        fishOilTimeVal: String = "13:10",
        fishOilDaysVal: String = "1,2,3,4,5,6,7"
    ) {
        viewModelScope.launch {
            storage.saveSettings(
                empIdVal,
                passVal,
                cookieVal,
                encVal,
                gpsVal,
                notifyBeforeVal,
                notifyOnVal,
                advanceMinutesVal,
                useLiveUpdatesVal,
                forgetClockOutEnabledVal,
                forgetClockOutTimeVal,
                fishOilEnabledVal,
                fishOilTimeVal,
                fishOilDaysVal
            )

            if (forgetClockOutEnabledVal) {
                notificationHelper.scheduleForgetClockOutReminders(forgetClockOutTimeVal)
            }

            if (fishOilEnabledVal) {
                notificationHelper.scheduleFishOilReminders(fishOilTimeVal, fishOilDaysVal)
            }

            if (enableWatchSync.value) {
                WearableSyncManager.syncSettings(
                    context = getApplication(),
                    empId = empIdVal,
                    pass = passVal,
                    cookie = cookieVal,
                    enc = encVal,
                    gps = gpsVal,
                    notifyBefore = notifyBeforeVal,
                    notifyOn = notifyOnVal
                )
            }
        }
    }

    fun importConfigFromText(content: String): ConfigParseResult {
        var cookieParsed = ""
        var encParsed = ""
        var empIdParsed = ""
        var passParsed = ""
        var gpsParsed = ""

        val trimmedContent = content.trim()

        if ((trimmedContent.startsWith("{") && trimmedContent.endsWith("}")) ||
            (trimmedContent.startsWith("[") && trimmedContent.endsWith("]"))) {
            try {
                val json = if (trimmedContent.startsWith("{")) {
                    JSONObject(trimmedContent)
                } else {
                    JSONArray(trimmedContent).optJSONObject(0) ?: JSONObject()
                }
                empIdParsed = json.optString("empId").ifBlank { json.optString("empid").ifBlank { json.optString("emp_id").ifBlank { json.optString("工號") } } }
                passParsed = json.optString("pass").ifBlank { json.optString("password").ifBlank { json.optString("PASS").ifBlank { json.optString("密碼") } } }
                cookieParsed = json.optString("cookie").ifBlank { json.optString("Cookie").ifBlank { json.optString("COOKIE") } }
                encParsed = json.optString("enc").ifBlank { json.optString("ENC") }
                gpsParsed = json.optString("gps").ifBlank { json.optString("GPS").ifBlank { json.optString("gpsLocation") } }
            } catch (e: Exception) {
            }
        }

        if (cookieParsed.isBlank() || encParsed.isBlank() || empIdParsed.isBlank() || passParsed.isBlank()) {
            val lines = content.lines()
            val cookieBuilder = StringBuilder()
            val encBuilder = StringBuilder()
            var currentSection = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue
                val lower = trimmed.lowercase()

                if (lower.startsWith("empid") || lower.startsWith("emp_id") || lower.startsWith("emp") || lower.contains("工號")) {
                    if (trimmed.contains(":") || trimmed.contains("=")) {
                        val valStr = trimmed.substringAfter(":", "").substringAfter("=", "").trim()
                        if (valStr.isNotBlank()) {
                            empIdParsed = valStr
                            continue
                        }
                    } else if (empIdParsed.isBlank()) {
                        currentSection = "EMPID"
                        continue
                    }
                }

                if (lower.startsWith("password") || lower.startsWith("pass") || lower.contains("密碼")) {
                    if (trimmed.contains(":") || trimmed.contains("=")) {
                        val valStr = trimmed.substringAfter(":", "").substringAfter("=", "").trim()
                        if (valStr.isNotBlank()) {
                            passParsed = valStr
                            continue
                        }
                    } else if (passParsed.isBlank()) {
                        currentSection = "PASS"
                        continue
                    }
                }

                if (lower.startsWith("gps") || lower.contains("座標")) {
                    if (trimmed.contains(":") || trimmed.contains("=")) {
                        val valStr = trimmed.substringAfter(":", "").substringAfter("=", "").trim()
                        if (valStr.isNotBlank()) {
                            gpsParsed = valStr
                            continue
                        }
                    }
                }

                if (lower.startsWith("cookie") || lower.contains("cookie:")) {
                    if (trimmed.contains(":") || trimmed.contains("=")) {
                        val valStr = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else trimmed.substringAfter("=").trim()
                        if (valStr.isNotBlank()) {
                            if (cookieBuilder.isNotEmpty()) cookieBuilder.append("\n")
                            cookieBuilder.append(valStr)
                        }
                    }
                    currentSection = "COOKIE"
                    continue
                }

                if ((lower.startsWith("enc") || lower.contains("enc:")) && !lower.contains("encode")) {
                    if (trimmed.contains(":") || trimmed.contains("=")) {
                        val valStr = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else trimmed.substringAfter("=").trim()
                        if (valStr.isNotBlank()) {
                            if (encBuilder.isNotEmpty()) encBuilder.append("\n")
                            encBuilder.append(valStr)
                        }
                    }
                    currentSection = "ENC"
                    continue
                }

                when (currentSection) {
                    "EMPID" -> {
                        if (empIdParsed.isBlank()) empIdParsed = trimmed
                    }
                    "PASS" -> {
                        if (passParsed.isBlank()) passParsed = trimmed
                    }
                    "COOKIE" -> {
                        if (cookieBuilder.isNotEmpty()) cookieBuilder.append("\n")
                        cookieBuilder.append(trimmed)
                    }
                    "ENC" -> {
                        if (encBuilder.isNotEmpty()) encBuilder.append("\n")
                        encBuilder.append(trimmed)
                    }
                }
            }

            if (cookieBuilder.isNotEmpty() && cookieParsed.isBlank()) {
                cookieParsed = cookieBuilder.toString().trim()
            }
            if (encBuilder.isNotEmpty() && encParsed.isBlank()) {
                encParsed = encBuilder.toString().trim()
            }
        }

        _message.value = "已成功解析設定檔！"
        return ConfigParseResult(
            cookie = cookieParsed,
            enc = encParsed,
            empId = empIdParsed,
            password = passParsed,
            gps = gpsParsed
        )
    }

    fun syncAllToWearable() {
        viewModelScope.launch {
            try {
                val id = storage.empId.first()
                val pass = storage.password.first()
                val ck = storage.cookie.first()
                val en = storage.enc.first()
                val gps = storage.gpsLocation.first()
                val notifyBefore = storage.notifyBeforeOffWork.first()
                val notifyOn = storage.notifyOnOffWork.first()

                WearableSyncManager.syncSettings(
                    context = getApplication(),
                    empId = id,
                    pass = pass,
                    cookie = ck,
                    enc = en,
                    gps = gps,
                    notifyBefore = notifyBefore,
                    notifyOn = notifyOn
                )

                val rawIn = _cloudInTime.value ?: storage.clockInTime.first()
                val rawOut = _cloudOutTime.value ?: storage.clockOutTime.first()
                val todayStr = java.time.LocalDate.now().toString()

                WearableSyncManager.syncClockStatus(
                    context = getApplication(),
                    clockIn = rawIn,
                    clockOut = rawOut,
                    lastDate = todayStr
                )

                checkConnectedWearables()
                _message.value = "手錶同步完成 (已連接裝置: ${_connectedNodesCount.value})"
            } catch (e: Exception) {
                _error.value = "同步至手錶失敗: ${e.message}"
            }
        }
    }

    fun doClockAction() {
        viewModelScope.launch {
            _isClockingIn.value = true
            _error.value = null

            val id = storage.empId.first().trim()
            val pass = storage.password.first().trim()
            val ck = storage.cookie.first().trim()
            val en = storage.enc.first().trim()
            val gps = storage.gpsLocation.first().trim()

            if (id.isBlank() || pass.isBlank() || ck.isBlank() || en.isBlank()) {
                _error.value = "打卡失敗：請先至 [系統設定] 完整填寫工號、密碼、Cookie 與 enc"
                _isClockingIn.value = false
                return@launch
            }

            val currentIn = clockInTime.value
            val isClockingInAction = currentIn.isNullOrBlank()

            val result = repository.clockIn(id, pass, ck, en, gps)
            if (result.isSuccess) {
                val time = result.getOrNull() ?: ""
                if (isClockingInAction) {
                    storage.saveClockIn(time)
                    notificationHelper.scheduleReminders(time, notifyAdvanceMinutes.value, useLiveUpdates.value)
                    _message.value = "打卡成功: 上班卡 ($time)"
                } else {
                    storage.saveClockOut(time)
                    notificationHelper.cancelLiveUpdateNotification()
                    _message.value = "打卡成功: 下班卡 ($time)"
                }

                if (enableWatchSync.value) {
                    val todayStr = java.time.LocalDate.now().toString()
                    WearableSyncManager.syncClockStatus(
                        context = getApplication(),
                        clockIn = if (isClockingInAction) time else _cloudInTime.value,
                        clockOut = if (!isClockingInAction) time else _cloudOutTime.value,
                        lastDate = todayStr
                    )
                }

                fetchCloudData()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "打卡失敗"
            }
            _isClockingIn.value = false
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return notificationHelper.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings() {
        notificationHelper.openExactAlarmSettings()
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return notificationHelper.isIgnoringBatteryOptimizations()
    }

    fun requestIgnoreBatteryOptimizations() {
        notificationHelper.requestIgnoreBatteryOptimizations()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}

data class ConfigParseResult(
    val cookie: String,
    val enc: String,
    val empId: String,
    val password: String,
    val gps: String
)
