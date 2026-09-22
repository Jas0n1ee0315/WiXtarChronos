package com.jas0n1ee.wixtar.chronoswear.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.jas0n1ee.wixtar.chronoswear.presentation.screens.WearMainScreen

class MainActivity : ComponentActivity() {
    private val viewModel: WearClockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Requirement 19: Keep screen awake while watch app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                AppScaffold {
                    WearMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
