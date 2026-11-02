package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizerScreen(
    viewModel: VisualizerViewModel,
    onNavigateToAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()
    val yaw by viewModel.yaw.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val panX by viewModel.panX.collectAsStateWithLifecycle()
    val panY by viewModel.panY.collectAsStateWithLifecycle()
    val zScale by viewModel.zScale.collectAsStateWithLifecycle()
    val colorThreshold by viewModel.colorThreshold.collectAsStateWithLifecycle()
    val renderStyle by viewModel.renderStyle.collectAsStateWithLifecycle()
    val isRgbAnalysis by viewModel.isRgbAnalysis.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val sliceAxis by viewModel.sliceAxis.collectAsStateWithLifecycle()
    val sliceIndex by viewModel.sliceIndex.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }
    var scanSaveName by remember { mutableStateOf("") }
    var scanSaveNotes by remember { mutableStateOf("") }
    var isTopDown2D by remember { mutableStateOf(false) }
    var isTopHudExpanded by remember { mutableStateOf(true) }
    var isBottomDrawerExpanded by remember { mutableStateOf(true) }

    if (viewedScan == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = CyberGold, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("هیچ اسکن فعالی یافت نشد", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "لطفاً ابتدا یک اسکن زمین در تب اول انجام دهید یا یکی از قالب‌های آماده تاریخی را از تب چهارم لود کنید.",
                color = GrayText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (savedScans.isNotEmpty()) {
                        viewModel.loadScan(savedScans.first())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("بارگذاری اسکن نمونه (Load Demo)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        val currentScan = viewedScan!!

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 3D Viewport (Native Canvas with 3D or 2D Top-Down View)
            Native3DCanvas(
                scan = currentScan,
                yaw = yaw,
                pitch = pitch,
                zoom = zoom,
                panX = panX,
                panY = panY,
                zScale = zScale,
                colorThreshold = colorThreshold,
                renderStyle = renderStyle,
                filterMode = filterMode,
                sliceAxis = sliceAxis,
                sliceIndex = sliceIndex,
                isTopDown2D = isTopDown2D,
                onRotate = { dy, dp -> viewModel.updateYawPitch(dy, dp) },
                onZoom = { factor -> viewModel.updateZoom(factor) },
                onPan = { dx, dy -> viewModel.updatePan(dx, dy) },
                onSelectNode = { idx -> viewModel.selectNodeIndex(idx) },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Floating Top HUD
            AnimatedVisibility(
                visible = isTopHudExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentScan.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "شبکه: ${currentScan.gridWidth}×${currentScan.gridLength} | خاک: ${currentScan.soilType}",
                                    color = GrayText,
                                    fontSize = 10.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // 2D / 3D Mode Toggle
                                FilterChip(
                                    selected = isTopDown2D,
                                    onClick = { isTopDown2D = !isTopDown2D },
                                    label = { Text(if (isTopDown2D) "نمای 2D" else "نمای 3D", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = CyberCyan,
                                        containerColor = CardBg,
                                        labelColor = Color.White
                                    )
                                )

                                // Reset camera
                                IconButton(
                                    onClick = { viewModel.resetCamera() },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = CyberGold, modifier = Modifier.size(16.dp))
                                }

                                // Toggle HUD
                                IconButton(
                                    onClick = { isTopHudExpanded = false },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Hide HUD", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Target Info Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = CyberGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Radar, contentDescription = null, tint = CyberGold, modifier = Modifier.size(14.dp))
                                    Text(currentScan.anomalyClassification, color = CyberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            Surface(
                                color = CyberCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Height, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                    Text("عمق: ${currentScan.estimatedDepthMeters}m", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Restore HUD pill button if hidden
            if (!isTopHudExpanded) {
                IconButton(
                    onClick = { isTopHudExpanded = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceBg)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Show HUD", tint = CyberGold)
                }
            }

            // 3. Floating Bottom Controls Drawer
            AnimatedVisibility(
                visible = isBottomDrawerExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header row of controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تنظیمات نمایش و تحلیل (3D Controls)", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { showSaveDialog = true },
                                    modifier = Modifier.size(30.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save", tint = CyberGold, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { isBottomDrawerExpanded = false },
                                    modifier = Modifier.size(30.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Hide Drawer", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // 1. Render Style Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "SURFACE" to "سطحی (Mesh)",
                                "WIREFRAME" to "سیمی (Wire)",
                                "VOXEL" to "وکسیل (Voxel)",
                                "POINTS" to "نقاط (Points)"
                            ).forEach { (styleKey, styleLabel) ->
                                val selected = renderStyle == styleKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setRenderStyle(styleKey) },
                                    label = { Text(styleLabel, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberCyan,
                                        containerColor = CardBg,
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 2. Sliders: Vertical Z-Scale and Color Threshold
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Z-Scale
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("برجستگی Z", color = GrayText, fontSize = 9.sp)
                                    Text(String.format("%.1fx", zScale), color = CyberGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = zScale,
                                    onValueChange = { viewModel.setZScale(it) },
                                    valueRange = 0.2f..4.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyberGold,
                                        activeTrackColor = CyberGold
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            // Color Threshold
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("فیلتر نویز زمینه", color = GrayText, fontSize = 9.sp)
                                    Text("${(colorThreshold * 100).toInt()}%", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = colorThreshold,
                                    onValueChange = { viewModel.setColorThreshold(it) },
                                    valueRange = 0.0f..0.8f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyberCyan,
                                        activeTrackColor = CyberCyan
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        // 3. Quick Action Buttons: AI Analysis & Export
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToAi,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("visualizer_to_ai_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تحلیل تخصصی هوش مصنوعی", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val csv = currentScan.data.chunked(currentScan.gridWidth).joinToString("\n") { it.joinToString(",") }
                                    Toast.makeText(context, "داده‌های اسکن کپی شد (${currentScan.data.size} نقطه)", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Restore drawer pill button if hidden
            if (!isBottomDrawerExpanded) {
                IconButton(
                    onClick = { isBottomDrawerExpanded = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceBg)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Show Controls", tint = CyberGold)
                }
            }
        }

        // Save Scan Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                containerColor = SurfaceBg,
                title = {
                    Text("ذخیره اسکن زمین", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = scanSaveName,
                            onValueChange = { scanSaveName = it },
                            label = { Text("نام اسکن", color = GrayText) },
                            placeholder = { Text(currentScan.name, color = GrayText.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyberGold,
                                unfocusedBorderColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = scanSaveNotes,
                            onValueChange = { scanSaveNotes = it },
                            label = { Text("یادداشت و مختصات میدانی", color = GrayText) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBorder
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveCurrentScan(scanSaveName, scanSaveNotes)
                            showSaveDialog = false
                            Toast.makeText(context, "اسکن با موفقیت در تاریخچه ذخیره شد", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGold)
                    ) {
                        Text("ذخیره", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("انصراف", color = GrayText)
                    }
                }
            )
        }
    }
}
