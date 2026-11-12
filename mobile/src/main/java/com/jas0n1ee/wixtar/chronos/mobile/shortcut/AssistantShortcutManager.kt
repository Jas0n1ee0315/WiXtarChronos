package com.jas0n1ee.wixtar.chronos.mobile.shortcut

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jas0n1ee.wixtar.chronos.mobile.R
import com.jas0n1ee.wixtar.chronos.mobile.presentation.MainActivity

object AssistantShortcutManager {

    fun pushDynamicShortcut(context: Context) {
        try {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)

            fun buildIntent(actionValue: String) = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("action", actionValue)
            }

            val punchShortcut = ShortcutInfoCompat.Builder(context, "punch_card_shortcut")
                .setShortLabel("線上打卡")
                .setLongLabel("線上打卡")
                .setRank(0)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_cloud_clock))
                .setIntent(buildIntent("PUNCH_CARD"))
                .addCapabilityBinding(
                    "actions.intent.OPEN_APP_FEATURE",
                    "feature",
                    listOf("線上打卡", "打卡", "簽到", "簽退")
                )
                .addCapabilityBinding(
                    "custom.actions.intent.PUNCH_CARD",
                    "action",
                    listOf("PUNCH_CARD")
                )
                .build()

            val makeupShortcut = ShortcutInfoCompat.Builder(context, "makeup_punch_shortcut")
                .setShortLabel("補打卡")
                .setLongLabel("補打卡")
                .setRank(1)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_makeup_punch))
                .setIntent(buildIntent("MAKEUP_PUNCH"))
                .build()

            val leaveShortcut = ShortcutInfoCompat.Builder(context, "leave_shortcut")
                .setShortLabel("請假單")
                .setLongLabel("請假單")
                .setRank(2)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_leave))
                .setIntent(buildIntent("LEAVE"))
                .build()

            val attendanceShortcut = ShortcutInfoCompat.Builder(context, "attendance_shortcut")
                .setShortLabel("出勤表")
                .setLongLabel("出勤表")
                .setRank(3)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_attendance))
                .setIntent(buildIntent("ATTENDANCE"))
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(punchShortcut, makeupShortcut, leaveShortcut, attendanceShortcut)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openAssistantShortcutsPromo(context: Context) {
        pushDynamicShortcut(context)

        // 1. First try system level Pin / Add Shortcut request
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("action", "PUNCH_CARD")
                }

                val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "punch_card_shortcut")
                    .setShortLabel("線上打卡")
                    .setLongLabel("線上打卡")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_cloud_clock))
                    .setIntent(intent)
                    .addCapabilityBinding(
                        "actions.intent.OPEN_APP_FEATURE",
                        "feature",
                        listOf("打卡", "簽到", "簽退")
                    )
                    .build()

                ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to Google Assistant Settings
        val intents = listOf(
            Intent("com.google.android.googlequicksearchbox.action.ASSISTANT_SETTINGS").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.intent.action.ASSISTANT_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.VOICE_CONTROL_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        var launched = false
        for (intent in intents) {
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
            }
        }

        if (!launched) {
            Toast.makeText(context, "請對 Google 助理說：「Hey Google, 透過 WiXtar 打卡」", Toast.LENGTH_LONG).show()
        }
    }
}
