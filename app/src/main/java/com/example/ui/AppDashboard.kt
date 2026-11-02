package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AppDashboard(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf("scan") }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceBg,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == "scan",
                    onClick = { currentTab = "scan" },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Scan") },
                    label = { Text("اسکن (Scan)", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberGold,
                        selectedTextColor = CyberGold,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_scan")
                )
                NavigationBarItem(
                    selected = currentTab == "visualizer",
                    onClick = { currentTab = "visualizer" },
                    icon = { Icon(Icons.Default.ViewInAr, contentDescription = "3D") },
                    label = { Text("سه‌بعدی (3D)", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_visualizer")
                )
                NavigationBarItem(
                    selected = currentTab == "tracker",
                    onClick = { currentTab = "tracker" },
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Live") },
                    label = { Text("زنده (Live)", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberRed,
                        selectedTextColor = CyberRed,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_tracker")
                )
                NavigationBarItem(
                    selected = currentTab == "history",
                    onClick = { currentTab = "history" },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("تاریخچه (History)", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberGold,
                        selectedTextColor = CyberGold,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_history")
                )
                NavigationBarItem(
                    selected = currentTab == "ai",
                    onClick = { currentTab = "ai" },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    label = { Text("هوش مصنوعی (AI)", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberGold,
                        selectedTextColor = CyberGold,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_ai")
                )
            }
        },
        containerColor = DarkBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AmbientParticleBackground()

            when (currentTab) {
                "scan" -> ScanScreen(viewModel = viewModel, onNavigateToAi = { currentTab = "ai" })
                "visualizer" -> VisualizerScreen(viewModel = viewModel, onNavigateToAi = { currentTab = "ai" })
                "tracker" -> TrackerScreen(viewModel = viewModel)
                "history" -> HistoryScreen(viewModel = viewModel, onLoadScan = { currentTab = "visualizer" })
                "ai" -> AiAnalysisScreen(viewModel = viewModel)
            }
        }
    }
}
