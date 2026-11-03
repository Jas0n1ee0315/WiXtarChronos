package com.jas0n1ee.wixtar.chronoswear.tile

import android.R
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jas0n1ee.wixtar.chronos.data.AppStorage
import com.jas0n1ee.wixtar.chronos.data.ClockRepository
import com.jas0n1ee.wixtar.chronos.data.WearableSyncManager
import com.jas0n1ee.wixtar.chronoswear.notification.NotificationHelper
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
            try {
                getUpdater(context).requestUpdate(MainTileService::class.java)
            } catch (e: Exception) {
                Log.e("MainTileService", "Failed to force update Tile", e)
            }
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return serviceScope.future {
            val storage = AppStorage(applicationContext)
            val repository = ClockRepository(storage)
            val notificationHelper = NotificationHelper(applicationContext)

            storage.clearClockDataIfExpired()

            val lastClickId = requestParams.currentState.lastClickableId
            Log.d("MainTileService", "onTileRequest triggered, lastClickableId=$lastClickId")

            val isNet = isNetworkAvailable(applicationContext)
            val isPhone = checkPhoneConnected(applicationContext)

            if (lastClickId == "PUNCH_CLICK") {
                val currentIn = storage.clockInTime.first()
                val currentOut = storage.clockOutTime.first()
                val isClockingIn = currentIn.isNullOrBlank()
                val typeStr = if (isClockingIn) "上班卡" else "下班卡"

                val empId = storage.empId.first()
                val pass = storage.password.first()
                val cookie = storage.cookie.first()
                val enc = storage.enc.first()
                val gps = storage.gpsLocation.first()

                if (hasValidInOrOutToPunch(currentIn, currentOut)) {
                    if (isNet && empId.isNotBlank() && pass.isNotBlank()) {
                        val result = repository.clockIn(empId, pass, cookie, enc, gps)
                        if (result.isSuccess) {
                            val time = result.getOrNull() ?: ""
                            if (isClockingIn) {
                                storage.saveClockInWithSource(time, "雲端")
                            } else {
                                storage.saveClockOutWithSource(time, "雲端")
                            }
                            notificationHelper.showPunchSuccessNotification(typeStr, time)
                        } else if (isPhone) {
                            WearableSyncManager.sendActionToNodes(applicationContext, if (isClockingIn) "CLOCK_IN" else "CLOCK_OUT")
                        }
                    } else if (isPhone) {
                        WearableSyncManager.sendActionToNodes(applicationContext, if (isClockingIn) "CLOCK_IN" else "CLOCK_OUT")
                    }
                }
            }

            val isHrNormal = if (isNet) {
                repository.checkHrSystemHealth()
            } else {
                true
            }

            val clockIn = storage.clockInTime.first()
            val clockOut = storage.clockOutTime.first()
            val inSource = storage.clockInSource.first()
            val outSource = storage.clockOutSource.first()

            buildTile(applicationContext, isHrNormal, clockIn, clockOut, inSource, outSource)
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }

    private fun hasValidInOrOutToPunch(inTime: String?, outTime: String?): Boolean {
        return inTime.isNullOrBlank() || outTime.isNullOrBlank() || !outTime.isNullOrBlank()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkPhoneConnected(context: Context): Boolean {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = Tasks.await(nodeClient.connectedNodes)
            nodes.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}

private fun getPrimaryColor(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val c = context.getColor(R.color.system_accent1_300)
            if (c != 0) c else 0xFFD0BCFF.toInt()
        } catch (_: Exception) {
            0xFFD0BCFF.toInt()
        }
    } else {
        0xFFD0BCFF.toInt()
    }
}

private fun getPrimaryContainerColor(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val c = context.getColor(R.color.system_accent1_200)
            if (c != 0) c else 0xFFD0BCFF.toInt()
        } catch (_: Exception) {
            0xFFD0BCFF.toInt()
        }
    } else {
        0xFFD0BCFF.toInt()
    }
}

private fun getOnPrimaryContainerColor(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val c = context.getColor(R.color.system_accent1_900)
            if (c != 0) c else 0xFF21005D.toInt()
        } catch (_: Exception) {
            0xFF21005D.toInt()
        }
    } else {
        0xFF21005D.toInt()
    }
}

private fun getSecondaryColor(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val c = context.getColor(R.color.system_accent2_300)
            if (c != 0) c else 0xFFE8DEF8.toInt()
        } catch (_: Exception) {
            0xFFE8DEF8.toInt()
        }
    } else {
        0xFFE8DEF8.toInt()
    }
}

private fun getCardBackgroundColor(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val c = context.getColor(R.color.system_neutral1_800)
            if (c != 0) c else 0xFF382A4C.toInt()
        } catch (_: Exception) {
            0xFF382A4C.toInt()
        }
    } else {
        0xFF382A4C.toInt()
    }
}

