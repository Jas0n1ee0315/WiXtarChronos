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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppStorage(private val context: Context) {
    companion object {
        val EMP_ID = stringPreferencesKey("emp_id")
        val PASSWORD = stringPreferencesKey("password")
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
    }

    val empId: Flow<String> = context.dataStore.data.map { it[EMP_ID] ?: "" }
    val password: Flow<String> = context.dataStore.data.map { it[PASSWORD] ?: "" }
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
        context.dataStore.edit { prefs ->
            prefs[EMP_ID] = empId
            prefs[PASSWORD] = pass
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

    suspend fun clearClockDataIfExpired() {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val lastDate = prefs[LAST_CLOCK_DATE]
            if (lastDate != null && lastDate != today) {
                prefs[CLOCK_IN_TIME] = ""
                prefs[CLOCK_IN_SOURCE] = ""
                prefs[CLOCK_OUT_TIME] = ""
                prefs[CLOCK_OUT_SOURCE] = ""
            }
        }
        requestWidgetsAndTilesUpdate(context)
    }
}
