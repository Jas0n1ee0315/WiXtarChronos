package com.jas0n1ee.wixtar.chronoswear.presentation

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.VibrationUtil
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronoswear.service.WearableDataListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PunchButtonStatus {
    NORMAL,
    LOADING,
    SUCCESS,
    FAILURE,
    NO_NETWORK,
    HR_ERROR
}

class WearClockViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val repository = ClockRepository(storage)

    val clockInTime: StateFlow<String?> = storage.clockInTime.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val clockOutTime: StateFlow<String?> = storage.clockOutTime.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val clockInSource: StateFlow<String> = storage.clockInSource.stateIn(viewModelScope, SharingStarted.Eagerly, "本地離線")
    val clockOutSource: StateFlow<String> = storage.clockOutSource.stateIn(viewModelScope, SharingStarted.Eagerly, "本地離線")

    private val _isHrSystemNormal = MutableStateFlow<Boolean?>(null)
    val isHrSystemNormal: StateFlow<Boolean?> = _isHrSystemNormal

    private val _buttonStatus = MutableStateFlow(PunchButtonStatus.NORMAL)
    val buttonStatus: StateFlow<PunchButtonStatus> = _buttonStatus

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private var hrCheckJob: Job? = null
    private var buttonResetJob: Job? = null

    fun onAppResume() {
        viewModelScope.launch {
            storage.clearClockDataIfExpired()
            fetchLatestClockData()
            startHrHealthCheckLoop()
        }
    }

    fun onAppPause() {
        stopHrHealthCheckLoop()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun checkPhoneConnected(): Boolean {
        return try {
            val nodeClient = Wearable.getNodeClient(getApplication<Application>())
            val nodes = Tasks.await(nodeClient.connectedNodes)
            nodes.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchLatestClockData() {
        val hasPhone = checkPhoneConnected()
        val hasNet = isNetworkAvailable()

        if (hasPhone) {
            WearableSyncManager.sendActionToNodes(getApplication(), "FETCH_CLOUD")
        } else if (hasNet) {
            val cookie = storage.cookie.first()
            if (cookie.isNotBlank()) {
                repository.getCloudClockInfo(cookie).onSuccess { (cloudIn, cloudOut, _) ->
                    if (cloudIn != null || cloudOut != null) {
                        storage.saveClockDataWithSources(
                            cloudIn ?: "", "雲端",
                            cloudOut ?: "", "雲端"
                        )
                    }
                }
            }
        }
    }

    fun startHrHealthCheckLoop() {
        if (hrCheckJob?.isActive == true) return
        hrCheckJob = viewModelScope.launch {
            while (isActive) {
                val isNormal = checkHrHealthOnce()
                _isHrSystemNormal.value = isNormal
                if (isNormal) {
                    if (_buttonStatus.value == PunchButtonStatus.HR_ERROR) {
                        _buttonStatus.value = PunchButtonStatus.NORMAL
                    }
                    break
                } else {
                    _buttonStatus.value = PunchButtonStatus.HR_ERROR
                    delay(3000)
                }
            }
        }
    }

    fun stopHrHealthCheckLoop() {
        hrCheckJob?.cancel()
        hrCheckJob = null
    }

    private suspend fun checkHrHealthOnce(): Boolean {
        val hasNet = isNetworkAvailable()
        val hasPhone = checkPhoneConnected()

        return if (hasNet) {
            repository.checkHrSystemHealth()
        } else if (hasPhone) {
            var phoneResult = false
            try {
                val nodeClient = Wearable.getNodeClient(getApplication<Application>())
                val nodes = Tasks.await(nodeClient.connectedNodes)
                if (nodes.isNotEmpty()) {
                    val messageClient = Wearable.getMessageClient(getApplication<Application>())
                    for (node in nodes) {
                        Tasks.await(messageClient.sendMessage(node.id, "/request_check_hr_health", ByteArray(0)))
                    }
                    val resp = withTimeoutOrNull(2500) {
                        WearableDataListenerService.messageEvents.first { (path, _) -> path == "/response_hr_health" }
                    }
                    if (resp?.second == "200") {
                        phoneResult = true
                    }
                }
            } catch (e: Exception) {
                Log.e("WearClockViewModel", "Failed to check HR health via phone", e)
            }
            phoneResult
        } else {
            false
        }
    }

    fun doPunchAction() {
        if (_buttonStatus.value == PunchButtonStatus.SUCCESS || _buttonStatus.value == PunchButtonStatus.LOADING) {
            return
        }

        viewModelScope.launch {
            _buttonStatus.value = PunchButtonStatus.LOADING
            val hasNet = isNetworkAvailable()
            val hasPhone = checkPhoneConnected()

            if (!hasNet && !hasPhone) {
                showTemporaryStatus(PunchButtonStatus.NO_NETWORK, 5000)
                return@launch
            }

            val empId = storage.empId.first()
            val pass = storage.password.first()
            val cookie = storage.cookie.first()
            val enc = storage.enc.first()
            val gps = storage.gpsLocation.first()

            val currentIn = clockInTime.value
            val isClockingInAction = currentIn.isNullOrBlank()

            if (hasNet && empId.isNotBlank() && pass.isNotBlank()) {
                val result = repository.clockIn(empId, pass, cookie, enc, gps)
                if (result.isSuccess) {
                    val time = result.getOrNull() ?: ""
                    val source = "雲端"
                    if (isClockingInAction) {
                        storage.saveClockInWithSource(time, source)
                    } else {
                        storage.saveClockOutWithSource(time, source)
                    }
                    showTemporaryStatus(PunchButtonStatus.SUCCESS, 10000)
                } else {
                    showTemporaryStatus(PunchButtonStatus.FAILURE, 3000)
                }
            } else if (hasPhone) {
                var punchSuccess = false
                var punchTime = ""
                try {
                    val action = if (isClockingInAction) "CLOCK_IN" else "CLOCK_OUT"
                    WearableSyncManager.sendActionToNodes(getApplication(), action)

                    val resp = withTimeoutOrNull(8000) {
                        WearableDataListenerService.messageEvents.first { (path, _) -> path == "/response_punch" }.second
                    }

                    if (resp?.startsWith("SUCCESS") == true) {
                        punchSuccess = true
                        punchTime = resp.substringAfter("SUCCESS:").ifBlank { "已打卡" }
                    }
                } catch (e: Exception) {
                    Log.e("WearClockViewModel", "Failed to punch via phone", e)
                }

                if (punchSuccess) {
                    val source = "來自手機"
                    if (isClockingInAction) {
                        storage.saveClockInWithSource(punchTime, source)
                    } else {
                        storage.saveClockOutWithSource(punchTime, source)
                    }
                    showTemporaryStatus(PunchButtonStatus.SUCCESS, 10000)
                } else {
                    showTemporaryStatus(PunchButtonStatus.FAILURE, 3000)
                }
            } else {
                showTemporaryStatus(PunchButtonStatus.NO_NETWORK, 5000)
            }
        }
    }

    private fun showTemporaryStatus(status: PunchButtonStatus, durationMs: Long) {
        buttonResetJob?.cancel()
        _buttonStatus.value = status

        if (status == PunchButtonStatus.SUCCESS) {
            VibrationUtil.vibrateShortSuccess(getApplication())
        } else if (status == PunchButtonStatus.FAILURE || status == PunchButtonStatus.NO_NETWORK || status == PunchButtonStatus.HR_ERROR) {
            VibrationUtil.vibrateLongFailure(getApplication())
        }

        buttonResetJob = viewModelScope.launch {
            delay(durationMs)
            if (_buttonStatus.value == status) {
                _buttonStatus.value = if (_isHrSystemNormal.value == false) PunchButtonStatus.HR_ERROR else PunchButtonStatus.NORMAL
            }
        }
    }
}
