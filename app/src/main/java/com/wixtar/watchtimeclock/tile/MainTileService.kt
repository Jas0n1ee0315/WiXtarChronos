package com.wixtar.watchtimeclock.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wixtar.watchtimeclock.data.AppStorage
import com.wixtar.watchtimeclock.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val RESOURCES_VERSION = "0"

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return serviceScope.future {
            val storage = AppStorage(this@MainTileService)
            val clockIn = storage.clockInTime.first()
            val clockOut = storage.clockOutTime.first()
            tile(requestParams, this@MainTileService, clockIn, clockOut)
        }
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )

    companion object {
        fun forceUpdate(context: Context) {
            getUpdater(context).requestUpdate(MainTileService::class.java)
        }
    }
}

private fun calculateOffWork(clockIn: String?): String {
    if (clockIn.isNullOrBlank()) return "--:--"
    val raw = clockIn.substringBefore(" ")
    return try {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val time = LocalTime.parse(raw.take(5), formatter)
        time.plusHours(8).format(formatter)
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
    val offWork = calculateOffWork(clockIn)
    
    val buttonText = when {
        clockIn.isNullOrBlank() -> "打上班卡"
        clockOut.isNullOrBlank() -> "打下班卡"
        else -> "再次打下班卡"
    }

    val clockClickable = ModifiersBuilders.Clickable.Builder()
        .setId("auto_clock")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .addKeyToExtraMapping("EXTRA_AUTO_CLOCK", ActionBuilders.booleanExtra(true))
                        .build()
                )
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        titleSlot = {
                            text("打卡助手".layoutString, typography = TITLE_SMALL)
                        },
                        mainSlot = {
                            LayoutElementBuilders.Column.Builder()
                                .addContent(text("上班: ${clockIn ?: "無"}".layoutString, typography = BODY_MEDIUM))
                                .addContent(text("下班: ${clockOut ?: "無"}".layoutString, typography = BODY_MEDIUM))
                                .addContent(text("可下班: $offWork".layoutString, typography = BODY_MEDIUM))
                                .build()
                        },
                        bottomSlot = {
                            textEdgeButton(
                                onClick = clockClickable,
                                labelContent = { text(buttonText.layoutString) },
                                colors = filledButtonColors().copy(
                                    containerColor = 0xFFEADDFF.toInt().argb
                                )
                            )
                        }
                    )
                }
            )
        )
        .build()
}
