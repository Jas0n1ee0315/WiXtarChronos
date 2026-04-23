package com.wixtar.watchtimeclock.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography.TITLE_MEDIUM
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.wixtar.watchtimeclock.R
import com.wixtar.watchtimeclock.data.AppStorage
import com.wixtar.watchtimeclock.presentation.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "0"

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val storage = AppStorage(this@MainTileService)
            val clockIn = storage.clockInTime.first()
            val clockOut = storage.clockOutTime.first()
            tile(requestParams, this@MainTileService, clockIn, clockOut)
        }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(resources(requestParams))
}

private fun resources(requestParams: ResourcesRequest): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    clockIn: String?,
    clockOut: String?
): TileBuilders.Tile {
    val statusText = when {
        !clockOut.isNullOrEmpty() -> "下班: $clockOut"
        !clockIn.isNullOrEmpty() -> "上班: $clockIn"
        else -> "尚未打卡"
    }

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        titleSlot = {
                            text(
                                "打卡助手".layoutString,
                                typography = TITLE_MEDIUM
                            )
                        },
                        mainSlot = {
                            textButton(
                                onClick = ModifiersBuilders.Clickable.Builder()
                                    .setId("open_app")
                                    .setOnClick(
                                        ActionBuilders.LaunchAction.Builder()
                                            .setAndroidActivity(
                                                ActionBuilders.AndroidActivity.Builder()
                                                    .setPackageName(context.packageName)
                                                    .setClassName(MainActivity::class.java.name)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build(),
                                labelContent = {
                                    text(statusText.layoutString)
                                }
                            )
                        }
                    )
                }
            )
        )
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    tile(it, context, "09:00", null)
}
