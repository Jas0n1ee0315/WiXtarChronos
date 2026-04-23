package com.wixtar.watchtimeclock.presentation

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wixtar.watchtimeclock.data.AppStorage
import com.wixtar.watchtimeclock.data.ClockRepository
import com.wixtar.watchtimeclock.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

    val clockInTime = storage.clockInTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val clockOutTime = storage.clockOutTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionStatus = MutableStateFlow<ActionStatus?>(null)
    val actionStatus: StateFlow<ActionStatus?> = _actionStatus

    enum class ActionStatus {
        SUCCESS_IN, SUCCESS_OUT, SUCCESS_OVERTIME,
        FAILURE_IN, FAILURE_OUT, FAILURE_OVERTIME
    }

    init {
        viewModelScope.launch {
            // Simulate loading animation
            kotlinx.coroutines.delay(1000)
            _isLoading.value = false
        }
    }

    fun saveSettings(empId: String, pass: String, cookie: String, enc: String, gps: String, notifyBefore: Boolean, notifyOn: Boolean) {
        viewModelScope.launch {
            storage.saveSettings(empId, pass, cookie, enc, gps, notifyBefore, notifyOn)
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

            val isIn = clockInTime.value.isNullOrEmpty()
            val isOut = !isIn && clockOutTime.value.isNullOrEmpty()
            
            val result = repository.clockIn(id, pass, ck, en, gps)
            if (result.isSuccess) {
                val time = result.getOrNull() ?: ""
                vibrateSuccess()
                if (isIn) {
                    storage.saveClockIn(time)
                    notificationHelper.scheduleReminders(time)
                    _actionStatus.value = ActionStatus.SUCCESS_IN
                } else if (isOut) {
                    storage.saveClockOut(time)
                    _actionStatus.value = ActionStatus.SUCCESS_OUT
                } else {
                    _actionStatus.value = ActionStatus.SUCCESS_OVERTIME
                }
                
                launch {
                    kotlinx.coroutines.delay(10000)
                    if (_actionStatus.value?.name?.startsWith("SUCCESS") == true) {
                        _actionStatus.value = null
                    }
                }
            } else {
                vibrateFailure()
                _error.value = result.exceptionOrNull()?.message ?: "打卡失敗"
                _actionStatus.value = when {
                    isIn -> ActionStatus.FAILURE_IN
                    isOut -> ActionStatus.FAILURE_OUT
                    else -> ActionStatus.FAILURE_OVERTIME
                }
                
                launch {
                    kotlinx.coroutines.delay(3000)
                    if (_actionStatus.value?.name?.startsWith("FAILURE") == true) {
                        _actionStatus.value = null
                        _error.value = null
                    }
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
