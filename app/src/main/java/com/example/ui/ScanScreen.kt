package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.ConnectionMode
import com.example.hardware.SensorType
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(
    viewModel: VisualizerViewModel,
    onNavigateToAi: () -> Unit = {}
) {
    val gridWidth by viewModel.gridWidth.collectAsStateWithLifecycle()
    val gridLength by viewModel.gridLength.collectAsStateWithLifecycle()
    val soilType by viewModel.soilType.collectAsStateWithLifecycle()
    val scanPattern by viewModel.scanPattern.collectAsStateWithLifecycle()
    val isScanActive by viewModel.isScanActive.collectAsStateWithLifecycle()
    val currentCol by viewModel.currentCol.collectAsStateWithLifecycle()
    val currentRow by viewModel.currentRow.collectAsStateWithLifecycle()
    val activeScanData by viewModel.activeScanData.collectAsStateWithLifecycle()
    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.sensorManager.tvStatus.collectAsStateWithLifecycle()
    val adcValue by viewModel.sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by viewModel.sensorManager.phaseShift.collectAsStateWithLifecycle()
    val activeSensorType by viewModel.sensorManager.sensorType.collectAsStateWithLifecycle()
    val activeBaudRate by viewModel.sensorManager.baudRate.collectAsStateWithLifecycle()
    val compassHeading by viewModel.sensorManager.compassHeading.collectAsStateWithLifecycle()

    var autoPulseEnabled by remember { mutableStateOf(false) }

    // Polling Coroutine for Automatic Impulses
    LaunchedEffect(isScanActive, autoPulseEnabled) {
        if (isScanActive && autoPulseEnabled) {
            while (true) {
                delay(1200)
                viewModel.recordCurrentStep()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "OKM 3D VISUALIZER",
                    color = CyberGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "اسکن زمین و رادار عمق‌سنج مگنتومتر",
                    color = GrayText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Connection Status Widget
            Card(
                onClick = { viewModel.sensorManager.autoConnect() },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("connection_status_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val indicatorColor = when (connectionState) {
                        ConnectionMode.DISCONNECTED -> Color.Red
                        ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> Color.Yellow
                        ConnectionMode.USB -> Color.Green
                        ConnectionMode.BLUETOOTH -> CyberCyan
                        ConnectionMode.SIMULATOR -> CyberGold
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Text(
                        text = connectionStatus,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (!isScanActive) {
            // Configuration mode
            Text(
                text = "تنظیمات اسکن جدید (Scan Configuration)",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Right
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Grid size configuration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Width (Columns)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تعداد پالس در خط (عرض)", color = CyberGold, fontSize = 11.sp)
                            Text("Grid Width (Cols)", color = GrayText, fontSize = 9.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBg, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.updateGridWidth(gridWidth - 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Minus", tint = Color.White)
                                }
                                Text("$gridWidth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { viewModel.updateGridWidth(gridWidth + 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = Color.White)
                                }
                            }
                        }

                        // Length (Lines/Rows)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تعداد خطوط اسکن (طول)", color = CyberGold, fontSize = 11.sp)
                            Text("Grid Length (Rows)", color = GrayText, fontSize = 9.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBg, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.updateGridLength(gridLength - 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Minus", tint = Color.White)
                                }
                                Text("$gridLength", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { viewModel.updateGridLength(gridLength + 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Soil Selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("نوع خاک هدف (Soil Type)", color = CyberGold, fontSize = 11.sp)
                        Text("بر روی سرعت امواج عمق‌سنج اثرگذار است", color = GrayText, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val soils = listOf("خاک کشاورزی (Soil)", "خاک معدنی (Mineral)", "سنگ و صخره (Hard Rock)", "ماسه خیس (Wet Sand)")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            soils.forEach { s ->
                                val selected = soilType == s
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSoilType(s) },
                                    label = { Text(s, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberGold,
                                        containerColor = CardBg,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Scan Mode Selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("طرح حرکت اسکن (Scan Pattern)", color = CyberGold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                onClick = { viewModel.updateScanPattern("موازی (Parallel)") },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (scanPattern.contains("Parallel") || scanPattern.contains("موازی")) {
                                        CyberCyan.copy(alpha = 0.15f)
                                    } else CardBg
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (scanPattern.contains("Parallel") || scanPattern.contains("موازی")) CyberCyan else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FormatAlignJustify, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                    Text("موازی (Parallel)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("شروع خطوط همیشه از چپ", color = GrayText, fontSize = 8.sp)
                                }
                            }
                            Card(
                                onClick = { viewModel.updateScanPattern("زیگزاگ (Zig-Zag)") },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")) {
                                        CyberCyan.copy(alpha = 0.15f)
                                    } else CardBg
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")) CyberCyan else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                    Text("زیگزاگ (Zig-Zag)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("رفت و برگشتی متناوب", color = GrayText, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card: Sensor & Connection Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp))
                        Column {
                            Text("تنظیمات سنسور و درایور ارتباطی", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("انتخاب نوع مگنتومتر و پورت CH340", color = GrayText, fontSize = 9.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // 1. Sensor Profile Selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("نوع سنسور مگنتومتر (Sensor Type)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SensorType.values().forEach { type ->
                                val label = when (type) {
                                    SensorType.GOLD_RADAR_X20 -> "استاندارد X20"
                                    SensorType.FMG3 -> "FMG3 فلاکس‌گیت"
                                    SensorType.FLC100 -> "FLC100 فلاکس‌گیت"
                                    SensorType.HMC5883L -> "قطب‌نما HMC5883L"
                                }
                                val isSelected = activeSensorType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.sensorManager.setSensorType(type) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberGold,
                                        containerColor = CardBg,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // 2. Baud Rate Selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("سرعت درایور CH340 (Baud Rate)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(9600, 115200).forEach { rate ->
                                val isSelected = activeBaudRate == rate
                                Card(
                                    onClick = { viewModel.sensorManager.setBaudRate(rate) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) CyberCyan.copy(alpha = 0.15f) else CardBg
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) CyberCyan else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text("$rate bps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Dynamic Digital Compass Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBg, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = CyberGold,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(-compassHeading)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("قطب‌نمای دیجیتال زمین‌شناسی", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("تراز و هماهنگ‌کننده جهت اسکن سه‌بعدی", color = GrayText, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(String.format("%.1f°", compassHeading), color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            val directionName = when {
                                compassHeading >= 337.5f || compassHeading < 22.5f -> "شمال (N)"
                                compassHeading >= 22.5f && compassHeading < 67.5f -> "شمال‌شرق (NE)"
                                compassHeading >= 67.5f && compassHeading < 112.5f -> "شرق (E)"
                                compassHeading >= 112.5f && compassHeading < 157.5f -> "جنوب‌شرق (SE)"
                                compassHeading >= 157.5f && compassHeading < 202.5f -> "جنوب (S)"
                                compassHeading >= 202.5f && compassHeading < 247.5f -> "جنوب‌غرب (SW)"
                                compassHeading >= 247.5f && compassHeading < 292.5f -> "غرب (W)"
                                else -> "شمال‌غرب (NW)"
                            }
                            Text(directionName, color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Start scanning button
            Button(
                onClick = { viewModel.startNewScan() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_scan_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("شروع اسکن سه‌بعدی جدید", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // Scan acquisition mode
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scan State Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("در حال تصویربرداری زمین...", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("سایز کل شبکه: $gridWidth × $gridLength", color = GrayText, fontSize = 11.sp)
                        }

                        // Cancel button
                        IconButton(
                            onClick = { viewModel.cancelScan() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                            modifier = Modifier.testTag("cancel_scan_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Discard", tint = Color.Red)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    val totalPoints = gridWidth * gridLength
                    val scannedPoints = activeScanData.size
                    val rawProgressFraction = if (totalPoints > 0) scannedPoints.toFloat() / totalPoints else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawProgressFraction,
                        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
                        label = "ScanProgress"
                    )

                    val infiniteTransition = rememberInfiniteTransition(label = "AcquisitionAnimation")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseAlpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (scannedPoints < totalPoints) CyberCyan.copy(alpha = pulseAlpha) else Color.Green)
                                )
                                Text(
                                    text = "پیشرفت تصویربرداری (Scan Progress)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "$scannedPoints / $totalPoints نقطه (${(rawProgressFraction * 100).toInt()}%)",
                                color = CyberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Custom Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CardBg.copy(alpha = 0.5f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(6.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                if (animatedProgress > 0f) {
                                    val progressW = w * animatedProgress
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(CyberCyan.copy(alpha = 0.75f), CyberGold.copy(alpha = 0.95f))
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(progressW, h)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3f,
                                        center = Offset(progressW, h / 2)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2D Acquisition Matrix
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                            .background(CardBg, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cellWidth = size.width / gridWidth
                            val cellHeight = size.height / gridLength

                            for (y in 0 until gridLength) {
                                for (x in 0 until gridWidth) {
                                    val cellIdx = y * gridWidth + x
                                    val isCurrent = (x == currentCol && y == currentRow)

                                    val isZigZag = scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")
                                    val goingRight = !isZigZag || (y % 2 == 0)
                                    val isScanned = when {
                                        y < currentRow -> true
                                        y == currentRow -> if (goingRight) x < currentCol else x > currentCol
                                        else -> false
                                    }

                                    val cellLeft = x * cellWidth
                                    val cellTop = y * cellHeight
                                    val colorVal = activeScanData.getOrNull(cellIdx) ?: 250f

                                    val fillBrush = if (isScanned) {
                                        val norm = ((colorVal - 250f) / 200f).coerceIn(-1f, 2f)
                                        val c = getTargetColor(norm, 0f)
                                        Brush.linearGradient(listOf(c, c.copy(alpha = 0.85f)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF202634), Color(0xFF141924)))
                                    }

                                    drawRect(
                                        brush = fillBrush,
                                        topLeft = Offset(cellLeft + 1.5f, cellTop + 1.5f),
                                        size = Size(cellWidth - 3f, cellHeight - 3f)
                                    )

                                    if (isCurrent) {
                                        drawRect(
                                            color = CyberGold,
                                            topLeft = Offset(cellLeft + 1f, cellTop + 1f),
                                            size = Size(cellWidth - 2f, cellHeight - 2f),
                                            style = Stroke(width = 2.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Coordinates Readout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("موقعیت فعلی X", color = GrayText, fontSize = 10.sp)
                            Text("ستون ${currentCol + 1} از $gridWidth", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("موقعیت فعلی Y", color = GrayText, fontSize = 10.sp)
                            Text("خط ${currentRow + 1} از $gridLength", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Sensor Readout Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("پالس سنسور (ADC)", color = CyberCyan, fontSize = 10.sp)
                                Text("$adcValue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(modifier = Modifier.height(28.dp).width(1.dp), color = Color.White.copy(alpha = 0.15f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("اختلاف فاز (Phase)", color = CyberCyan, fontSize = 10.sp)
                                Text("$phaseShift°", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Auto record switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "ثبت خودکار\n(Auto-Pulse)",
                                color = Color.White,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Right
                            )
                            Switch(
                                checked = autoPulseEnabled,
                                onCheckedChange = { autoPulseEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberGold,
                                    checkedTrackColor = CyberGold.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = CardBg
                                ),
                                modifier = Modifier.testTag("auto_pulse_switch")
                            )
                        }

                        // Capture Step Trigger (Big Ping Circle)
                        Button(
                            onClick = { viewModel.recordCurrentStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(88.dp)
                                .border(BorderStroke(3.dp, CyberGold.copy(alpha = 0.3f)), CircleShape)
                                .testTag("record_pulse_button"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
                                Text("ثبت پالس", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Simulation Step forward / peak injection
                        IconButton(
                            onClick = {
                                if (connectionState == ConnectionMode.SIMULATOR) {
                                    val phaseSim = if (Math.random() > 0.65) 48 else -15
                                    val adcSim = if (phaseSim > 0) 820 else 120
                                    viewModel.sensorManager.setSimulatedReading(adcSim, phaseSim)
                                }
                                viewModel.recordCurrentStep()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                            modifier = Modifier.testTag("skip_step_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Step Forward", tint = CyberCyan)
                        }
                    }
                }
            }
        }
    }
}
