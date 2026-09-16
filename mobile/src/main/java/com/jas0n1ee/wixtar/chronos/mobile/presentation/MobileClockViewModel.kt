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

class MobileClockViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val repository = ClockRepository(storage)
    private val notificationHelper = MobileNotificationHelper(application)

    val empId = storage.empId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val password = storage.password.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val cookie = storage.cookie.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val enc = storage.enc.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val gpsLocation = storage.gpsLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "22.987929,120.197210")
    val notifyBeforeOffWork = storage.notifyBeforeOffWork.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val notifyOnOffWork = storage.notifyOnOffWork.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val enableWatchSync = storage.enableWatchSync.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _cloudInTime = MutableStateFlow<String?>(null)
    private val _cloudOutTime = MutableStateFlow<String?>(null)
    private val _cloudRawJson = MutableStateFlow<String>("")

    private val _isHrSystemNormal = MutableStateFlow<Boolean?>(null)
    val isHrSystemNormal: StateFlow<Boolean?> = _isHrSystemNormal

    val cloudInTime: StateFlow<String?> = _cloudInTime
    val cloudOutTime: StateFlow<String?> = _cloudOutTime
    val cloudRawJson: StateFlow<String> = _cloudRawJson

    val clockInTime: StateFlow<String?> = combine(_cloudInTime, storage.clockInTime) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val clockOutTime: StateFlow<String?> = combine(_cloudOutTime, storage.clockOutTime) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

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
            val ck = cookie.value
            if (ck.isNotBlank()) {
                repository.getCloudClockInfo(ck).onSuccess { (cloudIn, cloudOut, rawJson) ->
                    _cloudInTime.value = cloudIn
                    _cloudOutTime.value = cloudOut
                    _cloudRawJson.value = rawJson

                    cloudIn?.let { storage.saveClockIn(it) }
                    cloudOut?.let { storage.saveClockOut(it) }

                    if (enableWatchSync.value) {
                        val todayStr = java.time.LocalDate.now().toString()
                        WearableSyncManager.syncClockStatus(getApplication(), cloudIn, cloudOut, todayStr)
                    }
                }.onFailure { e ->
                    _error.value = "雲端同步失敗: ${e.message}"
                }
            } else {
                _error.value = "未設定 Cookie，請至設定頁填寫"
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
        notifyOnVal: Boolean
    ) {
        viewModelScope.launch {
            storage.saveSettings(
                empIdVal,
                passVal,
                cookieVal,
                encVal,
                gpsVal,
                notifyBeforeVal,
                notifyOnVal
            )

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

            _message.value = "設定已完成儲存"
            fetchCloudData()
        }
    }

    fun importConfigFromText(content: String): ConfigParseResult {
        val lines = content.lines()
        val cookieBuilder = StringBuilder()
        val encBuilder = StringBuilder()
        var empIdParsed = ""
        var passParsed = ""

        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()

            if (lower.startsWith("empid") || lower.startsWith("emp_id") || lower.contains("工號")) {
                val value = trimmed.substringAfter(":", "").substringAfter("=", "").trim()
                if (value.isNotBlank()) empIdParsed = value
                continue
            }

            if (lower.startsWith("password") || lower.startsWith("pass") || lower.contains("密碼")) {
                val value = trimmed.substringAfter(":", "").substringAfter("=", "").trim()
                if (value.isNotBlank()) passParsed = value
                continue
            }

            if (lower.contains("cookie")) {
                currentSection = "COOKIE"
                continue
            }

            if (lower.contains("enc") && !lower.contains("encode")) {
                currentSection = "ENC"
                continue
            }

            when (currentSection) {
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

        val parsedCookie = cookieBuilder.toString().trim()
        val parsedEnc = encBuilder.toString().trim()

        _message.value = "已成功解析設定檔！"
        return ConfigParseResult(
            cookie = parsedCookie,
            enc = parsedEnc,
            empId = empIdParsed,
            password = passParsed
        )
    }

    fun syncAllToWearable() {
        viewModelScope.launch {
            try {
                val id = empId.value
                val pass = password.value
                val ck = cookie.value
                val en = enc.value
                val gps = gpsLocation.value
                val notifyBefore = notifyBeforeOffWork.value
                val notifyOn = notifyOnOffWork.value

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

            val id = empId.value
            val pass = password.value
            val ck = cookie.value
            val en = enc.value
            val gps = gpsLocation.value

            val currentIn = clockInTime.value

            val isClockingInAction = currentIn.isNullOrBlank()

            val result = repository.clockIn(id, pass, ck, en, gps)
            if (result.isSuccess) {
                val time = result.getOrNull() ?: ""
                if (isClockingInAction) {
                    storage.saveClockIn(time)
                    notificationHelper.scheduleReminders(time)
                    _message.value = "打卡成功: 上班卡 ($time)"
                } else {
                    storage.saveClockOut(time)
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
    val password: String
)
