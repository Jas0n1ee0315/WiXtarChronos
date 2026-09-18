package com.jas0n1ee.wixtar.chronos.data

import android.content.Context
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
import java.time.format.DateTimeFormatter

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
        val LAST_CLOCK_DATE = stringPreferencesKey("last_clock_date")

        val NOTIFY_BEFORE_OFF_WORK = booleanPreferencesKey("notify_before_off_work")
        val NOTIFY_ON_OFF_WORK = booleanPreferencesKey("notify_on_off_work")
        val NOTIFY_ADVANCE_MINUTES = intPreferencesKey("notify_advance_minutes")
        val USE_LIVE_UPDATES = booleanPreferencesKey("use_live_updates")
        val ENABLE_WATCH_SYNC = booleanPreferencesKey("enable_watch_sync")

        val FORGET_CLOCK_OUT_NOTIFY_ENABLED = booleanPreferencesKey("forget_clock_out_notify_enabled")
        val FORGET_CLOCK_OUT_NOTIFY_TIME = stringPreferencesKey("forget_clock_out_notify_time")

        val FISH_OIL_NOTIFY_ENABLED = booleanPreferencesKey("fish_oil_notify_enabled")
        val FISH_OIL_NOTIFY_TIME = stringPreferencesKey("fish_oil_notify_time")
        val FISH_OIL_NOTIFY_DAYS = stringPreferencesKey("fish_oil_notify_days")

        val AUTO_CLOCK_IN_ENABLED = booleanPreferencesKey("auto_clock_in_enabled")
        val WORK_START_TIME = stringPreferencesKey("work_start_time")
        val FLEXIBLE_CLOCK_IN_ENABLED = booleanPreferencesKey("flexible_clock_in_enabled")
        val FLEXIBLE_START_TIME = stringPreferencesKey("flexible_start_time")
        val FLEXIBLE_END_TIME = stringPreferencesKey("flexible_end_time")

        val AUTO_CLOCK_OUT_ENABLED = booleanPreferencesKey("auto_clock_out_enabled")
        val AUTO_PUNCH_PAUSE_DAYS = stringPreferencesKey("auto_punch_pause_days")

        val LUNCH_DND_ENABLED = booleanPreferencesKey("lunch_dnd_enabled")
        val LUNCH_DND_START_TIME = stringPreferencesKey("lunch_dnd_start_time")
        val LUNCH_DND_END_TIME = stringPreferencesKey("lunch_dnd_end_time")

        val MOCK_OFF_WORK_TIME = stringPreferencesKey("mock_off_work_time")
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
    val forgetClockOutNotifyTime: Flow<String> = context.dataStore.data.map { it[FORGET_CLOCK_OUT_NOTIFY_TIME] ?: "18:30" }

    val fishOilNotifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[FISH_OIL_NOTIFY_ENABLED] ?: false }
    val fishOilNotifyTime: Flow<String> = context.dataStore.data.map { it[FISH_OIL_NOTIFY_TIME] ?: "13:10" }
    val fishOilNotifyDays: Flow<String> = context.dataStore.data.map { it[FISH_OIL_NOTIFY_DAYS] ?: "1,2,3,4,5,6,7" }

    val autoClockInEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CLOCK_IN_ENABLED] ?: false }
    val workStartTime: Flow<String> = context.dataStore.data.map { it[WORK_START_TIME] ?: "08:30" }
    val flexibleClockInEnabled: Flow<Boolean> = context.dataStore.data.map { it[FLEXIBLE_CLOCK_IN_ENABLED] ?: false }
    val flexibleStartTime: Flow<String> = context.dataStore.data.map { it[FLEXIBLE_START_TIME] ?: "08:00" }
    val flexibleEndTime: Flow<String> = context.dataStore.data.map { it[FLEXIBLE_END_TIME] ?: "09:00" }

    val autoClockOutEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CLOCK_OUT_ENABLED] ?: false }
    val autoPunchPauseDays: Flow<String> = context.dataStore.data.map { it[AUTO_PUNCH_PAUSE_DAYS] ?: "" }

    val lunchDndEnabled: Flow<Boolean> = context.dataStore.data.map { it[LUNCH_DND_ENABLED] ?: false }
    val lunchDndStartTime: Flow<String> = context.dataStore.data.map { it[LUNCH_DND_START_TIME] ?: "12:00" }
    val lunchDndEndTime: Flow<String> = context.dataStore.data.map { it[LUNCH_DND_END_TIME] ?: "13:00" }

    val mockOffWorkTime: Flow<String> = context.dataStore.data.map { it[MOCK_OFF_WORK_TIME] ?: "17:30" }

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
        forgetClockOutTime: String = "18:30",
        fishOilEnabled: Boolean = false,
        fishOilTime: String = "13:10",
        fishOilDays: String = "1,2,3,4,5,6,7",
        autoClockIn: Boolean = false,
        workStart: String = "08:30",
        flexibleClockIn: Boolean = false,
        flexibleStart: String = "08:00",
        flexibleEnd: String = "09:00",
        autoClockOut: Boolean = false,
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
            // When autoClockOut is enabled, notifyOn (滿9小時可下班通知) is turned off
            prefs[NOTIFY_ON_OFF_WORK] = if (autoClockOut) false else notifyOn
            prefs[NOTIFY_ADVANCE_MINUTES] = advanceMinutes
            prefs[USE_LIVE_UPDATES] = useLiveUpdates
            prefs[FORGET_CLOCK_OUT_NOTIFY_ENABLED] = forgetClockOutEnabled
            prefs[FORGET_CLOCK_OUT_NOTIFY_TIME] = forgetClockOutTime
            prefs[FISH_OIL_NOTIFY_ENABLED] = fishOilEnabled
            prefs[FISH_OIL_NOTIFY_TIME] = fishOilTime
            prefs[FISH_OIL_NOTIFY_DAYS] = fishOilDays
            prefs[AUTO_CLOCK_IN_ENABLED] = autoClockIn
            prefs[WORK_START_TIME] = workStart
            prefs[FLEXIBLE_CLOCK_IN_ENABLED] = flexibleClockIn
            prefs[FLEXIBLE_START_TIME] = flexibleStart
            prefs[FLEXIBLE_END_TIME] = flexibleEnd
            prefs[AUTO_CLOCK_OUT_ENABLED] = autoClockOut
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

    suspend fun saveClockIn(time: String) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[CLOCK_IN_TIME] = time
            prefs[CLOCK_OUT_TIME] = ""
            prefs[LAST_CLOCK_DATE] = today
        }
    }

    suspend fun saveClockOut(time: String) {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            prefs[CLOCK_OUT_TIME] = time
            prefs[LAST_CLOCK_DATE] = today
        }
    }

    suspend fun clearClockDataIfExpired() {
        context.dataStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val lastDate = prefs[LAST_CLOCK_DATE]
            if (lastDate != null && lastDate != today) {
                prefs[CLOCK_IN_TIME] = ""
                prefs[CLOCK_OUT_TIME] = ""
            }
        }
    }

    suspend fun saveAutoPunchPauseDays(daysCsv: String) {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val validDays = daysCsv.split(",")
            .mapNotNull { s -> s.trim().takeIf { it.isNotBlank() } }
            .filter { dateStr ->
                try {
                    val date = LocalDate.parse(dateStr, formatter)
                    !date.isBefore(today)
                } catch (_: Exception) {
                    false
                }
            }
            .sorted()
            .joinToString(",")

        context.dataStore.edit { prefs ->
            prefs[AUTO_PUNCH_PAUSE_DAYS] = validDays
        }
    }

    suspend fun cleanExpiredPauseDays() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        context.dataStore.edit { prefs ->
            val rawCsv = prefs[AUTO_PUNCH_PAUSE_DAYS] ?: ""
            if (rawCsv.isNotBlank()) {
                val validDates = rawCsv.split(",")
                    .mapNotNull { s -> s.trim().takeIf { it.isNotBlank() } }
                    .filter { dateStr ->
                        try {
                            val date = LocalDate.parse(dateStr, formatter)
                            !date.isBefore(today)
                        } catch (_: Exception) {
                            false
                        }
                    }
                prefs[AUTO_PUNCH_PAUSE_DAYS] = validDates.sorted().joinToString(",")
            }
        }
    }
}
