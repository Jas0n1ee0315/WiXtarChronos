package com.jas0n1ee.wixtar.chronos.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * PJM（https://pjm.wixtar.com）研發工時 API。
 *
 * 串接的 API（取自網站前端程式，非官方文件，網站改版時可能變動）：
 *   POST /api/auth/login             表單 username / password → { access_token, user: { id } }
 *   GET  /api/clients/               找「Z1150001 請假」客戶 id
 *   GET  /api/rnd-hours/categories   找「ADM行政事務」分類 id
 *   GET  /api/rnd-hours/calendar     查既有工時，避免重複送出
 *   POST /api/rnd-hours/batch        送出工時（等同網頁「儲存這些工時」）
 *
 * 帳密從 AppStorage 讀取，密碼以 Android Keystore 加密保存（SecureStringCipher）。
 */
class PjmRepository(
    private val storage: AppStorage,
    private val calendar: TaiwanCalendarRepository = TaiwanCalendarRepository()
) {
    companion object {
        private const val TAG = "PjmRepository"
        const val BASE_URL = "https://pjm.wixtar.com/api"
        const val LEAVE_CLIENT_CODE = "Z1150001"   // 客戶「請假」
        const val LEAVE_CATEGORY_PREFIX = "ADM"    // 工時分類「ADM行政事務」
        const val LEAVE_DESCRIPTION = "請假"       // 說明欄
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /** 同步結果：已送出的日期，以及略過的日期與原因 */
    data class SyncResult(
        val submitted: List<PjmLeaveDay>,
        val skipped: List<Pair<LocalDate, String>>
    )

    private data class Session(val token: String, val userId: Long, val displayName: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 設定頁「測試登入」：成功回傳 PJM 上的顯示名稱 */
    suspend fun testLogin(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { login(username, password).displayName }
    }

    /**
     * 送出請假工時。
     * - 週末、國定假日（查不到行事曆時只略過週末）、當天已有請假工時的日期會略過。
     * - 全部日期都略過時不會呼叫送出 API，仍回傳 success。
     */
    suspend fun syncLeave(days: List<PjmLeaveDay>): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val username = storage.pjmUsername.first().ifBlank { storage.empId.first() }.trim()
            val password = storage.getPjmPassword()
                ?: throw IllegalStateException("PJM 密碼無法解密，請到設定重新輸入")
            if (username.isBlank() || password.isBlank()) {
                throw IllegalStateException("尚未設定 PJM 帳號或密碼")
            }

            val skipped = mutableListOf<Pair<LocalDate, String>>()

            // 1) 過濾非工作日
            val workDays = days.filter { day ->
                when (isWorkDay(day.date)) {
                    true -> true
                    false -> { skipped += day.date to "非工作日"; false }
                }
            }
            if (workDays.isEmpty()) return@runCatching SyncResult(emptyList(), skipped)

            // 2) 登入並找出 id（以代碼查詢，不寫死 id）
            val session = login(username, password)
            val clientId = getArray(session, "/clients/").firstObject { it.optString("project_code") == LEAVE_CLIENT_CODE }
                ?.optLong("id") ?: throw IllegalStateException("PJM 找不到客戶 $LEAVE_CLIENT_CODE 請假")
            val categoryId = getArray(session, "/rnd-hours/categories").firstObject { it.optString("name").startsWith(LEAVE_CATEGORY_PREFIX) }
                ?.optLong("id") ?: throw IllegalStateException("PJM 找不到工時分類 $LEAVE_CATEGORY_PREFIX")

            // 3) 查既有工時：同一天已有自己的請假工時（未被駁回）就不重送
            val from = workDays.minOf { it.date }
            val to = workDays.maxOf { it.date }
            val existing = getArray(session, "/rnd-hours/calendar?date_from=$from&date_to=$to")
            val alreadyLeave = mutableSetOf<String>()
            for (i in 0 until existing.length()) {
                val r = existing.optJSONObject(i) ?: continue
                if (r.optLong("user_id") == session.userId &&
                    r.optLong("client_id") == clientId &&
                    r.optString("status") != "已駁回"
                ) alreadyLeave += r.optString("work_date")
            }
            val toSubmit = workDays.filter { day ->
                if (day.date.toString() in alreadyLeave) { skipped += day.date to "當天已有請假工時"; false } else true
            }

            // 4) 送出
            if (toSubmit.isNotEmpty()) {
                val entries = JSONArray()
                toSubmit.forEach { day ->
                    entries.put(JSONObject().apply {
                        put("work_date", day.date.toString())
                        put("project_id", JSONObject.NULL)
                        put("client_id", clientId)
                        put("category_id", categoryId)
                        put("name", LEAVE_DESCRIPTION)
                        put("hours", day.hours)
                    })
                }
                val body = JSONObject().put("entries", entries).toString().toRequestBody(JSON)
                val request = authed(session, "/rnd-hours/batch").post(body).build()
                client.newCall(request).execute().use { resp -> ensureOk(resp, "送出工時") }
            }
            SyncResult(toSubmit, skipped)
        }.onFailure { Log.w(TAG, "syncLeave failed: ${it.message}") }
    }

    /** 台灣行事曆判斷；查詢失敗時退回「週一到週五算工作日」 */
    private suspend fun isWorkDay(date: LocalDate): Boolean =
        calendar.isWorkDay(date).getOrElse {
            date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        }

    /** 登入：FastAPI OAuth2 表單格式（不是 JSON） */
    private fun login(username: String, password: String): Session {
        val form = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url("$BASE_URL/auth/login").post(form).build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 400 || resp.code == 401) throw IllegalStateException("PJM 帳號或密碼錯誤")
            val json = JSONObject(ensureOk(resp, "登入"))
            val user = json.optJSONObject("user")
            val token = json.optString("access_token")
            if (token.isBlank() || user == null) throw IllegalStateException("PJM 登入回應格式不符，網站可能已改版")
            return Session(token, user.optLong("id"), user.optString("display_name"))
        }
    }

    private fun authed(session: Session, path: String): Request.Builder =
        Request.Builder().url("$BASE_URL$path").header("Authorization", "Bearer ${session.token}")

    private fun getArray(session: Session, path: String): JSONArray =
        client.newCall(authed(session, path).get().build()).execute().use { resp ->
            JSONArray(ensureOk(resp, "查詢 $path"))
        }

    /** 非 2xx 時丟出包含後端 detail 的錯誤訊息；成功回傳 body 字串 */
    private fun ensureOk(resp: Response, action: String): String {
        val text = resp.body?.string().orEmpty()
        if (resp.isSuccessful) return text
        val detail = runCatching { JSONObject(text).opt("detail")?.toString() }.getOrNull()
        throw IllegalStateException("PJM $action 失敗（HTTP ${resp.code}）${detail?.let { "：$it" } ?: ""}")
    }

    private inline fun JSONArray.firstObject(predicate: (JSONObject) -> Boolean): JSONObject? {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            if (predicate(o)) return o
        }
        return null
    }
}
