package com.jas0n1ee.wixtar.chronos.watch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import kotlinx.coroutines.flow.first

class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("打卡", "WiXtar")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val storage = AppStorage(applicationContext)
        val clockIn = storage.clockInTime.first()
        val text = clockIn?.take(5) ?: "未打卡"
        return createComplicationData(text, "WiXtar")
    }

    private fun createComplicationData(text: String, title: String): ShortTextComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(title).build()
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .build()
    }
}
