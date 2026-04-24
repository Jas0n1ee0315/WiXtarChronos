package com.wixtar.watchtimeclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val LAST_CLOCK_DATE = stringPreferencesKey("last_clock_date")

        val NOTIFY_BEFORE_OFF_WORK = booleanPreferencesKey("notify_before_off_work")
        val NOTIFY_ON_OFF_WORK = booleanPreferencesKey("notify_on_off_work")
    }

    val empId: Flow<String> = context.dataStore.data.map { it[EMP_ID] ?: "" }
    val password: Flow<String> = context.dataStore.data.map { it[PASSWORD] ?: "" }
    val cookie: Flow<String> = context.dataStore.data.map { it[COOKIE] ?: "" }
    val enc: Flow<String> = context.dataStore.data.map { it[ENC] ?: "" }
    val gpsLocation: Flow<String> = context.dataStore.data.map { it[GPS_LOCATION] ?: "22.987929,120.197210" }

    val notifyBeforeOffWork: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_BEFORE_OFF_WORK] ?: true }
    val notifyOnOffWork: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_ON_OFF_WORK] ?: true }

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

    suspend fun saveSettings(empId: String, pass: String, cookie: String, enc: String, gps: String, notifyBefore: Boolean, notifyOn: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EMP_ID] = empId
            prefs[PASSWORD] = pass
            prefs[COOKIE] = cookie
            prefs[ENC] = enc
            prefs[GPS_LOCATION] = gps
            prefs[NOTIFY_BEFORE_OFF_WORK] = notifyBefore
            prefs[NOTIFY_ON_OFF_WORK] = notifyOn
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
                // 我們不清除 LAST_CLOCK_DATE，或者更新它，取決於邏輯
                // 這裡選擇不主動更新 LAST_CLOCK_DATE 為今日，直到真的打卡
            }
        }
    }
}
