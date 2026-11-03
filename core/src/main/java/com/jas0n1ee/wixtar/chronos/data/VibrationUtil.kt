package com.jas0n1ee.wixtar.chronos.data

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

object VibrationUtil {
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrate(context: Context, durationMs: Long = 100L) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateClick(context: Context) {
        vibrate(context, 50L)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateShortSuccess(context: Context) {
        vibrate(context, 100L)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateLongFailure(context: Context) {
        vibrate(context, 2000L)
    }
}
