package com.jas0n1ee.wixtar.chronos.mobile.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.AttendanceRecord
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.LeaveAttachment
import com.jas0n1ee.wixtar.chronos.data.LeaveConfirmData
import com.jas0n1ee.wixtar.chronos.data.LeavePageInfo
import com.jas0n1ee.wixtar.chronos.data.MakeupPunchPageInfo
import com.jas0n1ee.wixtar.chronos.data.MidnightAlarmHelper
import com.jas0n1ee.wixtar.chronos.data.SavedAgent
import com.jas0n1ee.wixtar.chronos.data.VibrationUtil
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronos.mobile.notification.MobileNotificationHelper
import com.jas0n1ee.wixtar.chronos.mobile.worker.DailyResetWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

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

    val lunchDndEnabled = storage.lunchDndEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lunchDndStartTime = storage.lunchDndStartTime.stateIn(viewModelScope, SharingStarted.Eagerly, "12:00")
    val lunchDndEndTime = storage.lunchDndEndTime.stateIn(viewModelScope, SharingStarted.Eagerly, "13:00")

    val mockOffWorkTime = storage.mockOffWorkTime.stateIn(viewModelScope, SharingStarted.Eagerly, "17:30")

    val agentEmpId = storage.agentEmpId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val agentName = storage.agentName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val agentDept = storage.agentDept.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _isQueryingAgent = MutableStateFlow(false)
    val isQueryingAgent: StateFlow<Boolean> = _isQueryingAgent

    private val _cloudInTime = MutableStateFlow<String?>(null)
    private val _cloudOutTime = MutableStateFlow<String?>(null)
    private val _cloudDate = MutableStateFlow<String?>(null)
    private val _currentDate = MutableStateFlow(LocalDate.now().toString())

    private val _isHrSystemNormal = MutableStateFlow<Boolean?>(null)
    val isHrSystemNormal: StateFlow<Boolean?> = _isHrSystemNormal

    private val _hrRetryCountdown = MutableStateFlow(3)
    val hrRetryCountdown: StateFlow<Int> = _hrRetryCountdown

    private var hrRetryJob: Job? = null

    val cloudInTime: StateFlow<String?> = _cloudInTime
    val cloudOutTime: StateFlow<String?> = _cloudOutTime

    val clockInTime: StateFlow<String?> = combine(_cloudInTime, storage.clockInTime, _cloudDate, _currentDate) { cloud, local, cloudDate, today ->
        val validCloud = if (cloudDate == today) cloud else null
        val cleanCloud = validCloud?.substringBefore(" ")?.take(5)
        val cleanLocal = local?.take(5)

        when {
            !cleanCloud.isNullOrBlank() -> "$cleanCloud (雲端)"
            !cleanLocal.isNullOrBlank() -> "$cleanLocal (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clockOutTime: StateFlow<String?> = combine(_cloudOutTime, storage.clockOutTime, _cloudDate, _currentDate) { cloud, local, cloudDate, today ->
        val validCloud = if (cloudDate == today) cloud else null
        val cleanCloud = validCloud?.substringBefore(" ")?.take(5)
        val cleanLocal = local?.take(5)

        when {
            !cleanCloud.isNullOrBlank() -> "$cleanCloud (雲端)"
            !cleanLocal.isNullOrBlank() -> "$cleanLocal (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isClockingIn = MutableStateFlow(false)
    val isClockingIn: StateFlow<Boolean> = _isClockingIn

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _connectedNodesCount = MutableStateFlow(0)
    val connectedNodesCount: StateFlow<Int> = _connectedNodesCount

    private val _attendanceStartDate = MutableStateFlow(LocalDate.now().minusWeeks(2))
    val attendanceStartDate: StateFlow<LocalDate> = _attendanceStartDate

    private val _attendanceEndDate = MutableStateFlow(LocalDate.now())
    val attendanceEndDate: StateFlow<LocalDate> = _attendanceEndDate

    private val _attendanceSkind = MutableStateFlow("B")
    val attendanceSkind: StateFlow<String> = _attendanceSkind

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    private val _isLoadingAttendance = MutableStateFlow(false)
    val isLoadingAttendance: StateFlow<Boolean> = _isLoadingAttendance

    private val _attendanceError = MutableStateFlow<String?>(null)
    val attendanceError: StateFlow<String?> = _attendanceError

    private val _leavePageInfo = MutableStateFlow<LeavePageInfo?>(null)
    val leavePageInfo: StateFlow<LeavePageInfo?> = _leavePageInfo

    private val _isLoadingLeaveInfo = MutableStateFlow(false)
    val isLoadingLeaveInfo: StateFlow<Boolean> = _isLoadingLeaveInfo

    private val _isSubmittingLeave = MutableStateFlow(false)
    val isSubmittingLeave: StateFlow<Boolean> = _isSubmittingLeave

    private val _isPreparingLeaveConfirm = MutableStateFlow(false)
    val isPreparingLeaveConfirm: StateFlow<Boolean> = _isPreparingLeaveConfirm

    private val _pendingLeaveConfirm = MutableStateFlow<LeaveConfirmData?>(null)
    val pendingLeaveConfirm: StateFlow<LeaveConfirmData?> = _pendingLeaveConfirm

    private val _leaveResultDialog = MutableStateFlow<Pair<Boolean, String>?>(null)
    val leaveResultDialog: StateFlow<Pair<Boolean, String>?> = _leaveResultDialog

    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab

    private val _makeupInfo = MutableStateFlow<MakeupPunchPageInfo?>(null)
    val makeupInfo: StateFlow<MakeupPunchPageInfo?> = _makeupInfo

    private val _isLoadingMakeupInfo = MutableStateFlow(false)
    val isLoadingMakeupInfo: StateFlow<Boolean> = _isLoadingMakeupInfo

    private val _isSubmittingMakeup = MutableStateFlow(false)
    val isSubmittingMakeup: StateFlow<Boolean> = _isSubmittingMakeup

    private val _makeupResultDialog = MutableStateFlow<Pair<Boolean, String>?>(null)
    val makeupResultDialog: StateFlow<Pair<Boolean, String>?> = _makeupResultDialog

    val savedAgents: StateFlow<List<SavedAgent>> =
        storage.savedAgents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        DailyResetWorker.scheduleDailyReset(getApplication())

        viewModelScope.launch {
            clearExpiredClockDataInternal()
            checkConnectedWearables()
            checkHrHealth()
            
            cookie.collect { ck ->
                if (ck.isNotBlank()) {
                    fetchCloudData()
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(MidnightAlarmHelper.millisUntilNextMidnight())
                clearExpiredClockDataInternal()
            }
        }
    }

    fun triggerMockLiveUpdateNotification(mockTimeStr: String) {
        viewModelScope.launch {
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val targetTime = java.time.LocalTime.parse(mockTimeStr.trim(), formatter)
                val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())

                var targetDateTime = now
                    .withHour(targetTime.hour)
                    .withMinute(targetTime.minute)
                    .withSecond(0)

                if (targetDateTime.isBefore(now) && targetDateTime.plusMinutes(1).isBefore(now)) {
                    targetDateTime = targetDateTime.plusDays(1)
                }

                val targetMillis = targetDateTime.toInstant().toEpochMilli()

                notificationHelper.scheduleReminders(
                    clockInTimeStr = "",
                    advanceMinutes = notifyAdvanceMinutes.value,
                    useLiveUpdates = useLiveUpdates.value,
                    mockOffWorkTimeStr = mockTimeStr
                )

                notificationHelper.postLiveUpdateNotification(
                    offWorkTimeMillis = targetMillis,
                    title = "下班時間倒數",
                    text = "再過不久就可以下班囉"
                )
                _message.value = "已發送測試 Live Update 通知 (模擬下班時間: $mockTimeStr)"
            } catch (e: Exception) {
                _error.value = "發送測試通知失敗: ${e.message}"
            }
        }
    }

    fun startHrHealthRetryLoop() {
        if (hrRetryJob?.isActive == true) return
        hrRetryJob = viewModelScope.launch {
            while (isActive) {
                val isNormal = repository.checkHrSystemHealth()
                _isHrSystemNormal.value = isNormal
                if (isNormal) {
                    _hrRetryCountdown.value = 3
                    break
                }

                for (seconds in 3 downTo 1) {
                    _hrRetryCountdown.value = seconds
                    delay(1000L)
                    if (!isActive) return@launch
                }
                _hrRetryCountdown.value = 0
            }
        }
    }

    fun stopHrHealthRetryLoop() {
        hrRetryJob?.cancel()
        hrRetryJob = null
    }

    fun checkHrHealth() {
        viewModelScope.launch {
            val isNormal = repository.checkHrSystemHealth()
            _isHrSystemNormal.value = isNormal
            if (!isNormal) {
                startHrHealthRetryLoop()
            }
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

    fun checkAndClearExpiredClockData() {
        viewModelScope.launch {
            clearExpiredClockDataInternal()
        }
    }

    private suspend fun clearExpiredClockDataInternal() {
        val today = LocalDate.now().toString()
        _currentDate.value = today
        val lastDate = storage.lastClockDate.first()

        if (lastDate != null && lastDate != today) {
            Log.d("MobileClockViewModel", "Detected expired clock data from date: $lastDate (today: $today). Clearing...")
            _cloudInTime.value = null
            _cloudOutTime.value = null
            _cloudDate.value = null
            storage.clearClockDataIfExpired()
        } else {
            storage.clearClockDataIfExpired()
            if (_cloudDate.value != null && _cloudDate.value != today) {
                _cloudInTime.value = null
                _cloudOutTime.value = null
                _cloudDate.value = null
            }
        }
    }

    fun fetchAttendanceSummary(
        startDate: LocalDate = _attendanceStartDate.value,
        endDate: LocalDate = _attendanceEndDate.value,
        skind: String = _attendanceSkind.value
    ) {
        viewModelScope.launch {
            _attendanceStartDate.value = startDate
            _attendanceEndDate.value = endDate
            _attendanceSkind.value = skind
            _isLoadingAttendance.value = true
            _attendanceError.value = null

            repository.getAttendanceSummary(startDate, endDate, skind).onSuccess { list ->
                _attendanceRecords.value = list
            }.onFailure { e ->
                _attendanceError.value = e.message ?: "查詢出勤表失敗"
            }

            _isLoadingAttendance.value = false
        }
    }

    fun loadMakeupPunchInfo(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _isLoadingMakeupInfo.value = true
            repository.getMakeupPunchInfo(date).onSuccess { info ->
                _makeupInfo.value = info
            }.onFailure { e ->
                _error.value = e.message ?: "載入補打卡資訊失敗"
            }
            _isLoadingMakeupInfo.value = false
        }
    }

    fun submitMakeupPunch(
        cardio: String,
        date: LocalDate,
        timeHHmm: String,
        reasonCode: String,
        note: String
    ) {
        viewModelScope.launch {
            _isSubmittingMakeup.value = true
            val currentInfo = _makeupInfo.value
                ?: repository.getMakeupPunchInfo(date).getOrElse { e ->
                    _makeupResultDialog.value = false to (e.message ?: "載入補打卡資訊失敗")
                    _isSubmittingMakeup.value = false
                    return@launch
                }

            repository.submitMakeupPunch(
                info = currentInfo,
                cardio = cardio,
                date = date,
                timeHHmm = timeHHmm,
                reasonCode = reasonCode,
                note = note
            ).onSuccess { msg ->
                _makeupResultDialog.value = true to msg
                loadMakeupPunchInfo(date)
            }.onFailure { e ->
                _makeupResultDialog.value = false to (e.message ?: "補打卡送出失敗")
            }
            _isSubmittingMakeup.value = false
        }
    }

    fun clearMakeupResultDialog() {
        _makeupResultDialog.value = null
    }

    fun lookupAndSaveAgent(targetEmpId: String) {
        viewModelScope.launch {
            val trimmed = targetEmpId.trim().uppercase()
            if (trimmed.isBlank()) {
                storage.saveAgentsList(emptyList(), null)
                _message.value = "已清除職務代理人設定"
                return@launch
            }
            _isQueryingAgent.value = true
            _error.value = null
            repository.queryColleagueById(trimmed).onSuccess { info ->
                val newAgent = SavedAgent(info.empId, info.name, info.deptName)
                val currentList = savedAgents.value.toMutableList()
                val existingIdx = currentList.indexOfFirst { it.empId == newAgent.empId }
                if (existingIdx >= 0) {
                    currentList[existingIdx] = newAgent
                } else {
                    currentList.add(newAgent)
                }
                val currentDefaultId = agentEmpId.value.trim()
                val defaultAgent = if (currentDefaultId.isBlank() || currentList.size == 1) {
                    newAgent
                } else {
                    currentList.find { it.empId == currentDefaultId } ?: newAgent
                }
                storage.saveAgentsList(currentList, defaultAgent)
                val deptText = if (info.deptName.isNotBlank()) "（${info.deptName}）" else ""
                _message.value = "已新增職務代理人：${info.empId} ${info.name}$deptText"
            }.onFailure { e ->
                _error.value = e.message ?: "查詢職務代理人失敗"
            }
            _isQueryingAgent.value = false
        }
    }

    fun setDefaultAgent(agent: SavedAgent) {
        viewModelScope.launch {
            storage.saveAgentInfo(agent.empId, agent.name, agent.deptName)
            _message.value = "已將 ${agent.empId} ${agent.name} 設為預設代理人"
        }
    }

    fun removeAgent(empIdToRemove: String) {
        viewModelScope.launch {
            val remaining = savedAgents.value.filterNot { it.empId == empIdToRemove }
            val currentDefaultId = agentEmpId.value.trim()
            val nextDefault = if (currentDefaultId == empIdToRemove) {
                remaining.firstOrNull()
            } else {
                remaining.find { it.empId == currentDefaultId } ?: remaining.firstOrNull()
            }
            storage.saveAgentsList(remaining, nextDefault)
            _message.value = "已移除代理人 $empIdToRemove"
        }
    }

    fun loadLeavePageInfo() {
        viewModelScope.launch {
            _isLoadingLeaveInfo.value = true
            repository.getLeavePageInfo().onSuccess { info ->
                _leavePageInfo.value = info
            }.onFailure { e ->
                Log.d("MobileClockViewModel", "loadLeavePageInfo error: ${e.message}")
            }
            _isLoadingLeaveInfo.value = false
        }
    }

    fun prepareLeaveConfirmation(
        agentEmpId: String,
        agentDisplay: String,
        leaveCode: String,
        leaveLabel: String,
        startDate: LocalDate,
        endDate: LocalDate,
        startTimeHHmm: String,
        endTimeHHmm: String,
        specDate: LocalDate?,
        overdueReason: String,
        note: String
    ) {
        viewModelScope.launch {
            _isPreparingLeaveConfirm.value = true
            repository.prepareLeaveConfirmation(
                pageInfo = _leavePageInfo.value,
                agentEmpId = agentEmpId,
                agentDisplay = agentDisplay,
                leaveCode = leaveCode,
                leaveLabel = leaveLabel,
                startDate = startDate,
                endDate = endDate,
                startTimeHHmm = startTimeHHmm,
                endTimeHHmm = endTimeHHmm,
                specDate = specDate,
                overdueReason = overdueReason,
                note = note
            ).onSuccess { data ->
                _pendingLeaveConfirm.value = data
            }.onFailure { e ->
                _leaveResultDialog.value = Pair(false, e.message ?: "請假單檢核失敗")
            }
            _isPreparingLeaveConfirm.value = false
        }
    }

    fun confirmLeaveApplication(attachments: List<LeaveAttachment>) {
        val confirmData = _pendingLeaveConfirm.value ?: return
        viewModelScope.launch {
            _isSubmittingLeave.value = true
            repository.confirmLeaveApplication(confirmData, attachments).onSuccess { msg ->
                _pendingLeaveConfirm.value = null
                _leaveResultDialog.value = Pair(true, msg)
                loadLeavePageInfo()
            }.onFailure { e ->
                _leaveResultDialog.value = Pair(false, e.message ?: "送出請假單失敗")
            }
            _isSubmittingLeave.value = false
        }
    }

    fun cancelLeaveConfirmation() {
        _pendingLeaveConfirm.value = null
    }

    fun clearLeaveResultDialog() {
        _leaveResultDialog.value = null
    }

    fun requestTab(tabName: String) {
        _requestedTab.value = tabName
    }

    fun clearRequestedTab() {
        _requestedTab.value = null
    }

    fun fetchCloudData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            checkHrHealth()
            clearExpiredClockDataInternal()
            val ck = storage.cookie.first().trim()
            val id = storage.empId.first().trim()
            val pass = storage.password.first().trim()
            if (ck.isNotBlank() || (id.isNotBlank() && pass.isNotBlank())) {
                repository.getCloudClockInfo(ck).onSuccess { (cloudIn, cloudOut, _) ->
                    _cloudInTime.value = cloudIn
                    _cloudOutTime.value = cloudOut
                    _cloudDate.value = LocalDate.now().toString()

                    cloudIn?.let { storage.saveClockIn(it) }
                    cloudOut?.let { storage.saveClockOut(it) }

                    if (enableWatchSync.value) {
                        val todayStr = java.time.LocalDate.now().toString()
                        WearableSyncManager.syncClockStatus(getApplication(), cloudIn, cloudOut, todayStr)
                    }
                }.onFailure { e ->
                    Log.d("MobileClockViewModel", "Cloud clock info sync result: ${e.message}")
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
        lunchDndEnabledVal: Boolean = false,
        lunchDndStartTimeVal: String = "12:00",
        lunchDndEndTimeVal: String = "13:00",
        mockOffWorkTimeVal: String = "17:30"
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
                lunchDndEnabledVal,
                lunchDndStartTimeVal,
                lunchDndEndTimeVal
            )

            if (notifyBeforeVal || notifyOnVal) {
                notificationHelper.scheduleReminders(
                    clockInTimeStr = clockInTime.value ?: "",
                    advanceMinutes = advanceMinutesVal,
                    useLiveUpdates = useLiveUpdatesVal,
                    mockOffWorkTimeStr = mockOffWorkTimeVal
                )
            }

            if (lunchDndEnabledVal) {
                notificationHelper.scheduleLunchDndReminders(lunchDndStartTimeVal, lunchDndEndTimeVal)
            }

            if (forgetClockOutEnabledVal) {
                notificationHelper.scheduleForgetClockOutReminders(forgetClockOutTimeVal)
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

    fun fetchCredentialsByLogin(empIdVal: String? = null, passVal: String? = null) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _error.value = null
            val id = (empIdVal ?: storage.empId.first()).trim()
            val pass = (passVal ?: storage.password.first()).trim()
            val result = repository.loginAndFetchCredentials(id, pass)
            if (result.isSuccess) {
                val (newCookie, newEnc) = result.getOrThrow()
                if (enableWatchSync.value) {
                    WearableSyncManager.syncSettings(
                        context = getApplication(),
                        empId = id,
                        pass = pass,
                        cookie = newCookie,
                        enc = newEnc,
                        gps = storage.gpsLocation.first(),
                        notifyBefore = storage.notifyBeforeOffWork.first(),
                        notifyOn = storage.notifyOnOffWork.first()
                    )
                }
                _message.value = "已自動登入並取得 Cookie 與 enc！"
                fetchCloudData()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "自動登入失敗"
            }
            _isLoggingIn.value = false
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

            if (id.isBlank() || pass.isBlank()) {
                _error.value = "打卡失敗：請先至 [系統設定] 填寫工號與密碼"
                _isClockingIn.value = false
                return@launch
            }

            val currentIn = clockInTime.value
            val isClockingInAction = currentIn.isNullOrBlank()

            val result = repository.clockIn(id, pass, ck, en, gps)
            if (result.isSuccess) {
                VibrationUtil.vibrateShortSuccess(getApplication())
                val time = result.getOrNull() ?: ""
                if (isClockingInAction) {
                    storage.saveClockIn(time)
                    notificationHelper.scheduleReminders(time, notifyAdvanceMinutes.value, useLiveUpdates.value, mockOffWorkTime.value)
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
                VibrationUtil.vibrateLongFailure(getApplication())
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

    fun isNotificationPolicyAccessGranted(): Boolean {
        return notificationHelper.isNotificationPolicyAccessGranted()
    }

    fun openNotificationPolicySettings() {
        notificationHelper.openNotificationPolicySettings()
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
