package com.jas0n1ee.wixtar.chronos.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.IOException
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

class ClockRepository(private val storage: AppStorage) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = LinkedHashMap<String, Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                cookieStore[cookie.name] = cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore.values.toList()
        }

        fun toHeaderString(): String {
            return cookieStore.values.joinToString("; ") { "${it.name}=${it.value}" }
        }
    }

    private fun decodeResponseBody(bytes: ByteArray, preferMarker: String, secondaryMarker: String): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (utf8.contains(preferMarker) || utf8.contains(secondaryMarker)) {
            return utf8
        }
        val big5 = String(bytes, Charset.forName("Big5"))
        if (big5.contains(preferMarker) || big5.contains(secondaryMarker)) {
            return big5
        }
        return utf8
    }

    private fun looksLikeLoginOrExpiredPage(body: String): Boolean {
        return body.contains("login.js") ||
            body.contains("name=\"uid\"") ||
            body.contains("name='uid'") ||
            body.contains("帳號密碼錯誤") ||
            body.contains("重新登入") ||
            body.contains("登入逾時") ||
            body.contains("請先登入")
    }

    private fun extractEncFromHtml(html: String): String? {
        val patterns = listOf(
            Pattern.compile("""<input[^>]*name=["']?enc["']?[^>]*value=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<input[^>]*name=["']?enc["']?[^>]*value=([^\s>]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<input[^>]*value=["']([^"']+)["'][^>]*name=["']?enc["']?""", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    /**
     * Logs in to the HR portal using employee ID and password, retrieves a valid
     * session Cookie and dynamic `enc` token from the B3.8 punch form, and saves them.
     */
    suspend fun loginAndFetchCredentials(
        empId: String,
        pass: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val trimmedId = empId.trim()
        val trimmedPass = pass.trim()

        if (trimmedId.isBlank() || trimmedPass.isBlank()) {
            return@withContext Result.failure(
                Exception("自動登入失敗：請先填寫工號與密碼")
            )
        }

        val cookieJar = InMemoryCookieJar()
        val loginClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .build()

        try {
            val loginBody = FormBody.Builder()
                .add("uid", trimmedId)
                .add("pwd", trimmedPass)
                .add("locale", "TW")
                .add("file", "hrm8w.pkg,hrm8bw.pkg")
                .build()

            val loginRequest = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://hr.wixtar.com/hrm8w.html")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(loginBody)
                .build()

            val loginResp = loginClient.newCall(loginRequest).execute()
            val loginBytes = loginResp.body?.bytes() ?: byteArrayOf()
            val loginText = decodeResponseBody(loginBytes, "帳號密碼錯誤", "B3.8")

            if (loginText.contains("帳號密碼錯誤") ||
                loginText.contains("Account password is wrong") ||
                loginText.contains("class=\"error\"") ||
                loginText.contains("class='error'")
            ) {
                return@withContext Result.failure(Exception("自動登入失敗：工號或密碼錯誤"))
            }

            val cookieStr = cookieJar.toHeaderString()
            if (cookieStr.isBlank()) {
                return@withContext Result.failure(Exception("自動登入失敗：未取得登入 Cookie"))
            }

            val punchPageUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.8.%E7%B7%9A%E4%B8%8A%E7%B0%BD%E5%88%B0%E7%B0%BD%E9%80%80"
            val punchPageRequest = Request.Builder()
                .url(punchPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()

            val pageResp = loginClient.newCall(punchPageRequest).execute()
            val pageBytes = pageResp.body?.bytes() ?: byteArrayOf()
            val pageHtml = decodeResponseBody(pageBytes, "enc", "EMPID")

            val encStr = extractEncFromHtml(pageHtml)
            if (encStr.isNullOrBlank()) {
                return@withContext Result.failure(Exception("自動登入失敗：無法從打卡頁面取得 enc 金鑰，請檢查帳號密碼"))
            }

            val finalCookieStr = cookieJar.toHeaderString().ifBlank { cookieStr }
            storage.saveCredentials(finalCookieStr, encStr)
            Log.d("ClockRepository", "Auto-login succeeded: cookieLength=${finalCookieStr.length}, encLength=${encStr.length}")
            Result.success(finalCookieStr to encStr)
        } catch (e: IOException) {
            Log.e("ClockRepository", "Auto-login network error", e)
            Result.failure(Exception("自動登入失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Log.e("ClockRepository", "Auto-login error", e)
            Result.failure(e)
        }
    }

    suspend fun checkHrSystemHealth(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://hr.wixtar.com/hrm8w.html")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .get()
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful && response.code == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clockIn(
        empId: String,
        pass: String,
        cookie: String,
        enc: String,
        gps: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedId = empId.trim()
        val trimmedPass = pass.trim()
        var activeCookie = cookie.trim()
        var activeEnc = enc.trim()
        val trimmedGps = gps.trim()

        if (trimmedId.isBlank() || trimmedPass.isBlank()) {
            return@withContext Result.failure(
                Exception("打卡失敗：請先至 [系統設定] 填寫工號與密碼")
            )
        }

        var usedFreshLogin = false
        if (activeCookie.isBlank() || activeEnc.isBlank()) {
            val loginResult = loginAndFetchCredentials(trimmedId, trimmedPass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(
                    loginResult.exceptionOrNull() ?: Exception("自動登入取得憑證失敗")
                )
            }
            val (newCookie, newEnc) = loginResult.getOrThrow()
            activeCookie = newCookie
            activeEnc = newEnc
            usedFreshLogin = true
        }

        val firstAttempt = executeClockInRequest(trimmedId, trimmedPass, activeCookie, activeEnc, trimmedGps)
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }

        val firstError = firstAttempt.exceptionOrNull()
        if (firstError is AuthExpiredException) {
            Log.d("ClockRepository", "Cookie/enc appears expired, attempting auto-relogin...")
            val refreshResult = loginAndFetchCredentials(trimmedId, trimmedPass)
            if (refreshResult.isSuccess) {
                val (newCookie, newEnc) = refreshResult.getOrThrow()
                return@withContext executeClockInRequest(trimmedId, trimmedPass, newCookie, newEnc, trimmedGps)
            }
        }

        firstAttempt
    }

    private class AuthExpiredException(message: String) : Exception(message)

    private fun executeClockInRequest(
        empId: String,
        pass: String,
        cookie: String,
        enc: String,
        gps: String
    ): Result<String> {
        Log.d("ClockRepository", "clockIn called: empId=$empId, passLength=${pass.length}, cookieLength=${cookie.length}, encLength=${enc.length}, gps=$gps")
        val formBody = FormBody.Builder()
            .add("em_step", "ajax")
            .add("encodeURIComponent", "1")
            .add("locale", "TW")
            .add("FUNCTION_NAME", "B3.8.線上簽到簽退")
            .add("buttonid", "SIGN_IN_OUT")
            .add("buttonlink", "Ajax(No refresh Browser)")
            .add("em_POSITION", "1")
            .add("file", "hrm8w.pkg,hrm8bw.pkg")
            .add("enc", enc)
            .add("ITEM", "A")
            .add("EMPID", empId)
            .add("PASS", pass)
            .add("qCPNYID", "lafresh")
            .add("GPSADDR", gps)
            .build()

        val request = Request.Builder()
            .url("https://hr.wixtar.com/servlet/jform")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", cookie)
            .header("Referer", "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.8.%E7%B7%9A%E4%B8%8A%E7%B0%BD%E5%88%B0%E7%B0%BD%E9%80%80")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36")
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBytes = response.body?.bytes() ?: byteArrayOf()
            val body = decodeResponseBody(responseBytes, "打卡成功", "Message")

            Log.d("ClockRepository", "clockIn response body: $body")

            val isSuccess = body.contains("打卡成功") ||
                    body.contains("刷卡成功") ||
                    body.contains("簽到成功") ||
                    body.contains("簽退成功") ||
                    (body.contains("成功") && !body.contains("不成功") && !body.contains("失敗"))

            if (isSuccess) {
                val timeMatch = Pattern.compile("(?:時間| Time)[:\\s]*(\\d{2}:?\\d{2}|\\d{4})").matcher(body)
                val timeStr = if (timeMatch.find()) {
                    val raw = timeMatch.group(1)?.replace(":", "") ?: ""
                    if (raw.length >= 4) {
                        "${raw.substring(0, 2)}:${raw.substring(2, 4)}"
                    } else {
                        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    }
                } else {
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }
                Result.success(timeStr)
            } else {
                val messageMatch = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL).matcher(body)
                if (messageMatch.find()) {
                    val msg = messageMatch.group(1)?.trim() ?: "未知錯誤"
                    Result.failure(Exception("打卡失敗: $msg"))
                } else if (body.isBlank() || looksLikeLoginOrExpiredPage(body) || response.code == 401 || response.code == 403) {
                    Result.failure(AuthExpiredException("打卡失敗：登入憑證已失效"))
                } else {
                    val cleanText = body.replace(Regex("<[^>]*>"), "").trim()
                    if (cleanText.isNotBlank()) {
                        Result.failure(Exception("打卡失敗: " + cleanText.take(50)))
                    } else {
                        Result.failure(AuthExpiredException("打卡失敗：伺服器回應異常，請檢查 Cookie 或 enc 是否正確/過期"))
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("ClockRepository", "clockIn exception", e)
            Result.failure(e)
        }
    }

    suspend fun getCloudClockInfo(cookie: String): Result<Triple<String?, String?, String>> = withContext(Dispatchers.IO) {
        var activeCookie = cookie.trim()
        var usedFreshLogin = false

        if (activeCookie.isBlank()) {
            val empId = storage.empId.first().trim()
            val pass = storage.password.first().trim()
            if (empId.isNotBlank() && pass.isNotBlank()) {
                val loginResult = loginAndFetchCredentials(empId, pass)
                if (loginResult.isSuccess) {
                    activeCookie = loginResult.getOrThrow().first
                    usedFreshLogin = true
                } else {
                    return@withContext Result.failure(
                        loginResult.exceptionOrNull() ?: Exception("自動登入失敗")
                    )
                }
            } else {
                return@withContext Result.failure(Exception("未設定 Cookie 或工號密碼"))
            }
        }

        val firstAttempt = executeGetCloudClockInfo(activeCookie)
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }

        if (firstAttempt.exceptionOrNull() is AuthExpiredException) {
            val empId = storage.empId.first().trim()
            val pass = storage.password.first().trim()
            if (empId.isNotBlank() && pass.isNotBlank()) {
                val loginResult = loginAndFetchCredentials(empId, pass)
                if (loginResult.isSuccess) {
                    return@withContext executeGetCloudClockInfo(loginResult.getOrThrow().first)
                }
            }
        }

        firstAttempt
    }

    private fun executeGetCloudClockInfo(cookie: String): Result<Triple<String?, String?, String>> {
        val url = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=A1.%E5%80%8B%E4%BA%BA%E8%B3%87%E8%A8%8A%E5%8D%80"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBytes = response.body?.bytes() ?: byteArrayOf()
            val body = decodeResponseBody(responseBytes, "data-info", "in_card")

            if (looksLikeLoginOrExpiredPage(body)) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val dataInfoPattern = Pattern.compile("data-info=['\"](.*?)['\"]", Pattern.DOTALL)
            val matcher = dataInfoPattern.matcher(body)

            var foundInTime: String? = null
            var foundOutTime: String? = null
            var lastJsonStr = ""

            while (matcher.find()) {
                var jsonStr = matcher.group(1) ?: continue
                jsonStr = jsonStr.replace("&quot;", "\"").replace("&#39;", "'")

                try {
                    val rootArray = JSONArray(jsonStr)
                    for (i in 0 until rootArray.length()) {
                        val item = rootArray.getJSONObject(i)
                        if (item.optString("name") == "個人刷卡") {
                            val dataArray = item.getJSONArray("data")
                            if (dataArray.length() > 0) {
                                val firstEntry = dataArray.getJSONObject(0)
                                foundInTime = firstEntry.optString("in_card").let { if (it.isNullOrBlank() || it == "null") null else it }
                                foundOutTime = firstEntry.optString("out_card").let { if (it.isNullOrBlank() || it == "null") null else it }
                                lastJsonStr = jsonStr
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
                if (foundInTime != null && foundOutTime != null) break
            }

            if (foundInTime == null) {
                val inCardPattern = Pattern.compile("\"in_card\"\\s*:\\s*\"(\\d{2}:\\d{2})\"")
                val inMatcher = inCardPattern.matcher(body)
                if (inMatcher.find()) {
                    foundInTime = inMatcher.group(1)
                }
            }

            if (foundOutTime == null) {
                val outCardPattern = Pattern.compile("\"out_card\"\\s*:\\s*\"(\\d{2}:\\d{2})\"")
                val outMatcher = outCardPattern.matcher(body)
                if (outMatcher.find()) {
                    foundOutTime = outMatcher.group(1)
                }
            }

            if (foundInTime != null || foundOutTime != null) {
                Log.d("ClockRepository", "最終抓取結果 - 上班: $foundInTime, 下班: $foundOutTime")
                Result.success(Triple(foundInTime, foundOutTime, lastJsonStr))
            } else {
                Log.e("ClockRepository", "完全找不到打卡資訊")
                Result.failure(Exception("在網頁中找不到您的打卡時間"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches attendance summary rows from B4.1.考勤彙總表 for the given date range,
     * sorted descending by date.
     */
    suspend fun getAttendanceSummary(
        startDate: LocalDate,
        endDate: LocalDate,
        skind: String = "B"
    ): Result<List<AttendanceRecord>> = withContext(Dispatchers.IO) {
        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var activeCookie = storage.cookie.first().trim()
        var usedFreshLogin = false

        if (activeCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(
                    loginResult.exceptionOrNull() ?: Exception("自動登入失敗")
                )
            }
            activeCookie = loginResult.getOrThrow().first
            usedFreshLogin = true
        }

        val firstAttempt = executeGetAttendanceSummary(activeCookie, startDate, endDate, skind, empId)
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }

        if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                return@withContext executeGetAttendanceSummary(
                    loginResult.getOrThrow().first,
                    startDate,
                    endDate,
                    skind,
                    empId
                )
            }
        }

        firstAttempt
    }

    private fun executeGetAttendanceSummary(
        cookie: String,
        startDate: LocalDate,
        endDate: LocalDate,
        skind: String,
        fallbackEmpId: String
    ): Result<List<AttendanceRecord>> {
        val pageUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B4.1.%E8%80%83%E5%8B%A4%E5%BD%99%E7%B8%BD%E8%A1%A8"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        return try {
            val pageRequest = Request.Builder()
                .url(pageUrl)
                .header("Cookie", cookie)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            val pageHtml = decodeResponseBody(pageResponse.body?.bytes() ?: byteArrayOf(), "B4.1", "extjs_table1")

            val pageEnc = extractEncFromHtml(pageHtml)
            if (looksLikeLoginOrExpiredPage(pageHtml) || pageEnc.isNullOrBlank()) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val cpnyId = extractJsComboDefault(pageHtml, "extjs_CPNYID") ?: "lafresh"
            val deptNo = extractJsComboDefault(pageHtml, "extjs_DEPT_NO") ?: ""
            val formEmpId = extractJsComboDefault(pageHtml, "extjs_EMPID") ?: fallbackEmpId

            val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val formBody = FormBody.Builder()
                .add("em_step", "ajax")
                .add("encodeURIComponent", "1")
                .add("locale", "TW")
                .add("FUNCTION_NAME", "B4.1.考勤彙總表")
                .add("buttonid", "button2")
                .add("buttonlink", "Ajax(No refresh Browser)")
                .add("em_POSITION", "1")
                .add("file", "hrm8w.pkg,hrm8bw.pkg")
                .add("enc", pageEnc)
                .add("SDATE", startDate.format(dateFormatter))
                .add("EDATE", endDate.format(dateFormatter))
                .add("SKIND", skind)
                .add("CPNYID", cpnyId)
                .add("DEPT_NO", deptNo)
                .add("EMPID", formEmpId)
                .build()

            val queryRequest = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .header("Referer", pageUrl)
                .header("User-Agent", userAgent)
                .post(formBody)
                .build()

            val queryResponse = client.newCall(queryRequest).execute()
            val xmlBody = decodeResponseBody(queryResponse.body?.bytes() ?: byteArrayOf(), "setTableData", "extscripts")

            if (looksLikeLoginOrExpiredPage(xmlBody)) {
                return Result.failure(AuthExpiredException("查詢憑證已失效，需要重新登入"))
            }

            val records = parseAttendanceTableXml(xmlBody)
                .sortedByDescending { it.date.take(10) }
            Result.success(records)
        } catch (e: IOException) {
            Result.failure(Exception("查詢出勤表失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJsComboDefault(html: String, varName: String): String? {
        val pattern = Pattern.compile("""$varName\s*=.*?value\s*:\s*['"]([^'"]*)['"]""", Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun unescapeHtmlEntities(text: String): String {
        return text
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun parseAttendanceTableXml(xml: String): List<AttendanceRecord> {
        val unescaped = unescapeHtmlEntities(xml)
        val marker = "EMC['table1'].setTableData("
        val markerIndex = unescaped.indexOf(marker)
        if (markerIndex == -1) return emptyList()

        val arrayStart = unescaped.indexOf('[', markerIndex + marker.length)
        if (arrayStart == -1) return emptyList()

        val rows = parseJs2DStringArray(unescaped, arrayStart)
        val htmlTagRegex = Regex("<[^>]*>")
        val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        fun cleanCell(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw
                .replace(brRegex, " ")
                .replace(htmlTagRegex, "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        return rows.mapNotNull { cols ->
            if (cols.size < 19) return@mapNotNull null
            fun col(primary: Int, fallback: Int? = null): String {
                val primaryVal = cleanCell(cols.getOrNull(primary))
                if (primaryVal.isNotEmpty()) return primaryVal
                return fallback?.let { cleanCell(cols.getOrNull(it)) }.orEmpty()
            }
            AttendanceRecord(
                department = col(21, 0),
                empId = col(1),
                empName = col(22, 2),
                date = col(3),
                shiftName = col(23, 4),
                shiftTime = col(5),
                clockIn = col(24, 6),
                clockOut = col(25, 7),
                workHours = col(8),
                abnormal = col(9),
                leave = col(10),
                overtime = col(11),
                remark = col(12),
                plannedOvertime = col(13),
                makeupForm = col(14),
                link1 = col(26, 15),
                link2 = col(27, 16),
                pendingApproval = col(17),
                punchDetail = col(18)
            )
        }
    }

    private fun parseJs2DStringArray(input: String, startIndex: Int): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow: MutableList<String>? = null
        var depth = 0
        var i = startIndex
        val len = input.length

        while (i < len) {
            val ch = input[i]
            when {
                ch == '\'' || ch == '"' -> {
                    val quote = ch
                    val sb = StringBuilder()
                    i++
                    while (i < len) {
                        val c = input[i]
                        if (c == '\\' && i + 1 < len) {
                            sb.append(input[i + 1])
                            i += 2
                        } else if (c == quote) {
                            i++
                            break
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    if (depth == 2) {
                        currentRow?.add(sb.toString())
                    }
                }
                ch == '[' -> {
                    depth++
                    if (depth == 2) {
                        currentRow = mutableListOf()
                    }
                    i++
                }
                ch == ']' -> {
                    if (depth == 2) {
                        currentRow?.let { rows.add(it) }
                        currentRow = null
                    }
                    depth--
                    i++
                    if (depth == 0) break
                }
                else -> i++
            }
        }
        return rows
    }

    /**
     * Loads B3.7.線上補刷卡 form defaults and fetches the selected date's actual punch record (text5_H)
     * and scheduled shift time (CLASSTIME).
     */
    suspend fun getMakeupPunchInfo(targetDate: LocalDate = LocalDate.now()): Result<MakeupPunchPageInfo> =
        withContext(Dispatchers.IO) {
            val empId = storage.empId.first().trim()
            val pass = storage.password.first().trim()
            var activeCookie = storage.cookie.first().trim()
            var usedFreshLogin = false

            if (activeCookie.isBlank()) {
                if (empId.isBlank() || pass.isBlank()) {
                    return@withContext Result.failure(Exception("請先至 [系統設定] 填寫工號與密碼"))
                }
                val loginResult = loginAndFetchCredentials(empId, pass)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(
                        loginResult.exceptionOrNull() ?: Exception("自動登入失敗")
                    )
                }
                activeCookie = loginResult.getOrThrow().first
                usedFreshLogin = true
            }

            val firstAttempt = executeGetMakeupPunchInfo(activeCookie, targetDate, empId)
            if (firstAttempt.isSuccess || usedFreshLogin) {
                return@withContext firstAttempt
            }

            if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
                val loginResult = loginAndFetchCredentials(empId, pass)
                if (loginResult.isSuccess) {
                    return@withContext executeGetMakeupPunchInfo(loginResult.getOrThrow().first, targetDate, empId)
                }
            }

            firstAttempt
        }

    private fun executeGetMakeupPunchInfo(
        cookie: String,
        targetDate: LocalDate,
        fallbackEmpId: String
    ): Result<MakeupPunchPageInfo> {
        val pageUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.7.%E7%B7%9A%E4%B8%8A%E8%A3%9C%E5%88%B7%E5%8D%A1"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        return try {
            val pageRequest = Request.Builder()
                .url(pageUrl)
                .header("Cookie", cookie)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            val pageHtml = decodeResponseBody(pageResponse.body?.bytes() ?: byteArrayOf(), "B3.7", "CARDDATE")

            val pageEnc = extractEncFromHtml(pageHtml)
            if (looksLikeLoginOrExpiredPage(pageHtml) || pageEnc.isNullOrBlank()) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val cpnyId = extractInputValueByName(pageHtml, "CPNYID") ?: "lafresh"
            val deptNo = extractInputValueByName(pageHtml, "DEPT") ?: ""
            val companyName = extractInputValueByName(pageHtml, "CPNYID1") ?: "WiXtar"
            val deptName = extractInputValueByName(pageHtml, "DEPT1") ?: ""
            val formEmpId = extractJsComboDefault(pageHtml, "extjs_EMPID") ?: fallbackEmpId
            var text5H = extractInputValueByName(pageHtml, "text5_H") ?: ""
            var classTime = extractInputValueByName(pageHtml, "CLASSTIME") ?: ""
            var shiftClass = extractInputValueByName(pageHtml, "CLASS") ?: "D"

            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val targetStr = targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            if (targetStr != todayStr) {
                val dateBody = FormBody.Builder()
                    .add("em_step", "ajax")
                    .add("encodeURIComponent", "1")
                    .add("locale", "TW")
                    .add("FUNCTION_NAME", "B3.7.線上補刷卡")
                    .add("buttonid", "CARDDATE")
                    .add("buttonlink", "Ajax(No refresh Browser)")
                    .add("fromlink", "[html_check]")
                    .add("em_POSITION", "1")
                    .add("file", "hrm8w.pkg,hrm8bw.pkg")
                    .add("enc", pageEnc)
                    .add("DEPT", deptNo)
                    .add("CPNYID", cpnyId)
                    .add("CPNYID1", companyName)
                    .add("DEPT1", deptName)
                    .add("EMPID", formEmpId)
                    .add("CARDDATE", targetStr)
                    .add("CARDTIME", "")
                    .add("CARDIO", "")
                    .add("REASON", "")
                    .add("NOTE", "")
                    .add("CLASS", shiftClass)
                    .build()

                val dateReq = Request.Builder()
                    .url("https://hr.wixtar.com/servlet/jform")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", cookie)
                    .header("Referer", pageUrl)
                    .header("User-Agent", userAgent)
                    .post(dateBody)
                    .build()

                val dateResp = client.newCall(dateReq).execute()
                val dateXml = decodeResponseBody(dateResp.body?.bytes() ?: byteArrayOf(), "text5_H", "CLASSTIME")
                extractXmlSetValue(dateXml, "text5_H")?.let { text5H = it }
                extractXmlSetValue(dateXml, "CLASSTIME")?.let { classTime = it }
                extractXmlSetValue(dateXml, "CLASS")?.let { if (it.isNotBlank()) shiftClass = it }
            }

            Result.success(
                MakeupPunchPageInfo(
                    enc = pageEnc,
                    cpnyId = cpnyId,
                    deptNo = deptNo,
                    companyName = companyName,
                    deptName = deptName,
                    empId = formEmpId,
                    punchHistoryText = text5H,
                    classTime = classTime,
                    shiftClass = shiftClass
                )
            )
        } catch (e: IOException) {
            Result.failure(Exception("載入補打卡資訊失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submits a makeup punch application (B3.7.線上補刷卡) via multipart/form-data POST.
     */
    suspend fun submitMakeupPunch(
        info: MakeupPunchPageInfo,
        cardio: String,
        date: LocalDate,
        timeHHmm: String,
        reasonCode: String,
        note: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var activeCookie = storage.cookie.first().trim()
        var activeInfo = info

        if (activeCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("自動登入失敗"))
            }
            activeCookie = loginResult.getOrThrow().first
            val refreshedInfo = executeGetMakeupPunchInfo(activeCookie, date, empId)
            if (refreshedInfo.isSuccess) {
                activeInfo = refreshedInfo.getOrThrow()
            }
        }

        val firstAttempt = executeSubmitMakeupPunch(activeCookie, activeInfo, cardio, date, timeHHmm, reasonCode, note)
        if (firstAttempt.isSuccess) {
            return@withContext firstAttempt
        }

        if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                val newCookie = loginResult.getOrThrow().first
                val refreshedInfo = executeGetMakeupPunchInfo(newCookie, date, empId).getOrNull() ?: activeInfo
                return@withContext executeSubmitMakeupPunch(newCookie, refreshedInfo, cardio, date, timeHHmm, reasonCode, note)
            }
        }

        firstAttempt
    }

    private fun executeSubmitMakeupPunch(
        cookie: String,
        info: MakeupPunchPageInfo,
        cardio: String,
        date: LocalDate,
        timeHHmm: String,
        reasonCode: String,
        note: String
    ): Result<String> {
        val pageUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.7.%E7%B7%9A%E4%B8%8A%E8%A3%9C%E5%88%B7%E5%8D%A1"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val cardDateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val cleanTime = timeHHmm.replace(":", "").trim()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("locale", "TW")
            .addFormDataPart("FUNCTION_NAME", "B3.7.線上補刷卡")
            .addFormDataPart("act", "")
            .addFormDataPart("init_func", "")
            .addFormDataPart("flow_approve", "")
            .addFormDataPart("buttonid", "button1")
            .addFormDataPart("buttonlink", "Ajax(No refresh Browser)")
            .addFormDataPart("fromlink", "")
            .addFormDataPart("table_data", "")
            .addFormDataPart("form_target", "")
            .addFormDataPart("menu_display_flag", "")
            .addFormDataPart("em_step", "ajax")
            .addFormDataPart("encodeURIComponent", "1")
            .addFormDataPart("em_POSITION", "1")
            .addFormDataPart("file", "hrm8w.pkg,hrm8bw.pkg")
            .addFormDataPart("enc", info.enc)
            .addFormDataPart("EMPID2", "")
            .addFormDataPart("DEPT", info.deptNo)
            .addFormDataPart("CPNYID", info.cpnyId)
            .addFormDataPart("EMPID1", "")
            .addFormDataPart("REASON1", "")
            .addFormDataPart("APNAME1", "")
            .addFormDataPart("REASON2", "")
            .addFormDataPart("CLASSTIME", info.classTime)
            .addFormDataPart("text5_H", info.punchHistoryText)
            .addFormDataPart("CPNYID1", info.companyName)
            .addFormDataPart("DEPT1", info.deptName)
            .addFormDataPart("EMPID", info.empId)
            .addFormDataPart("CARDIO", cardio)
            .addFormDataPart("CARDDATE", cardDateStr)
            .addFormDataPart("CARDTIME", cleanTime)
            .addFormDataPart("REASON", reasonCode)
            .addFormDataPart("NOTE", note.trim())
            .addFormDataPart("CLASS", info.shiftClass)
            .addFormDataPart("FUNCTION", "")
            .build()

        val request = Request.Builder()
            .url("https://hr.wixtar.com/servlet/jform")
            .header("Cookie", cookie)
            .header("Referer", pageUrl)
            .header("User-Agent", userAgent)
            .post(multipartBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = decodeResponseBody(response.body?.bytes() ?: byteArrayOf(), "Message", "補登")
            Log.d("ClockRepository", "submitMakeupPunch response: $body")

            if (looksLikeLoginOrExpiredPage(body) || response.code == 401 || response.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val messageMatch = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL).matcher(body)
            val serverMessage = if (messageMatch.find()) {
                unescapeHtmlEntities(messageMatch.group(1)?.trim().orEmpty())
            } else {
                val scriptMsgMatch = Pattern.compile("""(?:alert|showMessage|MessageBox\.show)\s*\(\s*(?:\{[^}]*msg\s*:\s*)?['"]([^'"]+)['"]""").matcher(body)
                if (scriptMsgMatch.find()) {
                    unescapeHtmlEntities(scriptMsgMatch.group(1)?.trim().orEmpty())
                } else {
                    unescapeHtmlEntities(body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim())
                }
            }

            val isSuccess = (serverMessage.contains("完成") || serverMessage.contains("成功")) &&
                !serverMessage.contains("重複") &&
                !serverMessage.contains("失敗") &&
                !serverMessage.contains("不可") &&
                !serverMessage.contains("異常") &&
                !serverMessage.contains("錯誤")

            if (isSuccess) {
                Result.success(serverMessage.ifBlank { "補登單新增完成且進入流程等待簽核" })
            } else {
                Result.failure(Exception(serverMessage.ifBlank { "補打卡送出失敗，伺服器未回傳詳細訊息" }))
            }
        } catch (e: IOException) {
            Result.failure(Exception("送出補打卡失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractInputValueByName(html: String, name: String): String? {
        val patterns = listOf(
            Pattern.compile("""<input[^>]*name=["']?$name["']?[^>]*value=["']([^"']*)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<input[^>]*value=["']([^"']*)["'][^>]*name=["']?$name["']?""", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }

    private fun extractXmlSetValue(xml: String, fieldName: String): String? {
        val pattern = Pattern.compile("""<name>\s*$fieldName\s*</name>\s*<value>(.*?)</value>""", Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) unescapeHtmlEntities(matcher.group(1)?.trim().orEmpty()) else null
    }

    data class ColleagueInfo(
        val empId: String,
        val name: String,
        val deptName: String,
        val deptNo: String,
        val email: String,
        val location: String
    )

    /**
     * Queries colleague profile by employee ID from A2.夥伴資料查詢.
     */
    suspend fun queryColleagueById(targetEmpId: String): Result<ColleagueInfo> = withContext(Dispatchers.IO) {
        val trimmedTarget = targetEmpId.trim().uppercase()
        if (trimmedTarget.isBlank()) {
            return@withContext Result.failure(Exception("請輸入代理人工號"))
        }

        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var activeCookie = storage.cookie.first().trim()
        var usedFreshLogin = false

        if (activeCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定 -> 打卡驗證資訊] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("自動登入失敗"))
            }
            activeCookie = loginResult.getOrThrow().first
            usedFreshLogin = true
        }

        val firstAttempt = executeQueryColleagueById(activeCookie, trimmedTarget, empId)
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }

        if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                return@withContext executeQueryColleagueById(loginResult.getOrThrow().first, trimmedTarget, empId)
            }
        }

        firstAttempt
    }

    private fun executeQueryColleagueById(
        cookie: String,
        targetEmpId: String,
        currentUserId: String
    ): Result<ColleagueInfo> {
        val pageUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=A2.%E5%A4%A5%E4%BC%B4%E8%B3%87%E6%96%99%E6%9F%A5%E8%A9%A2"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        return try {
            val pageRequest = Request.Builder()
                .url(pageUrl)
                .header("Cookie", cookie)
                .header("User-Agent", userAgent)
                .get()
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            val pageHtml = decodeResponseBody(pageResponse.body?.bytes() ?: byteArrayOf(), "A2", "HECNAME")

            val pageEnc = extractEncFromHtml(pageHtml)
            if (looksLikeLoginOrExpiredPage(pageHtml) || pageEnc.isNullOrBlank()) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val formUserId = extractInputValueByName(pageHtml, "user_id") ?: currentUserId
            val formBody = FormBody.Builder()
                .add("em_step", "ajax")
                .add("encodeURIComponent", "1")
                .add("locale", "TW")
                .add("FUNCTION_NAME", "A2.夥伴資料查詢")
                .add("buttonid", "button1")
                .add("buttonlink", "Ajax(No refresh Browser)")
                .add("em_POSITION", "1")
                .add("file", "hrm8w.pkg,hrm8bw.pkg")
                .add("enc", pageEnc)
                .add("user_id", formUserId)
                .add("CONDITION_B", "")
                .add("DEP_NO", "")
                .add("ROW", "")
                .add("CPNYID", "")
                .add("EXT", "")
                .add("HEENAME", "")
                .add("EMPID", targetEmpId)
                .add("HECNAME", "")
                .build()

            val queryRequest = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .header("Referer", pageUrl)
                .header("User-Agent", userAgent)
                .post(formBody)
                .build()

            val queryResponse = client.newCall(queryRequest).execute()
            val xml = decodeResponseBody(queryResponse.body?.bytes() ?: byteArrayOf(), "HECNAME", "DATA")

            if (looksLikeLoginOrExpiredPage(xml)) {
                return Result.failure(AuthExpiredException("查詢憑證已失效，需要重新登入"))
            }

            val foundEmpId = extractXmlSetValue(xml, "EMPID")?.trim().orEmpty()
            val foundName = extractXmlSetValue(xml, "HECNAME")?.trim().orEmpty()
            val foundDeptNo = extractXmlSetValue(xml, "DEPT_NO")?.trim().orEmpty()
            val dataHtml = extractXmlSetValue(xml, "DATA").orEmpty()

            if (foundName.isBlank()) {
                return Result.failure(Exception("查無工號 $targetEmpId 的員工資料"))
            }

            fun extractTableField(label: String): String {
                val p = Pattern.compile("""<td[^>]*>\s*$label\s*</td>\s*<td[^>]*>(.*?)</td>""", Pattern.DOTALL)
                val m = p.matcher(dataHtml)
                return if (m.find()) {
                    m.group(1)?.replace(Regex("<[^>]*>"), "")?.replace("&nbsp", "")?.trim().orEmpty()
                } else ""
            }

            val deptName = extractTableField("單位名稱")
            val email = extractTableField("電子信箱")
            val location = extractTableField("工作地點")

            Result.success(
                ColleagueInfo(
                    empId = foundEmpId.ifBlank { targetEmpId },
                    name = foundName,
                    deptName = deptName,
                    deptNo = foundDeptNo,
                    email = email,
                    location = location
                )
            )
        } catch (e: IOException) {
            Result.failure(Exception("查詢同仁資料失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLeavePageInfo(): Result<LeavePageInfo> = withContext(Dispatchers.IO) {
        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var activeCookie = storage.cookie.first().trim()
        var usedFreshLogin = false

        if (activeCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定 -> 打卡驗證資訊] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("自動登入失敗"))
            }
            activeCookie = loginResult.getOrThrow().first
            usedFreshLogin = true
        }

        val firstAttempt = executeGetLeavePageInfo(activeCookie, empId)
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }
        if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            Log.d("ClockRepository", "Cookie expired on getLeavePageInfo, attempting auto re-login...")
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                return@withContext executeGetLeavePageInfo(loginResult.getOrThrow().first, empId)
            }
        }
        return@withContext firstAttempt
    }

    private fun executeGetLeavePageInfo(cookie: String, empId: String): Result<LeavePageInfo> {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val mainUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B2.1.%E8%AB%8B%E5%81%87%E5%96%AE"
        val mainRequest = Request.Builder()
            .url(mainUrl)
            .header("Cookie", cookie)
            .header("User-Agent", userAgent)
            .get()
            .build()

        return try {
            val mainResponse = client.newCall(mainRequest).execute()
            val mainHtml = decodeResponseBody(mainResponse.body?.bytes() ?: byteArrayOf(), "VACATION_INFO", "請假單")
            if (looksLikeLoginOrExpiredPage(mainHtml) || mainResponse.code == 401 || mainResponse.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val mainEnc = extractEncFromHtml(mainHtml)
            if (mainEnc.isNullOrBlank()) {
                return Result.failure(Exception("無法取得請假單頁面憑證 (enc)"))
            }

            val vacationSummaries = parseVacationSummaries(mainHtml)

            val addBody = FormBody.Builder()
                .add("locale", "TW")
                .add("FUNCTION_NAME", "B2.1.請假單")
                .add("act", "")
                .add("init_func", "")
                .add("flow_approve", "")
                .add("buttonid", "button1")
                .add("buttonlink", "ADD")
                .add("fromlink", "")
                .add("table_data", "!NULL!")
                .add("form_target", "")
                .add("menu_display_flag", "")
                .add("em_step", "0")
                .add("em_POSITION", "1")
                .add("file", "hrm8w.pkg,hrm8bw.pkg")
                .add("enc", mainEnc)
                .build()

            val addRequest = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .header("Referer", mainUrl)
                .header("User-Agent", userAgent)
                .post(addBody)
                .build()

            val addResponse = client.newCall(addRequest).execute()
            val addHtml = decodeResponseBody(addResponse.body?.bytes() ?: byteArrayOf(), "HCODE", "請假單")
            if (looksLikeLoginOrExpiredPage(addHtml) || addResponse.code == 401 || addResponse.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val addEnc = extractEncFromHtml(addHtml) ?: mainEnc
            val htmlEmpId = extractInputValueByName(addHtml, "EMPID")?.takeIf { it.isNotBlank() } ?: empId
            val empName = extractInputValueByName(addHtml, "HECNAME").orEmpty()
            val empEngName = extractInputValueByName(addHtml, "HEENAME").orEmpty()
            var deptNo = extractInputValueByName(addHtml, "DEPT").orEmpty()
            var cpnyId = extractInputValueByName(addHtml, "CPNYID").orEmpty()

            if (deptNo.isBlank() || cpnyId.isBlank()) {
                val makeupUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.7.%E7%B7%9A%E4%B8%8A%E8%A3%9C%E5%88%B7%E5%8D%A1"
                val makeupReq = Request.Builder()
                    .url(makeupUrl)
                    .header("Cookie", cookie)
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
                runCatching {
                    val makeupHtml = decodeResponseBody(client.newCall(makeupReq).execute().body?.bytes() ?: byteArrayOf(), "CPNYID", "DEPT")
                    if (cpnyId.isBlank()) cpnyId = extractInputValueByName(makeupHtml, "CPNYID").orEmpty()
                    if (deptNo.isBlank()) deptNo = extractInputValueByName(makeupHtml, "DEPT").orEmpty()
                }
            }
            if (cpnyId.isBlank()) cpnyId = "lafresh"

            Result.success(
                LeavePageInfo(
                    empId = htmlEmpId,
                    empName = empName,
                    empEngName = empEngName,
                    deptNo = deptNo,
                    cpnyId = cpnyId,
                    enc = addEnc,
                    vacationSummaries = vacationSummaries
                )
            )
        } catch (e: IOException) {
            Result.failure(Exception("載入請假單失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVacationSummaries(html: String): List<String> {
        val spanMatcher = Pattern.compile("""id=["']?VACATION_INFO["']?[^>]*>(.*?)</span>""", Pattern.DOTALL).matcher(html)
        val tableHtml = if (spanMatcher.find()) {
            spanMatcher.group(1).orEmpty()
        } else {
            unescapeHtmlEntities(extractInputValueByName(html, "VACATION_INFO2").orEmpty())
        }
        if (tableHtml.isBlank()) return emptyList()

        val rows = mutableListOf<String>()
        val trMatcher = Pattern.compile("""<tr[^>]*>(.*?)</tr>""", Pattern.DOTALL).matcher(tableHtml)
        val tdPattern = Pattern.compile("""<td[^>]*>(.*?)</td>""", Pattern.DOTALL)
        while (trMatcher.find()) {
            val rowContent = trMatcher.group(1).orEmpty()
            val cells = mutableListOf<String>()
            val tdMatcher = tdPattern.matcher(rowContent)
            while (tdMatcher.find()) {
                val cellText = unescapeHtmlEntities(
                    tdMatcher.group(1).orEmpty().replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
                )
                if (cellText.isNotBlank()) {
                    cells.add(cellText)
                }
            }
            if (cells.isNotEmpty()) {
                val joined = cells.joinToString(" ")
                    .replace(" 小時", "小時")
                    .replace("小時 ", "小時 · ")
                rows.add(joined)
            }
        }
        return rows
    }

    suspend fun prepareLeaveConfirmation(
        pageInfo: LeavePageInfo?,
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
    ): Result<LeaveConfirmData> = withContext(Dispatchers.IO) {
        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var currentCookie = storage.cookie.first().trim()
        var usedFreshLogin = false

        if (currentCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定 -> 打卡驗證資訊] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("自動登入失敗"))
            }
            currentCookie = loginResult.getOrThrow().first
            usedFreshLogin = true
        }

        val activeInfo = pageInfo ?: run {
            val infoRes = executeGetLeavePageInfo(currentCookie, empId)
            if (infoRes.isFailure && infoRes.exceptionOrNull() is AuthExpiredException && !usedFreshLogin && empId.isNotBlank() && pass.isNotBlank()) {
                val loginResult = loginAndFetchCredentials(empId, pass)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("重新登入失敗"))
                }
                currentCookie = loginResult.getOrThrow().first
                usedFreshLogin = true
                executeGetLeavePageInfo(currentCookie, empId).getOrElse {
                    return@withContext Result.failure(it)
                }
            } else {
                infoRes.getOrElse { return@withContext Result.failure(it) }
            }
        }

        val firstAttempt = executePrepareLeaveConfirmation(
            cookie = currentCookie,
            info = activeInfo,
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
        )
        if (firstAttempt.isSuccess || usedFreshLogin) {
            return@withContext firstAttempt
        }
        if (firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            Log.d("ClockRepository", "Cookie expired on prepareLeaveConfirmation, attempting auto re-login...")
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                val newCookie = loginResult.getOrThrow().first
                val refreshedInfo = executeGetLeavePageInfo(newCookie, empId).getOrNull() ?: activeInfo
                return@withContext executePrepareLeaveConfirmation(
                    cookie = newCookie,
                    info = refreshedInfo,
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
                )
            }
        }
        return@withContext firstAttempt
    }

    private fun extractLeaveServerMessage(body: String): String {
        val decoded = unescapeHtmlEntities(body)
        val messageMatch = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL).matcher(decoded)
        if (messageMatch.find()) {
            return messageMatch.group(1)?.trim().orEmpty()
        }
        val scriptMsgMatch = Pattern.compile("""(?:alert|showMessage|MessageBox\.show)\s*\(\s*(?:\{[^}]*msg\s*:\s*)?['"]([^'"]+)['"]""").matcher(decoded)
        if (scriptMsgMatch.find()) {
            return scriptMsgMatch.group(1)?.trim().orEmpty()
        }
        return decoded.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun executePrepareLeaveConfirmation(
        cookie: String,
        info: LeavePageInfo,
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
    ): Result<LeaveConfirmData> {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val refererUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B2.1.%E8%AB%8B%E5%81%87%E5%96%AE"
        val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        val sDateStr = startDate.format(dateFmt)
        val eDateStr = endDate.format(dateFmt)
        val specDateStr = specDate?.format(dateFmt).orEmpty()

        fun buildStepFormBody(
            encValue: String,
            buttonId: String,
            buttonLink: String,
            fromLink: String,
            emStep: String,
            amt: String = "",
            amt0: String = "",
            amt1: String = "",
            unit: String = "",
            cpnyId: String = info.cpnyId,
            deptNo: String = info.deptNo,
            hecName: String = info.empName
        ): FormBody {
            val builder = FormBody.Builder()
                .add("locale", "TW")
                .add("FUNCTION_NAME", "B2.1.請假單")
                .add("act", "")
                .add("init_func", "")
                .add("flow_approve", "")
                .add("buttonid", buttonId)
                .add("buttonlink", buttonLink)
                .add("fromlink", fromLink)
                .add("table_data", "")
                .add("form_target", "")
                .add("menu_display_flag", "")
                .add("em_step", emStep)
            if (emStep == "ajax") {
                builder.add("encodeURIComponent", "1")
            }
            return builder
                .add("em_POSITION", "1")
                .add("file", "hrm8w.pkg,hrm8bw.pkg")
                .add("enc", encValue)
                .add("HR_JUMP", "Y")
                .add("REPORTS1", "")
                .add("bTYPE", "")
                .add("bPLACE", "")
                .add("SET14", "")
                .add("UNIT", unit)
                .add("AMT", amt)
                .add("AMT1", amt1)
                .add("AMT0", amt0)
                .add("DEPT", deptNo)
                .add("CPNYID", cpnyId)
                .add("CPNYID1", cpnyId)
                .add("DEPT1", deptNo)
                .add("AGENTS", "")
                .add("PYN", "")
                .add("REPORTS", "")
                .add("EMPID", info.empId)
                .add("HECNAME", hecName)
                .add("HEENAME", info.empEngName)
                .add("AGENT", agentEmpId.trim())
                .add("HCODE", leaveCode)
                .add("FUNERAL_NAME", "")
                .add("PLANE", "")
                .add("SDATE", sDateStr)
                .add("EDATE", eDateStr)
                .add("PLACE", "")
                .add("STIME", startTimeHHmm)
                .add("ETIME", endTimeHHmm)
                .add("SPECDATE", specDateStr)
                .add("DEADLINE", "")
                .add("DEADLINEDAYS", "")
                .add("DGPA_TYPE", "")
                .add("DGPA_PNO", "")
                .add("HYN", "")
                .add("FUNCTION", "")
                .add("NOTE1", overdueReason.trim())
                .add("NOTE", note.trim())
                .build()
        }

        return try {
            // Step 1: button1 validation + hour calculation
            val step1Req = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .header("Referer", refererUrl)
                .header("User-Agent", userAgent)
                .post(
                    buildStepFormBody(
                        encValue = info.enc,
                        buttonId = "button1",
                        buttonLink = "Ajax(No refresh Browser)",
                        fromLink = "ADD",
                        emStep = "ajax"
                    )
                )
                .build()

            val step1Res = client.newCall(step1Req).execute()
            val step1Body = decodeResponseBody(step1Res.body?.bytes() ?: byteArrayOf(), "toADD1", "Message")
            Log.d("ClockRepository", "prepareLeave step1(button1): $step1Body")

            if (looksLikeLoginOrExpiredPage(step1Body) || step1Res.code == 401 || step1Res.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            if (!step1Body.contains("toADD1")) {
                val msg = extractLeaveServerMessage(step1Body)
                return Result.failure(Exception(msg.ifBlank { "請假單檢核未通過，請確認填寫資料" }))
            }

            val calcAmt = extractXmlSetValue(step1Body, "AMT").orEmpty()
            val calcAmt0 = extractXmlSetValue(step1Body, "AMT0").orEmpty()
            val calcAmt1 = extractXmlSetValue(step1Body, "AMT1").orEmpty()
            val calcUnit = extractXmlSetValue(step1Body, "UNIT").orEmpty()
            val calcCpny = extractXmlSetValue(step1Body, "CPNYID")?.takeIf { it.isNotBlank() } ?: info.cpnyId
            val calcDept = extractXmlSetValue(step1Body, "DEPT")?.takeIf { it.isNotBlank() } ?: info.deptNo
            val calcName = extractXmlSetValue(step1Body, "HECNAME")?.takeIf { it.isNotBlank() } ?: info.empName
            val step2Enc = extractXmlSetValue(step1Body, "enc")?.takeIf { it.isNotBlank() } ?: info.enc

            // Step 2: transition to ADD1 confirmation page
            val step2Req = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .header("Referer", refererUrl)
                .header("User-Agent", userAgent)
                .post(
                    buildStepFormBody(
                        encValue = step2Enc,
                        buttonId = "toADD1",
                        buttonLink = "ADD1",
                        fromLink = "ADD",
                        emStep = "0",
                        amt = calcAmt,
                        amt0 = calcAmt0,
                        amt1 = calcAmt1,
                        unit = calcUnit,
                        cpnyId = calcCpny,
                        deptNo = calcDept,
                        hecName = calcName
                    )
                )
                .build()

            val step2Res = client.newCall(step2Req).execute()
            val add1Html = decodeResponseBody(step2Res.body?.bytes() ?: byteArrayOf(), "ADD1", "enc")
            Log.d("ClockRepository", "prepareLeave step2(toADD1) length=${add1Html.length}")

            if (looksLikeLoginOrExpiredPage(add1Html) || step2Res.code == 401 || step2Res.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val add1Enc = extractEncFromHtml(add1Html) ?: step2Enc
            val add1Inputs = linkedMapOf<String, String>()
            val inputPattern = Pattern.compile("""<input[^>]*name=["']?([A-Za-z0-9_]+)["']?[^>]*>""", Pattern.CASE_INSENSITIVE)
            val inputMatcher = inputPattern.matcher(add1Html)
            while (inputMatcher.find()) {
                val tag = inputMatcher.group(0).orEmpty()
                val name = inputMatcher.group(1).orEmpty()
                if (name.isBlank() || add1Inputs.containsKey(name)) continue
                val valMatch = Pattern.compile("""value=["']([^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(tag)
                val value = if (valMatch.find()) unescapeHtmlEntities(valMatch.group(1).orEmpty()) else ""
                add1Inputs[name] = value
            }

            Result.success(
                LeaveConfirmData(
                    empId = info.empId,
                    empName = calcName,
                    empEngName = info.empEngName,
                    cpnyId = calcCpny,
                    deptNo = calcDept,
                    agentEmpId = agentEmpId.trim(),
                    agentDisplay = agentDisplay,
                    leaveCode = leaveCode,
                    leaveLabel = leaveLabel,
                    startDate = startDate,
                    endDate = endDate,
                    startTimeHHmm = startTimeHHmm,
                    endTimeHHmm = endTimeHHmm,
                    specDate = specDate,
                    overdueReason = overdueReason.trim(),
                    note = note.trim(),
                    amt = calcAmt,
                    amt0 = calcAmt0,
                    amt1 = calcAmt1,
                    unit = calcUnit,
                    add1Enc = add1Enc,
                    add1Inputs = add1Inputs
                )
            )
        } catch (e: IOException) {
            Result.failure(Exception("檢核請假單失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmLeaveApplication(
        confirmData: LeaveConfirmData,
        attachments: List<LeaveAttachment>
    ): Result<String> = withContext(Dispatchers.IO) {
        val empId = storage.empId.first().trim()
        val pass = storage.password.first().trim()
        var currentCookie = storage.cookie.first().trim()
        if (currentCookie.isBlank()) {
            if (empId.isBlank() || pass.isBlank()) {
                return@withContext Result.failure(Exception("請先至 [系統設定 -> 打卡驗證資訊] 填寫工號與密碼"))
            }
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isFailure) {
                return@withContext Result.failure(loginResult.exceptionOrNull() ?: Exception("自動登入失敗"))
            }
            currentCookie = loginResult.getOrThrow().first
        }

        val firstAttempt = executeConfirmLeaveApplication(currentCookie, confirmData, attachments)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull() is AuthExpiredException && empId.isNotBlank() && pass.isNotBlank()) {
            val loginResult = loginAndFetchCredentials(empId, pass)
            if (loginResult.isSuccess) {
                return@withContext executeConfirmLeaveApplication(loginResult.getOrThrow().first, confirmData, attachments)
            }
        }
        return@withContext firstAttempt
    }

    private fun executeConfirmLeaveApplication(
        cookie: String,
        confirmData: LeaveConfirmData,
        attachments: List<LeaveAttachment>
    ): Result<String> {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val refererUrl = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B2.1.%E8%AB%8B%E5%81%87%E5%96%AE"
        val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

        val mergedFields = linkedMapOf<String, String>()
        mergedFields.putAll(confirmData.add1Inputs)
        mergedFields["locale"] = "TW"
        mergedFields["FUNCTION_NAME"] = "B2.1.請假單"
        mergedFields["buttonid"] = "confirm"
        mergedFields["buttonlink"] = "Ajax(No refresh Browser)"
        mergedFields["fromlink"] = "ADD1"
        mergedFields["em_step"] = "ajax"
        mergedFields["encodeURIComponent"] = "1"
        mergedFields["em_POSITION"] = "1"
        mergedFields["file"] = "hrm8w.pkg,hrm8bw.pkg"
        mergedFields["enc"] = confirmData.add1Enc
        mergedFields["CPNYID"] = confirmData.cpnyId
        mergedFields["DEPT"] = confirmData.deptNo
        mergedFields["EMPID"] = confirmData.empId
        mergedFields["HECNAME"] = confirmData.empName
        mergedFields["HEENAME"] = confirmData.empEngName
        mergedFields["AGENT"] = confirmData.agentEmpId
        mergedFields["HCODE"] = confirmData.leaveCode
        mergedFields["SDATE"] = confirmData.startDate.format(dateFmt)
        mergedFields["EDATE"] = confirmData.endDate.format(dateFmt)
        mergedFields["STIME"] = confirmData.startTimeHHmm
        mergedFields["ETIME"] = confirmData.endTimeHHmm
        mergedFields["SPECDATE"] = confirmData.specDate?.format(dateFmt).orEmpty()
        mergedFields["AMT"] = confirmData.amt
        mergedFields["AMT0"] = confirmData.amt0
        mergedFields["AMT1"] = confirmData.amt1
        mergedFields["UNIT"] = confirmData.unit
        mergedFields["NOTE1"] = confirmData.overdueReason
        mergedFields["NOTE"] = confirmData.note
        if (!mergedFields.containsKey("HYN")) mergedFields["HYN"] = "N"
        if (!mergedFields.containsKey("REPORTS")) mergedFields["REPORTS"] = "0"

        val uploadFieldNames = listOf("UPLOAD_FILE", "UPLOAD_FILE2", "UPLOAD_FILE3", "UPLOAD_FILE4", "UPLOAD_FILE5")
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        mergedFields.forEach { (key, value) ->
            if (key !in uploadFieldNames) {
                multipartBuilder.addFormDataPart(key, value)
            }
        }
        uploadFieldNames.forEachIndexed { idx, fieldName ->
            val attachment = attachments.getOrNull(idx)
            if (attachment != null) {
                val mediaType = attachment.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
                multipartBuilder.addFormDataPart(
                    fieldName,
                    attachment.fileName,
                    attachment.bytes.toRequestBody(mediaType)
                )
            } else {
                multipartBuilder.addFormDataPart(
                    fieldName,
                    "",
                    ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
            }
        }

        return try {
            val confirmReq = Request.Builder()
                .url("https://hr.wixtar.com/servlet/jform")
                .header("Cookie", cookie)
                .header("Referer", refererUrl)
                .header("User-Agent", userAgent)
                .post(multipartBuilder.build())
                .build()

            val confirmRes = client.newCall(confirmReq).execute()
            val confirmBody = decodeResponseBody(confirmRes.body?.bytes() ?: byteArrayOf(), "Message", "完成")
            Log.d("ClockRepository", "confirmLeave response: $confirmBody")

            if (looksLikeLoginOrExpiredPage(confirmBody) || confirmRes.code == 401 || confirmRes.code == 403) {
                return Result.failure(AuthExpiredException("Cookie 已失效，需要重新登入"))
            }

            val serverMessage = extractLeaveServerMessage(confirmBody)
            val isSuccess = (serverMessage.contains("完成") || serverMessage.contains("成功")) &&
                !serverMessage.contains("重複") &&
                !serverMessage.contains("失敗") &&
                !serverMessage.contains("不可") &&
                !serverMessage.contains("異常") &&
                !serverMessage.contains("錯誤")

            if (isSuccess) {
                Result.success(serverMessage.ifBlank { "請假單新增完成且進入流程等待簽核" })
            } else {
                Result.failure(Exception(serverMessage.ifBlank { "請假單送出失敗，伺服器未回傳詳細訊息" }))
            }
        } catch (e: IOException) {
            Result.failure(Exception("送出請假單失敗：網路連線異常 (${e.message})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
