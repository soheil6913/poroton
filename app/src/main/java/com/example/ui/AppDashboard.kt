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
                    label = { Text("اسکن", fontSize = 9.sp, maxLines = 1) },
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
                    label = { Text("سه‌بعدی", fontSize = 9.sp, maxLines = 1) },
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
                    label = { Text("زنده", fontSize = 9.sp, maxLines = 1) },
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
                    label = { Text("تاریخچه", fontSize = 9.sp, maxLines = 1) },
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
                    selected = currentTab == "compare",
                    onClick = { currentTab = "compare" },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Compare") },
                    label = { Text("مقایسه", fontSize = 9.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = GrayText,
                        unselectedTextColor = GrayText,
                        indicatorColor = CardBg
                    ),
                    modifier = Modifier.testTag("nav_compare")
                )
                NavigationBarItem(
                    selected = currentTab == "ai",
                    onClick = { currentTab = "ai" },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    label = { Text("هوشمند", fontSize = 9.sp, maxLines = 1) },
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
                "visualizer" -> VisualizerScreen(
                    viewModel = viewModel,
                    onNavigateToAi = { currentTab = "ai" },
                    onNavigateToCompare = { currentTab = "compare" }
                )
                "tracker" -> TrackerScreen(viewModel = viewModel)
                "history" -> HistoryScreen(
                    viewModel = viewModel,
                    onLoadScan = { currentTab = "visualizer" },
                    onCompareScan = { scan ->
                        viewModel.setComparisonScanA(scan)
                        currentTab = "compare"
                    }
                )
                "compare" -> ComparisonScreen(
                    viewModel = viewModel,
                    onNavigateToAi = { currentTab = "ai" }
                )
                "ai" -> AiAnalysisScreen(viewModel = viewModel)
            }
        }
    }
}
