package com.jas0n1ee.wixtar.chronos.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class TaiwanCalendarRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Checks whether the specified date is a normal working day in Taiwan.
     * Returns Result.success(true) if it's a working day (是否放假 == "0").
     * Returns Result.success(false) if it's a holiday / weekend (是否放假 == "2" or "1").
     * Returns Result.failure(Exception) if the API request failed or internet is unavailable.
     */
    suspend fun isWorkDay(date: LocalDate = LocalDate.now()): Result<Boolean> = withContext(Dispatchers.IO) {
        val year = date.year
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val urls = listOf(
            "https://raw.githubusercontent.com/880831ian/taiwan-calendar/master/data/$year.json",
            "https://cdn.jsdelivr.net/gh/880831ian/taiwan-calendar/data/$year.json"
        )

        var lastException: Exception? = null

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val jsonArray = JSONArray(body)
                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.optJSONObject(i) ?: continue
                            val itemDate = item.optString("西元日期")
                            if (itemDate == dateStr) {
                                val isHolidayStr = item.optString("是否放假")
                                // "0" means working day. "2" or "1" means holiday/off day.
                                val isWorkDay = (isHolidayStr == "0")
                                Log.d("TaiwanCalendarRepo", "Date $dateStr isWorkDay=$isWorkDay (是否放假=$isHolidayStr, 備註=${item.optString("備註")})")
                                return@withContext Result.success(isWorkDay)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaiwanCalendarRepo", "Failed to fetch calendar from $url", e)
                lastException = e
            }
        }

        Result.failure(lastException ?: Exception("無法取得國定假日資料"))
    }
}
