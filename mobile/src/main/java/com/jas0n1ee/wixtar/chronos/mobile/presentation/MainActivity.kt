package com.jas0n1ee.wixtar.chronos.mobile.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.jas0n1ee.wixtar.chronos.mobile.presentation.theme.WiXtarCompanionTheme
import com.jas0n1ee.wixtar.chronos.mobile.shortcut.AssistantShortcutManager

class MainActivity : ComponentActivity() {
    private val viewModel: MobileClockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AssistantShortcutManager.pushDynamicShortcut(this)
        handleActionIntent(intent)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }
            }

            WiXtarCompanionTheme(dynamicColor = true) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkConnectedWearables()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleActionIntent(intent)
    }

    private fun handleActionIntent(intent: Intent?) {
        val actionExtra = intent?.getStringExtra("action")
        if (actionExtra == "PUNCH_CARD") {
            intent.removeExtra("action")
            viewModel.doClockAction()
        }
    }
}
