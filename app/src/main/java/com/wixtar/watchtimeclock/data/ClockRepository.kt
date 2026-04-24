package com.wixtar.watchtimeclock.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

class ClockRepository(private val storage: AppStorage) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    suspend fun clockIn(
        empId: String,
        pass: String,
        cookie: String,
        enc: String,
        gps: String
    ): Result<String> = withContext(Dispatchers.IO) {
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

            if (body.contains("打卡成功")) {
                val timeMatch = Pattern.compile("時間 (\\d{4})").matcher(body)
                val timeStr = if (timeMatch.find()) {
                    val raw = timeMatch.group(1)
                    if (raw != null && raw.length >= 4) {
                        "${raw.substring(0, 2)}:${raw.substring(2, 4)}"
                    } else {
                        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    }
                } else {
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }
                Result.success<String>(timeStr)
            } else {
                val messageMatch = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL).matcher(body)
                val errorMessage = if (messageMatch.find()) {
                    messageMatch.group(1)?.trim() ?: "未知錯誤"
                } else {
                    "打卡失敗: " + body.replace(Regex("<[^>]*>"), "").take(30).trim()
                }
                Result.failure<String>(Exception(errorMessage))
            }
        } catch (e: IOException) {
            Result.failure<String>(e)
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

            // 1. 嘗試用 JSON 陣列解析 (正式做法)
            val dataInfoPattern = Pattern.compile("data-info=['\"](.*?)['\"]", Pattern.DOTALL)
            val matcher = dataInfoPattern.matcher(body)

            var foundInTime: String? = null
            var foundOutTime: String? = null
            var lastJsonStr = ""

            while (matcher.find()) {
                var jsonStr = matcher.group(1) ?: continue
                // 處理 HTML 轉義
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
                // 如果已經同時抓到上班和下班時間，就可以提早結束迴圈
                if (foundInTime != null && foundOutTime != null) break
            }

            // 2. 備案：如果 JSON 解析沒抓到，直接在全文搜尋關鍵字 (暴力做法但最穩)
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
