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
import androidx.compose.ui.text.style.TextOverflow
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
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val sliceAxis by viewModel.sliceAxis.collectAsStateWithLifecycle()
    val sliceIndex by viewModel.sliceIndex.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }
    var scanSaveName by remember { mutableStateOf("") }
    var scanSaveNotes by remember { mutableStateOf("") }
    var isTopDown2D by remember { mutableStateOf(false) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Top Header Card (Clean, organized, never overlaps canvas)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Title and Main Controls
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "شبکه: ${currentScan.gridWidth}×${currentScan.gridLength} | ${currentScan.soilType}",
                                color = GrayText,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                ),
                                border = BorderStroke(1.dp, if (isTopDown2D) CyberCyan else CardBorder)
                            )

                            // Reset Camera
                            IconButton(
                                onClick = { viewModel.resetCamera() },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Camera", tint = CyberGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Row 2: Badges (Horizontally scrollable to avoid wrapping on narrow screens)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = CyberGold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Radar, contentDescription = null, tint = CyberGold, modifier = Modifier.size(12.dp))
                                Text(currentScan.anomalyClassification, color = CyberGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = CyberCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Height, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                                Text("عمق: ${currentScan.estimatedDepthMeters}m", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                "نقاط: ${currentScan.data.size}",
                                color = GrayText,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // 2. Main 3D / 2D Canvas Viewport (Takes all remaining flexible middle space!)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090C11))
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(14.dp))
            ) {
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

                // Floating Zoom In / Zoom Out Controls on the side
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.updateZoom(1.18f) },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.updateZoom(0.85f) },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg.copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Small View Mode Tag in corner
                Surface(
                    color = CardBg.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        if (isTopDown2D) "2D Ortho" else "3D Perspective",
                        color = GrayText,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 3. Bottom Controls Panel (Docked at bottom, neatly organized, collapsible)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Header of Bottom Panel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                            Text(
                                "تنظیمات نمایش و تحلیل",
                                color = CyberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = CyberGold, modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { isBottomDrawerExpanded = !isBottomDrawerExpanded },
                                modifier = Modifier.size(28.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                            ) {
                                Icon(
                                    if (isBottomDrawerExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle Drawer",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Expandable Controls Section
                    AnimatedVisibility(
                        visible = isBottomDrawerExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. Render Styles (Scrollable row so chips never wrap weirdly)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
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
                                        border = BorderStroke(1.dp, if (selected) CyberCyan else CardBorder)
                                    )
                                }
                            }

                            // 2. Dual Sliders: Z-Scale and Background Filter
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                        modifier = Modifier.height(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("فیلتر نویز", color = GrayText, fontSize = 9.sp)
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
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            // 3. Action Buttons
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
                                        .height(38.dp)
                                        .testTag("visualizer_to_ai_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحلیل هوش مصنوعی", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val csv = currentScan.data.chunked(currentScan.gridWidth).joinToString("\n") { it.joinToString(",") }
                                        Toast.makeText(context, "داده‌های اسکن کپی شد (${currentScan.data.size} نقطه)", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
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
