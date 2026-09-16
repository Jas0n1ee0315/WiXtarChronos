package com.jas0n1ee.wixtar.chronos.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

class ClockRepository(private val storage: AppStorage) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

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
        val trimmedCookie = cookie.trim()
        val trimmedEnc = enc.trim()
        val trimmedGps = gps.trim()

        if (trimmedId.isBlank() || trimmedPass.isBlank() || trimmedCookie.isBlank() || trimmedEnc.isBlank()) {
            return@withContext Result.failure(
                Exception("打卡失敗：請先至 [系統設定] 完整填寫工號、密碼、Cookie 及 enc")
            )
        }

        Log.d("ClockRepository", "clockIn called: empId=$trimmedId, passLength=${trimmedPass.length}, cookieLength=${trimmedCookie.length}, encLength=${trimmedEnc.length}, gps=$trimmedGps")
        val formBody = FormBody.Builder()
            .add("em_step", "ajax")
            .add("encodeURIComponent", "1")
            .add("locale", "TW")
            .add("FUNCTION_NAME", "B3.8.線上簽到簽退")
            .add("buttonid", "SIGN_IN_OUT")
            .add("buttonlink", "Ajax(No refresh Browser)")
            .add("em_POSITION", "1")
            .add("file", "hrm8w.pkg,hrm8bw.pkg")
            .add("enc", trimmedEnc)
            .add("ITEM", "A")
            .add("EMPID", trimmedId)
            .add("PASS", trimmedPass)
            .add("qCPNYID", "lafresh")
            .add("GPSADDR", trimmedGps)
            .build()

        val request = Request.Builder()
            .url("https://hr.wixtar.com/servlet/jform")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", trimmedCookie)
            .header("Referer", "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=B3.8.%E7%B7%9A%E4%B8%8A%E7%B0%BD%E5%88%B0%E7%B0%BD%E9%80%80")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBytes = response.body?.bytes() ?: byteArrayOf()
            
            var body = String(responseBytes, Charsets.UTF_8)
            
            if (!body.contains("打卡成功") && !body.contains("Message")) {
                val big5Body = String(responseBytes, Charset.forName("Big5"))
                if (big5Body.contains("打卡成功") || big5Body.contains("Message")) {
                    body = big5Body
                }
            }

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
                val errorMessage = if (messageMatch.find()) {
                    val msg = messageMatch.group(1)?.trim() ?: "未知錯誤"
                    "打卡失敗: $msg"
                } else {
                    val cleanText = body.replace(Regex("<[^>]*>"), "").trim()
                    if (cleanText.isNotBlank()) {
                        "打卡失敗: " + cleanText.take(50)
                    } else {
                        "打卡失敗：伺服器回應異常，請檢查 Cookie 或 enc 是否正確/過期"
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: IOException) {
            Log.e("ClockRepository", "clockIn exception", e)
            Result.failure(e)
        }
    }

    suspend fun getCloudClockInfo(cookie: String): Result<Triple<String?, String?, String>> = withContext(Dispatchers.IO) {
        val url = "https://hr.wixtar.com/servlet/jform?file=hrm8w.pkg,hrm8bw.pkg&locale=TW&init_func=A1.%E5%80%8B%E4%BA%BA%E8%B3%87%E8%A8%8A%E5%8D%80"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBytes = response.body?.bytes() ?: byteArrayOf()
            var body = String(responseBytes, Charsets.UTF_8)

            if (!body.contains("data-info")) {
                val big5Body = String(responseBytes, Charset.forName("Big5"))
                if (big5Body.contains("data-info")) {
                    body = big5Body
                }
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
                return@withContext Result.success(Triple(foundInTime, foundOutTime, lastJsonStr))
            } else {
                Log.e("ClockRepository", "完全找不到打卡資訊")
                Result.failure(Exception("在網頁中找不到您的打卡時間"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
