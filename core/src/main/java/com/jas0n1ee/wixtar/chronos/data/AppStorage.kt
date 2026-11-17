package com.jas0n1ee.wixtar.chronos.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService as QsTileService
import androidx.wear.tiles.TileService as WearTileService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppStorage(private val context: Context) {
    companion object {
        val EMP_ID = stringPreferencesKey("emp_id")
        /** 舊版明文 HR 密碼：只用於遷移，App 啟動時會加密搬到 PASSWORD_ENC 並刪除 */
        val PASSWORD = stringPreferencesKey("password")
        /** 以 SecureStringCipher（Android Keystore）加密後的 HR 密碼 */
        val PASSWORD_ENC = stringPreferencesKey("password_enc")
        val COOKIE = stringPreferencesKey("cookie")
        val ENC = stringPreferencesKey("enc")
        val GPS_LOCATION = stringPreferencesKey("gps_location")
        
        val CLOCK_IN_TIME = stringPreferencesKey("clock_in_time")
        val CLOCK_OUT_TIME = stringPreferencesKey("clock_out_time")
        val CLOCK_IN_SOURCE = stringPreferencesKey("clock_in_source")
        val CLOCK_OUT_SOURCE = stringPreferencesKey("clock_out_source")
        val LAST_CLOCK_DATE = stringPreferencesKey("last_clock_date")

        val NOTIFY_BEFORE_OFF_WORK = booleanPreferencesKey("notify_before_off_work")
        val NOTIFY_ON_OFF_WORK = booleanPreferencesKey("notify_on_off_work")
        val NOTIFY_ADVANCE_MINUTES = intPreferencesKey("notify_advance_minutes")
        val USE_LIVE_UPDATES = booleanPreferencesKey("use_live_updates")
        val ENABLE_WATCH_SYNC = booleanPreferencesKey("enable_watch_sync")

        val FORGET_CLOCK_OUT_NOTIFY_ENABLED = booleanPreferencesKey("forget_clock_out_notify_enabled")
        val FORGET_CLOCK_OUT_NOTIFY_TIME = stringPreferencesKey("forget_clock_out_notify_time")

        val LUNCH_DND_ENABLED = booleanPreferencesKey("lunch_dnd_enabled")
        val LUNCH_DND_START_TIME = stringPreferencesKey("lunch_dnd_start_time")
        val LUNCH_DND_END_TIME = stringPreferencesKey("lunch_dnd_end_time")

        val MOCK_OFF_WORK_TIME = stringPreferencesKey("mock_off_work_time")
        val SELECTED_APP_ICON_INDEX = intPreferencesKey("selected_app_icon_index")

        val TRACKER_ICON_MODE = stringPreferencesKey("tracker_icon_mode")
        val TRACKER_CUSTOM_EMOJI = stringPreferencesKey("tracker_custom_emoji")
        val TRACKER_FLIP_HORIZONTAL = booleanPreferencesKey("tracker_flip_horizontal")

        val AUTO_CLOCK_IN_ENABLED = booleanPreferencesKey("auto_clock_in_enabled")
        val WORK_START_TIME = stringPreferencesKey("work_start_time")
        val FLEXIBLE_CLOCK_IN_ENABLED = booleanPreferencesKey("flexible_clock_in_enabled")
        val FLEXIBLE_START_TIME = stringPreferencesKey("flexible_start_time")
        val FLEXIBLE_END_TIME = stringPreferencesKey("flexible_end_time")
        val AUTO_CLOCK_OUT_ENABLED = booleanPreferencesKey("auto_clock_out_enabled")
        val SCHEDULED_AUTO_CLOCK_IN_TIME = stringPreferencesKey("scheduled_auto_clock_in_time")
        val SCHEDULED_AUTO_CLOCK_IN_DATE = stringPreferencesKey("scheduled_auto_clock_in_date")
        val SCHEDULED_AUTO_CLOCK_IN_MILLIS = longPreferencesKey("scheduled_auto_clock_in_millis")

        val AGENT_EMP_ID = stringPreferencesKey("agent_emp_id")
        val AGENT_NAME = stringPreferencesKey("agent_name")
        val AGENT_DEPT = stringPreferencesKey("agent_dept")
        val AGENTS_JSON = stringPreferencesKey("agents_json")

        // PJM（pjm.wixtar.com 專案管理系統）請假工時同步
        val PJM_SYNC_ENABLED = booleanPreferencesKey("pjm_sync_enabled")
        val PJM_USERNAME = stringPreferencesKey("pjm_username")
        /** 以 SecureStringCipher（Android Keystore）加密後的 PJM 密碼，不存明文 */
        val PJM_PASSWORD_ENC = stringPreferencesKey("pjm_password_enc")
    }

    val empId: Flow<String> = context.dataStore.data.map { it[EMP_ID] ?: "" }
    /**
     * HR 密碼（明文，僅存在記憶體）。
     * 讀取介面與舊版相同，所有呼叫端不用修改；實際儲存的是加密值，這裡自動解密。
     * 尚未遷移的舊資料則退回讀取明文欄位，確保升級過程不會中斷自動登入。
     */
    val password: Flow<String> = context.dataStore.data
        .map { prefs -> readHrPassword(prefs) }
        .distinctUntilChanged()
    val cookie: Flow<String> = context.dataStore.data.map { it[COOKIE] ?: "" }
    val enc: Flow<String> = context.dataStore.data.map { it[ENC] ?: "" }
    val gpsLocation: Flow<String> = context.dataStore.data.map { it[GPS_LOCATION] ?: "22.987929,120.197210" }

    val notifyBeforeOffWork: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_BEFORE_OFF_WORK] ?: false }
    val notifyOnOffWork: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_ON_OFF_WORK] ?: false }
    val notifyAdvanceMinutes: Flow<Int> = context.dataStore.data.map { it[NOTIFY_ADVANCE_MINUTES] ?: 10 }
    val useLiveUpdates: Flow<Boolean> = context.dataStore.data.map { it[USE_LIVE_UPDATES] ?: false }
    val enableWatchSync: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_WATCH_SYNC] ?: false }

    val forgetClockOutNotifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[FORGET_CLOCK_OUT_NOTIFY_ENABLED] ?: false }
    val forgetClockOutNotifyTime: Flow<String> = context.dataStore.data.map { it[FORGET_CLOCK_OUT_NOTIFY_TIME] ?: "21:00" }

    val lunchDndEnabled: Flow<Boolean> = context.dataStore.data.map { it[LUNCH_DND_ENABLED] ?: false }
    val lunchDndStartTime: Flow<String> = context.dataStore.data.map { it[LUNCH_DND_START_TIME] ?: "12:00" }
    val lunchDndEndTime: Flow<String> = context.dataStore.data.map { it[LUNCH_DND_END_TIME] ?: "13:00" }

    val mockOffWorkTime: Flow<String> = context.dataStore.data.map { it[MOCK_OFF_WORK_TIME] ?: "17:30" }
    val selectedAppIconIndex: Flow<Int> = context.dataStore.data.map { it[SELECTED_APP_ICON_INDEX] ?: 0 }

    val trackerIconMode: Flow<String> = context.dataStore.data.map { it[TRACKER_ICON_MODE] ?: "sprint" }
    val trackerCustomEmoji: Flow<String> = context.dataStore.data.map { it[TRACKER_CUSTOM_EMOJI] ?: "🚀" }
    val trackerFlipHorizontal: Flow<Boolean> = context.dataStore.data.map { it[TRACKER_FLIP_HORIZONTAL] ?: true }

    val autoClockInEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CLOCK_IN_ENABLED] ?: false }
    val workStartTime: Flow<String> = context.dataStore.data.map { it[WORK_START_TIME] ?: "09:00" }
    val flexibleClockInEnabled: Flow<Boolean> = context.dataStore.data.map { it[FLEXIBLE_CLOCK_IN_ENABLED] ?: false }
    val flexibleStartTime: Flow<String> = context.dataStore.data.map { it[FLEXIBLE_START_TIME] ?: "09:00" }
    val flexibleEndTime: Flow<String> = context.dataStore.data.map { it[FLEXIBLE_END_TIME] ?: "09:30" }
    val autoClockOutEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CLOCK_OUT_ENABLED] ?: false }
    val scheduledAutoClockInTime: Flow<String?> = context.dataStore.data.map { it[SCHEDULED_AUTO_CLOCK_IN_TIME] }
    val scheduledAutoClockInDate: Flow<String?> = context.dataStore.data.map { it[SCHEDULED_AUTO_CLOCK_IN_DATE] }
    val scheduledAutoClockInEpochMillis: Flow<Long?> = context.dataStore.data.map { it[SCHEDULED_AUTO_CLOCK_IN_MILLIS] }

    val agentEmpId: Flow<String> = context.dataStore.data.map { it[AGENT_EMP_ID] ?: "" }
    val agentName: Flow<String> = context.dataStore.data.map { it[AGENT_NAME] ?: "" }
    val agentDept: Flow<String> = context.dataStore.data.map { it[AGENT_DEPT] ?: "" }
    val savedAgents: Flow<List<SavedAgent>> = context.dataStore.data.map { prefs ->
        val raw = prefs[AGENTS_JSON].orEmpty()
        if (raw.isNotBlank()) {
            try {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val id = obj.optString("empId").trim()
                    if (id.isBlank()) null
                    else SavedAgent(
                        empId = id,
                        name = obj.optString("name").trim(),
                        deptName = obj.optString("deptName").trim()
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            val legacyId = prefs[AGENT_EMP_ID]?.trim().orEmpty()
            if (legacyId.isNotBlank()) {
                listOf(
                    SavedAgent(
                        empId = legacyId,
                        name = prefs[AGENT_NAME]?.trim().orEmpty(),
                        deptName = prefs[AGENT_DEPT]?.trim().orEmpty()
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    val pjmSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[PJM_SYNC_ENABLED] ?: false }
    val pjmUsername: Flow<String> = context.dataStore.data.map { it[PJM_USERNAME] ?: "" }
    /** 只公開「是否已設定密碼」，明文密碼僅在送出前由 getPjmPassword() 解密取得 */
    val hasPjmPassword: Flow<Boolean> = context.dataStore.data.map { !it[PJM_PASSWORD_ENC].isNullOrEmpty() }

    val lastClockDate: Flow<String?> = context.dataStore.data.map { it[LAST_CLOCK_DATE] }

    val clockInTime: Flow<String?> = context.dataStore.data.map { prefs ->
        val date = prefs[LAST_CLOCK_DATE]
        val today = LocalDate.now().toString()
        if (date == today) prefs[CLOCK_IN_TIME] else null 
    }
    val clockOutTime: Flow<String?> = context.dataStore.data.map { prefs ->
        val date = prefs[LAST_CLOCK_DATE]
        val today = LocalDate.now().toString()
        if (date == today) prefs[CLOCK_OUT_TIME] else null
    }

    val clockInSource: Flow<String> = context.dataStore.data.map { prefs ->
        val date = prefs[LAST_CLOCK_DATE]
        val today = LocalDate.now().toString()
        if (date == today) (prefs[CLOCK_IN_SOURCE] ?: "本地離線") else "本地離線"
    }
    val clockOutSource: Flow<String> = context.dataStore.data.map { prefs ->
        val date = prefs[LAST_CLOCK_DATE]
        val today = LocalDate.now().toString()
        if (date == today) (prefs[CLOCK_OUT_SOURCE] ?: "本地離線") else "本地離線"
    }

    suspend fun saveSettings(
        empId: String,
        pass: String,
        cookie: String,
        enc: String,
        gps: String,
        notifyBefore: Boolean,
        notifyOn: Boolean,
        advanceMinutes: Int = 10,
        useLiveUpdates: Boolean = false,
        forgetClockOutEnabled: Boolean = false,
        forgetClockOutTime: String = "21:00",
        lunchDndEnabled: Boolean = false,
        lunchDndStartTime: String = "12:00",
        lunchDndEndTime: String = "13:00",
        mockOffWorkTime: String = "17:30"
    ) {
        // 加密在 edit 區塊外進行；Keystore 異常（極少數裝置）時退回明文，避免使用者無法打卡
        val encrypted = encryptOrNull(pass)
        context.dataStore.edit { prefs ->
            prefs[EMP_ID] = empId
            if (encrypted != null) {
                if (encrypted.isEmpty()) prefs.remove(PASSWORD_ENC) else prefs[PASSWORD_ENC] = encrypted
                prefs.remove(PASSWORD) // 不再保留明文
            } else {
                prefs.remove(PASSWORD_ENC)
                prefs[PASSWORD] = pass
            }
            prefs[COOKIE] = cookie
            prefs[ENC] = enc
            prefs[GPS_LOCATION] = gps
            prefs[NOTIFY_BEFORE_OFF_WORK] = notifyBefore
            prefs[NOTIFY_ON_OFF_WORK] = notifyOn
            prefs[NOTIFY_ADVANCE_MINUTES] = advanceMinutes
            prefs[USE_LIVE_UPDATES] = useLiveUpdates
            prefs[FORGET_CLOCK_OUT_NOTIFY_ENABLED] = forgetClockOutEnabled
            prefs[FORGET_CLOCK_OUT_NOTIFY_TIME] = forgetClockOutTime
            prefs[LUNCH_DND_ENABLED] = lunchDndEnabled
            prefs[LUNCH_DND_START_TIME] = lunchDndStartTime
            prefs[LUNCH_DND_END_TIME] = lunchDndEndTime
            prefs[MOCK_OFF_WORK_TIME] = mockOffWorkTime
        }
    }

    suspend fun saveTrackerSettings(mode: String, customEmoji: String, flipHorizontal: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TRACKER_ICON_MODE] = mode
            prefs[TRACKER_CUSTOM_EMOJI] = customEmoji
            prefs[TRACKER_FLIP_HORIZONTAL] = flipHorizontal
        }
    }

    suspend fun saveAutoClockSettings(
        autoIn: Boolean,
        workStart: String,
        flexIn: Boolean,
        flexStart: String,
        flexEnd: String,
        autoOut: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_CLOCK_IN_ENABLED] = autoIn
            prefs[WORK_START_TIME] = workStart
            prefs[FLEXIBLE_CLOCK_IN_ENABLED] = flexIn
            prefs[FLEXIBLE_START_TIME] = flexStart
            prefs[FLEXIBLE_END_TIME] = flexEnd
            prefs[AUTO_CLOCK_OUT_ENABLED] = autoOut
        }
    }

    suspend fun saveScheduledAutoClockInTime(time: String?) {
        context.dataStore.edit { prefs ->
            if (time != null) {
                prefs[SCHEDULED_AUTO_CLOCK_IN_TIME] = time
            } else {
                prefs.remove(SCHEDULED_AUTO_CLOCK_IN_TIME)
            }
        }
    }

    suspend fun saveScheduledAutoClockIn(time: String?, date: String?, epochMillis: Long?) {
        context.dataStore.edit { prefs ->
            if (time != null && date != null && epochMillis != null) {
                prefs[SCHEDULED_AUTO_CLOCK_IN_TIME] = time
                prefs[SCHEDULED_AUTO_CLOCK_IN_DATE] = date
                prefs[SCHEDULED_AUTO_CLOCK_IN_MILLIS] = epochMillis
            } else {
                prefs.remove(SCHEDULED_AUTO_CLOCK_IN_TIME)
                prefs.remove(SCHEDULED_AUTO_CLOCK_IN_DATE)
                prefs.remove(SCHEDULED_AUTO_CLOCK_IN_MILLIS)
            }
        }
    }

    suspend fun saveCredentials(cookie: String, enc: String) {
        context.dataStore.edit { prefs ->
            prefs[COOKIE] = cookie
            prefs[ENC] = enc
        }
    }

    suspend fun saveAgentInfo(empId: String, name: String, dept: String) {
        context.dataStore.edit { prefs ->
            prefs[AGENT_EMP_ID] = empId
            prefs[AGENT_NAME] = name
            prefs[AGENT_DEPT] = dept
        }
    }

    suspend fun saveAgentsList(agents: List<SavedAgent>, defaultAgent: SavedAgent?) {
        val arr = org.json.JSONArray()
        agents.forEach { agent ->
            arr.put(
                org.json.JSONObject().apply {
                    put("empId", agent.empId)
                    put("name", agent.name)
                    put("deptName", agent.deptName)
                }
            )
        }
        context.dataStore.edit { prefs ->
            prefs[AGENTS_JSON] = arr.toString()
            prefs[AGENT_EMP_ID] = defaultAgent?.empId ?: ""
            prefs[AGENT_NAME] = defaultAgent?.name ?: ""
            prefs[AGENT_DEPT] = defaultAgent?.deptName ?: ""
        }
    }

    /**
     * 把舊版明文 HR 密碼加密搬到 PASSWORD_ENC，並刪除明文。
     * 由手機與手錶的 Application.onCreate 呼叫；重複呼叫沒有副作用。
     * @return true = 這次有搬移
     */
    suspend fun migrateLegacyPassword(): Boolean {
        val prefs = context.dataStore.data.first()
        val legacy = prefs[PASSWORD]
        if (legacy.isNullOrEmpty()) return false
        val encrypted = encryptOrNull(legacy) ?: return false // Keystore 不可用：維持明文，下次再試
        var migrated = false
        context.dataStore.edit { p ->
            // 再確認一次：避免與同時進行的 saveSettings 互相覆蓋
            if (p[PASSWORD] == legacy) {
                if (p[PASSWORD_ENC].isNullOrEmpty()) p[PASSWORD_ENC] = encrypted
                p.remove(PASSWORD)
                migrated = true
            }
        }
        return migrated
    }

    /** 讀取 HR 密碼：優先解密 PASSWORD_ENC；解密失敗（金鑰失效）回傳 "" 讓使用者重新輸入 */
    private fun readHrPassword(prefs: Preferences): String {
        val encoded = prefs[PASSWORD_ENC]
        if (!encoded.isNullOrEmpty()) return decryptCached(encoded) ?: ""
        return prefs[PASSWORD] ?: ""
    }

    /** DataStore 每次任何設定變更都會重新發送，快取最近一次解密結果避免重複做 Keystore 運算 */
    @Volatile private var lastEncoded: String? = null
    @Volatile private var lastDecoded: String? = null

    private fun decryptCached(encoded: String): String? {
        if (encoded == lastEncoded) return lastDecoded
        val plain = SecureStringCipher.decrypt(encoded)
        lastEncoded = encoded
        lastDecoded = plain
        return plain
    }

    /** 加密；Keystore 例外時回傳 null（由呼叫端決定退回方式） */
    private fun encryptOrNull(plain: String): String? = try {
        SecureStringCipher.encrypt(plain)
    } catch (e: Exception) {
        android.util.Log.w("AppStorage", "Keystore 加密失敗，改用明文儲存：${e.javaClass.simpleName}")
        null
    }

    /**
     * 儲存 PJM 同步設定。
     * @param password null = 不變更已儲存的密碼；"" = 清除密碼；其他 = 加密後儲存
     */
    suspend fun savePjmSettings(enabled: Boolean, username: String, password: String?) {
        // 加密在 edit 區塊外進行，避免在 DataStore 交易中做 Keystore 運算
        // PJM 密碼不退回明文：Keystore 失敗時（null）保留原本已儲存的值
        val encrypted = password?.let { encryptOrNull(it) }
        context.dataStore.edit { prefs ->
            prefs[PJM_SYNC_ENABLED] = enabled
            prefs[PJM_USERNAME] = username.trim()
            if (encrypted != null) prefs[PJM_PASSWORD_ENC] = encrypted
        }
    }

    /** 解密取得 PJM 密碼；未設定回傳 ""，解密失敗（金鑰失效）回傳 null */
    suspend fun getPjmPassword(): String? {
        val encoded = context.dataStore.data.first()[PJM_PASSWORD_ENC].orEmpty()
        return SecureStringCipher.decrypt(encoded)
    }

    suspend fun saveEnableWatchSync(enable: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ENABLE_WATCH_SYNC] = enable
        }
    }

    suspend fun saveSelectedAppIconIndex(index: Int) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_APP_ICON_INDEX] = index
        }
    }

    private fun requestWidgetsAndTilesUpdate(context: Context) {
        try {
            @Suppress("UNCHECKED_CAST")
            val clazz = Class.forName("com.jas0n1ee.wixtar.chronoswear.tile.MainTileService") as Class<out WearTileService>
            WearTileService.getUpdater(context).requestUpdate(clazz)
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val component = ComponentName(context, "com.jas0n1ee.wixtar.chronos.mobile.service.WiXtarTileService")
                QsTileService.requestListeningState(context, component)
            }
        } catch (_: Exception) {}

        try {
            val clazz = Class.forName("com.jas0n1ee.wixtar.chronos.mobile.widget.PunchWidgetActionReceiver")
            val method = clazz.getMethod("triggerUpdateAllWidgets", Context::class.java)
            method.invoke(null, context)
        } catch (_: Exception) {}

        try {
            val intent = Intent("com.jas0n1ee.wixtar.chronos.UPDATE_WIDGETS").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    suspend fun saveClockIn(time: String) {
        saveClockInWithSource(time, "本地離線")
    }

    suspend fun saveClockInWithSource(time: String, source: String) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[CLOCK_IN_TIME] = time
            prefs[CLOCK_IN_SOURCE] = source
            prefs[LAST_CLOCK_DATE] = today
        }
        requestWidgetsAndTilesUpdate(context)
    }

    suspend fun saveClockOut(time: String) {
        saveClockOutWithSource(time, "本地離線")
    }

    suspend fun saveClockOutWithSource(time: String, source: String) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[CLOCK_OUT_TIME] = time
            prefs[CLOCK_OUT_SOURCE] = source
            prefs[LAST_CLOCK_DATE] = today
        }
        requestWidgetsAndTilesUpdate(context)
    }

    suspend fun saveClockDataWithSources(inTime: String, inSource: String, outTime: String, outSource: String) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[CLOCK_IN_TIME] = inTime
            prefs[CLOCK_IN_SOURCE] = inSource
            prefs[CLOCK_OUT_TIME] = outTime
            prefs[CLOCK_OUT_SOURCE] = outSource
            prefs[LAST_CLOCK_DATE] = today
        }
        requestWidgetsAndTilesUpdate(context)
    }

    suspend fun clearClockData() {
        context.dataStore.edit { prefs ->
            prefs.remove(CLOCK_IN_TIME)
            prefs.remove(CLOCK_IN_SOURCE)
            prefs.remove(CLOCK_OUT_TIME)
            prefs.remove(CLOCK_OUT_SOURCE)
            prefs.remove(LAST_CLOCK_DATE)
        }
        requestWidgetsAndTilesUpdate(context)
    }

    suspend fun clearClockDataIfExpired(): Boolean {
        var cleared = false
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val lastDate = prefs[LAST_CLOCK_DATE]
            val hasStoredClockData = !prefs[CLOCK_IN_TIME].isNullOrEmpty() || !prefs[CLOCK_OUT_TIME].isNullOrEmpty()
            if ((lastDate != null && lastDate != today) || (lastDate == null && hasStoredClockData)) {
                prefs.remove(CLOCK_IN_TIME)
                prefs.remove(CLOCK_IN_SOURCE)
                prefs.remove(CLOCK_OUT_TIME)
                prefs.remove(CLOCK_OUT_SOURCE)
                prefs.remove(LAST_CLOCK_DATE)
                cleared = true
            }
        }
        if (cleared) {
            requestWidgetsAndTilesUpdate(context)
        }
        return cleared
    }
}
