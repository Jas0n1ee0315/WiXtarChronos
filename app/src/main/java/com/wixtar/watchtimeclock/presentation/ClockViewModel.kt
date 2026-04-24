package com.wixtar.watchtimeclock.presentation

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wixtar.watchtimeclock.data.AppStorage
import com.wixtar.watchtimeclock.data.ClockRepository
import com.wixtar.watchtimeclock.notification.NotificationHelper
import com.wixtar.watchtimeclock.tile.MainTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val repository = ClockRepository(storage)
    private val notificationHelper = NotificationHelper(application)
    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val empId = storage.empId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val password = storage.password.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val cookie = storage.cookie.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val enc = storage.enc.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    val gpsLocation = storage.gpsLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "22.987929,120.197210")
    val notifyBeforeOffWork = storage.notifyBeforeOffWork.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val notifyOnOffWork = storage.notifyOnOffWork.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    private val _cloudInTime = MutableStateFlow<String?>(null)
    private val _cloudOutTime = MutableStateFlow<String?>(null)

    val clockInTime: StateFlow<String?> = kotlinx.coroutines.flow.combine(
        _cloudInTime,
        storage.clockInTime
    ) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val clockOutTime: StateFlow<String?> = kotlinx.coroutines.flow.combine(
        _cloudOutTime,
        storage.clockOutTime
    ) { cloud, local ->
        when {
            !cloud.isNullOrBlank() -> "$cloud (雲端)"
            !local.isNullOrBlank() -> "$local (本地)"
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionStatus = MutableStateFlow<ActionStatus?>(null)
    val actionStatus: StateFlow<ActionStatus?> = _actionStatus

    enum class ActionStatus {
        SUCCESS_IN, SUCCESS_OUT, SUCCESS_OVERTIME, FAILURE_IN, FAILURE_OUT, FAILURE_OVERTIME
    }

    init {
        viewModelScope.launch {
            storage.clearClockDataIfExpired()
            
            // 監聽 cookie 變化，直到有值才執行第一次抓取
            launch {
                cookie.collect { ck ->
                    if (ck.isNotBlank()) {
                        Log.d("ClockViewModel", "Cookie loaded, fetching cloud data...")
                        fetchCloudData()
                    } else {
                        Log.d("ClockViewModel", "Cookie is blank, skipping fetch")
                    }
                }
            }
            
            _isLoading.value = false
        }
    }

    fun fetchCloudData() {
        viewModelScope.launch {
            val ck = cookie.value
            if (ck.isNotBlank()) {
                Log.d("ClockViewModel", "Starting getCloudClockInfo...")
                repository.getCloudClockInfo(ck).onSuccess { (cloudIn, cloudOut, _) ->
                    Log.d("ClockViewModel", "Fetch success: In=$cloudIn, Out=$cloudOut")
                    _cloudInTime.value = cloudIn
                    _cloudOutTime.value = cloudOut
                    
                    // 同步到 storage 讓 Tile 也能看到
                    cloudIn?.let { storage.saveClockIn(it) }
                    cloudOut?.let { storage.saveClockOut(it) }
                    
                    // 通知 Tile 更新
                    MainTileService.forceUpdate(getApplication())
                }.onFailure {
                    Log.e("ClockViewModel", "Fetch failed", it)
                    _error.value = "同步雲端資料失敗: ${it.message}"
                }
            } else {
                Log.w("ClockViewModel", "Cannot fetch cloud data: Cookie is blank")
            }
        }
    }

    fun dismissLoading() {
        _isLoading.value = false
    }

    fun saveSettings(empId: String, pass: String, cookie: String, enc: String, gps: String, notifyBefore: Boolean, notifyOn: Boolean) {
        viewModelScope.launch {
            storage.saveSettings(empId, pass, cookie, enc, gps, notifyBefore, notifyOn)
            fetchCloudData()
        }
    }

    fun doClockAction() {
        viewModelScope.launch {
            _error.value = null
            val id = empId.value
            val pass = password.value
            val ck = cookie.value
            val en = enc.value
            val gps = gpsLocation.value

            val currentIn = clockInTime.value
            val currentOut = clockOutTime.value
            
            val actionType = when {
                currentIn.isNullOrBlank() -> "IN"
                currentOut.isNullOrBlank() -> "OUT"
                else -> "OVERTIME"
            }
            
            val result = repository.clockIn(id, pass, ck, en, gps)
            if (result.isSuccess) {
                val time = result.getOrNull() ?: ""
                vibrateSuccess()
                when (actionType) {
                    "IN" -> {
                        storage.saveClockIn(time)
                        notificationHelper.scheduleReminders(time)
                        _actionStatus.value = ActionStatus.SUCCESS_IN
                    }
                    "OUT" -> {
                        storage.saveClockOut(time)
                        _actionStatus.value = ActionStatus.SUCCESS_OUT
                    }
                    else -> {
                        _actionStatus.value = ActionStatus.SUCCESS_OVERTIME
                    }
                }
                
                // 打卡成功後也要強制更新 Tile
                MainTileService.forceUpdate(getApplication())
                
                fetchCloudData()

                launch {
                    kotlinx.coroutines.delay(10000)
                    _actionStatus.value = null
                }
            } else {
                vibrateFailure()
                _error.value = result.exceptionOrNull()?.message ?: "打卡失敗"
                _actionStatus.value = when (actionType) {
                    "IN" -> ActionStatus.FAILURE_IN
                    "OUT" -> ActionStatus.FAILURE_OUT
                    else -> ActionStatus.FAILURE_OVERTIME
                }
                
                launch {
                    kotlinx.coroutines.delay(3000)
                    _actionStatus.value = null
                    _error.value = null
                }
            }
        }
    }

    private fun vibrateSuccess() {
        val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun vibrateFailure() {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    fun clearError() {
        _error.value = null
    }
}
