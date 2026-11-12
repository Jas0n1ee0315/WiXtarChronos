package com.jas0n1ee.wixtar.chronos.mobile.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.jas0n1ee.wixtar.chronos.mobile.R
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.AttendanceScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.LeaveApplicationScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.MakeupPunchScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.MobileSettingsScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.PunchCardScreen
import com.jas0n1ee.wixtar.chronos.mobile.presentation.screens.SettingsSubCategory

enum class MobileTab(val title: String) {
    PUNCH_CARD("線上打卡"),
    MAKEUP_PUNCH("補打卡"),
    LEAVE("請假單"),
    ATTENDANCE("出勤表")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MobileClockViewModel) {
    var selectedTab by remember { mutableStateOf(MobileTab.PUNCH_CARD) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsInitialCategory by remember { mutableStateOf(SettingsSubCategory.MAIN) }
    val snackbarHostState = remember { SnackbarHostState() }

    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val requestedTab by viewModel.requestedTab.collectAsState()

    LaunchedEffect(requestedTab) {
        val tabName = requestedTab ?: return@LaunchedEffect
        runCatching { MobileTab.valueOf(tabName) }.getOrNull()?.let { tab ->
            showSettings = false
            selectedTab = tab
        }
        viewModel.clearRequestedTab()
    }

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
            if (!showSettings) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = selectedTab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            settingsInitialCategory = SettingsSubCategory.MAIN
                            showSettings = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "系統設定"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (!showSettings) {
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
                        selected = selectedTab == MobileTab.MAKEUP_PUNCH,
                        onClick = { selectedTab = MobileTab.MAKEUP_PUNCH },
                        icon = { Icon(Icons.Default.EditCalendar, contentDescription = null) },
                        label = { Text(MobileTab.MAKEUP_PUNCH.title) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MobileTab.LEAVE,
                        onClick = { selectedTab = MobileTab.LEAVE },
                        icon = { Icon(Icons.Default.BeachAccess, contentDescription = null) },
                        label = { Text(MobileTab.LEAVE.title) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MobileTab.ATTENDANCE,
                        onClick = { selectedTab = MobileTab.ATTENDANCE },
                        icon = { Icon(Icons.Default.EventNote, contentDescription = null) },
                        label = { Text(MobileTab.ATTENDANCE.title) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (showSettings) {
            MobileSettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                initialSubCategory = settingsInitialCategory,
                onBack = { showSettings = false }
            )
        } else {
            when (selectedTab) {
                MobileTab.PUNCH_CARD -> PunchCardScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                MobileTab.MAKEUP_PUNCH -> MakeupPunchScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                MobileTab.LEAVE -> LeaveApplicationScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToAgentSettings = {
                        settingsInitialCategory = SettingsSubCategory.AGENT
                        showSettings = true
                    }
                )
                MobileTab.ATTENDANCE -> AttendanceScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
