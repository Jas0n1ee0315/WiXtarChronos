package com.jas0n1ee.wixtar.chronos.watch.tile

import android.content.Context
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        fun forceUpdate(context: Context) {
            getUpdater(context).requestUpdate(MainTileService::class.java)
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return serviceScope.future {
            val storage = AppStorage(applicationContext)
            val clockIn = storage.clockInTime.first()
            val clockOut = storage.clockOutTime.first()
            tile(requestParams, applicationContext, clockIn, clockOut)
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }
}

private fun calculateOffWork(clockIn: String?): String {
    if (clockIn.isNullOrBlank()) return "--:--"
    val raw = clockIn.substringBefore(" ")
    return try {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val time = LocalTime.parse(raw.take(5), formatter)
        time.plusHours(9).format(formatter)
    } catch (e: Exception) {
        "--:--"
    }
}

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    clockIn: String?,
    clockOut: String?
): TileBuilders.Tile {
    val layout = LayoutElementBuilders.Column.Builder()
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("WiXtar 打卡")
                .build()
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("上班: ${clockIn ?: "--:--"}")
                .build()
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("可下班: ${calculateOffWork(clockIn)}")
                .build()
        )
        .build()

    val timeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(layout)
                        .build()
                )
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion("1")
        .setTileTimeline(timeline)
        .build()
}