private fun calculateOffWork(clockIn: String?): String {
    if (clockIn.isNullOrBlank()) return "--:--"
    val raw = clockIn.substringBefore(" ")
    return try {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val time = LocalTime.parse(raw.take(5), formatter)
        time.plusHours(9).format(formatter)
    } catch (_: Exception) {
        "--:--"
    }
}

private fun createTileText(
    text: String,
    sizeSp: Float,
    colorArgb: Int,
    isBold: Boolean = false
): LayoutElementBuilders.Text {
    val styleBuilder = LayoutElementBuilders.FontStyle.Builder()
        .setSize(DimensionBuilders.sp(sizeSp))
        .setColor(argb(colorArgb))

    if (isBold) {
        styleBuilder.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
    }

    return LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(styleBuilder.build())
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .build()
}

private fun createTileEdgeButton(
    text: String,
    bgColorArgb: Int,
    textColorArgb: Int,
    clickableId: String
): LayoutElementBuilders.LayoutElement {
    val clickable = ModifiersBuilders.Clickable.Builder()
        .setId(clickableId)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    val textElement = LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                .setSize(DimensionBuilders.sp(15f))
                .setColor(argb(textColorArgb))
                .build()
        )
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .build()

    return LayoutElementBuilders.Box.Builder()
        .addContent(textElement)
        .setWidth(dp(168f))
        .setHeight(dp(48f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable)
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(bgColorArgb))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(24f))
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()
}

private fun buildTile(
    context: Context,
    isHrNormal: Boolean,
    clockIn: String?,
    clockOut: String?,
    inSource: String,
    outSource: String
): TileBuilders.Tile {
    val rootColumn = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

    val primaryColor = getPrimaryColor(context)
    val secondaryColor = getSecondaryColor(context)
    val buttonBgColor = getPrimaryContainerColor(context)
    val buttonTextColor = getOnPrimaryContainerColor(context)
    val cardBgColor = getCardBackgroundColor(context)

    // Center Card Container (M3 Container matching App UI)
    val cardColumn = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

    if (!isHrNormal) {
        // HR System Error State (Requirement 17)
        cardColumn.addContent(createTileText("HR系統目前異常\n再點擊重試", 13f, 0xFFFF5252.toInt(), isBold = true))
    } else {
        // Normal HR State
        if (clockIn.isNullOrBlank()) {
            cardColumn.addContent(createTileText("今日尚無打卡", 16f, 0xFFFFFFFF.toInt(), isBold = true))
        } else if (clockOut.isNullOrBlank()) {
            cardColumn.addContent(createTileText("上班時間", 12f, secondaryColor))
            cardColumn.addContent(createTileText("$clockIn ($inSource)", 14f, 0xFFFFFFFF.toInt(), isBold = true))
            cardColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            cardColumn.addContent(createTileText("預計可下班時間", 12f, secondaryColor))
            cardColumn.addContent(createTileText(calculateOffWork(clockIn), 16f, primaryColor, isBold = true))
        } else {
            cardColumn.addContent(createTileText("上班時間", 12f, secondaryColor))
            cardColumn.addContent(createTileText("$clockIn ($inSource)", 14f, 0xFFFFFFFF.toInt(), isBold = true))
            cardColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            cardColumn.addContent(createTileText("下班時間", 12f, secondaryColor))
            cardColumn.addContent(createTileText("$clockOut ($outSource)", 14f, 0xFFFFFFFF.toInt(), isBold = true))
        }
    }

    val cardBox = LayoutElementBuilders.Box.Builder()
        .addContent(cardColumn.build())
        .setWidth(dp(168f))
        .setHeight(dp(92f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(cardBgColor))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(24f))
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()

    val normalButtonText = when {
        clockIn.isNullOrBlank() -> "打上班卡"
        clockOut.isNullOrBlank() -> "打下班卡"
        else -> "再打下班卡"
    }

    rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
    rootColumn.addContent(cardBox)
    rootColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())

    if (!isHrNormal) {
        rootColumn.addContent(createTileEdgeButton("重試打卡", 0xFFC62828.toInt(), 0xFFFFFFFF.toInt(), "RETRY_CLICK"))
    } else {
        rootColumn.addContent(createTileEdgeButton(normalButtonText, buttonBgColor, buttonTextColor, "PUNCH_CLICK"))
    }

    val layout = LayoutElementBuilders.Layout.Builder()
        .setRoot(rootColumn.build())
        .build()

    val timeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(layout)
                .build()
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion("1")
        .setTileTimeline(timeline)
        .build()
}
