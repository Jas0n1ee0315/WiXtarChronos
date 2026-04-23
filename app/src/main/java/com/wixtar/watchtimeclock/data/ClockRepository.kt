package com.wixtar.watchtimeclock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
            .add("FUNCTION_NAME", "B3.8.線上簽到簽退") // 根據 Python 程式碼，這裡有點號
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
            
            // 優先依照 Python 邏輯使用 UTF-8
            var body = String(responseBytes, Charsets.UTF_8)
            
            // 如果 UTF-8 看起來像亂碼或不含關鍵字，才嘗試 Big5 (相容性考慮)
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
                Result.success(timeStr)
            } else {
                val messageMatch = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL).matcher(body)
                val errorMessage = if (messageMatch.find()) {
                    messageMatch.group(1)?.trim() ?: "未知錯誤"
                } else {
                    // 若找不到 Message 標籤，顯示簡短內容供辨識
                    "打卡失敗: " + body.replace(Regex("<[^>]*>"), "").take(30).trim()
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
