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
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("action", "PUNCH_CARD")
            }

            val shortcut = ShortcutInfoCompat.Builder(context, "punch_card_dynamic_shortcut")
                .setShortLabel("WiXtar 打卡")
                .setLongLabel("線上簽到與簽退打卡")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_cloud_clock))
                .setIntent(intent)
                .addCapabilityBinding(
                    "actions.intent.OPEN_APP_FEATURE",
                    "feature",
                    listOf("打卡", "簽到", "簽退")
                )
                .addCapabilityBinding(
                    "custom.actions.intent.PUNCH_CARD",
                    "action",
                    listOf("PUNCH_CARD")
                )
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
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

                val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "punch_card_dynamic_shortcut")
                    .setShortLabel("WiXtar 打卡")
                    .setLongLabel("線上簽到與簽退打卡")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_cloud_clock))
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
