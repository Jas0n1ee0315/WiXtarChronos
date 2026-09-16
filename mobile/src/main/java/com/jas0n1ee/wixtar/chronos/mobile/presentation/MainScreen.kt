package com.jas0n1ee.wixtar.chronos.mobile.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.jas0n1ee.wixtar.chronos.mobile.R
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.CloudRecordsScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.MobileSettingsScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.PunchCardScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.WatchScreen

enum class MobileTab(val title: String) {
    PUNCH_CARD("簽到打卡"),
    CLOUD_RECORDS("雲端紀錄"),
    WATCH("手錶"),
    SETTINGS("系統設定")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MobileClockViewModel) {
    var selectedTab by remember { mutableStateOf(MobileTab.PUNCH_CARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "WiXtar 打卡",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == MobileTab.PUNCH_CARD,
                    onClick = { selectedTab = MobileTab.PUNCH_CARD },
                    icon = { Icon(painterResource(id = R.drawable.ic_cloud_clock), contentDescription = null) },
                    label = { Text(MobileTab.PUNCH_CARD.title) }
                )
                NavigationBarItem(
                    selected = selectedTab == MobileTab.CLOUD_RECORDS,
                    onClick = { selectedTab = MobileTab.CLOUD_RECORDS },
                    icon = { Icon(Icons.Default.CloudDone, contentDescription = null) },
                    label = { Text(MobileTab.CLOUD_RECORDS.title) }
                )
                NavigationBarItem(
                    selected = selectedTab == MobileTab.WATCH,
                    onClick = { selectedTab = MobileTab.WATCH },
                    icon = { Icon(Icons.Default.Watch, contentDescription = null) },
                    label = { Text(MobileTab.WATCH.title) }
                )
                NavigationBarItem(
                    selected = selectedTab == MobileTab.SETTINGS,
                    onClick = { selectedTab = MobileTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(MobileTab.SETTINGS.title) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (selectedTab) {
            MobileTab.PUNCH_CARD -> PunchCardScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            MobileTab.CLOUD_RECORDS -> CloudRecordsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            MobileTab.WATCH -> WatchScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            MobileTab.SETTINGS -> MobileSettingsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
        }
    }
}
