package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ScanRecord
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Mathematical bilinear interpolation to resample any scan grid at normalized coordinates (u, v) in [0..1].
 * Allows pixel-perfect overlay even when Scans A and B have different dimensions.
 */
fun sampleGridValue(data: List<Float>, gridW: Int, gridL: Int, uNorm: Float, vNorm: Float): Float {
    if (data.isEmpty() || gridW <= 0 || gridL <= 0) return 0f
    val xFloat = (uNorm * (gridW - 1)).coerceIn(0f, (gridW - 1).toFloat())
    val yFloat = (vNorm * (gridL - 1)).coerceIn(0f, (gridL - 1).toFloat())

    val x0 = xFloat.toInt()
    val y0 = yFloat.toInt()
    val x1 = (x0 + 1).coerceAtMost(gridW - 1)
    val y1 = (y0 + 1).coerceAtMost(gridL - 1)

    val fracX = xFloat - x0
    val fracY = yFloat - y0

    val v00 = data.getOrNull(y0 * gridW + x0) ?: 200f
    val v10 = data.getOrNull(y0 * gridW + x1) ?: 200f
    val v01 = data.getOrNull(y1 * gridW + x0) ?: 200f
    val v11 = data.getOrNull(y1 * gridW + x1) ?: 200f

    val top = v00 * (1f - fracX) + v10 * fracX
    val bottom = v01 * (1f - fracX) + v11 * fracX

    return top * (1f - fracY) + bottom * fracY
}

/**
 * Colormap for Differential Delta (Δ):
 * High Positive (Red / Yellow) -> Magnetic Anomaly Gain (New metal or mineral concentration)
 * Near Zero (Deep Dark Slate) -> Neutral / Unchanged geological ground
 * High Negative (Cyan / Deep Navy) -> Void / Cavity / Excavation / Subsidence
 */
fun getDeltaColor(delta: Float, noiseThreshold: Float = 25f): Color {
    val absVal = abs(delta)
    if (absVal < noiseThreshold) {
        return Color(0xFF141922)
    }
    return if (delta > 0) {
        val t = ((delta - noiseThreshold) / 350f).coerceIn(0f, 1f)
        when {
            t < 0.4f -> lerpColor(Color(0xFF27382A), BrightYellow, t / 0.4f)
            else -> lerpColor(BrightYellow, CyberRed, (t - 0.4f) / 0.6f)
        }
    } else {
        val t = ((-delta - noiseThreshold) / 350f).coerceIn(0f, 1f)
        when {
            t < 0.4f -> lerpColor(Color(0xFF1A2736), CyberCyan, t / 0.4f)
            else -> lerpColor(CyberCyan, DeepNavy, (t - 0.4f) / 0.6f)
        }
    }
}

data class ComparisonMetrics(
    val commonW: Int,
    val commonL: Int,
    val deltaArray: FloatArray,
    val aArray: FloatArray,
    val bArray: FloatArray,
    val maxPositiveDelta: Float,
    val maxNegativeDelta: Float,
    val maxPosCoord: Pair<Int, Int>,
    val maxNegCoord: Pair<Int, Int>,
    val changedCellsCount: Int,
    val totalCells: Int,
    val similarityPercent: Int,
    val diagnosticSummary: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComparisonScreen(
    viewModel: VisualizerViewModel,
    onNavigateToAi: () -> Unit = {}
) {
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()
    val scanA by viewModel.comparisonScanA.collectAsStateWithLifecycle()
    val scanB by viewModel.comparisonScanB.collectAsStateWithLifecycle()
    val comparisonMode by viewModel.comparisonMode.collectAsStateWithLifecycle()
    val overlayOpacity by viewModel.overlayOpacity.collectAsStateWithLifecycle()
    val blinkSpeedMs by viewModel.blinkSpeedMs.collectAsStateWithLifecycle()
    val noiseThreshold by viewModel.noiseFilterThreshold.collectAsStateWithLifecycle()
    val splitCurtainPos by viewModel.splitCurtainPos.collectAsStateWithLifecycle()
    val selectedCoord by viewModel.comparisonSelectedCoord.collectAsStateWithLifecycle()

    var showPickerForSlot by remember { mutableStateOf<String?>(null) } // "A" or "B"
    var showSimulateDialog by remember { mutableStateOf(false) }

    // Blink state
    var isBlinkActive by remember { mutableStateOf(false) }
    var blinkPhaseA by remember { mutableStateOf(true) }

    LaunchedEffect(isBlinkActive, blinkSpeedMs) {
        if (isBlinkActive) {
            while (true) {
                delay(blinkSpeedMs)
                blinkPhaseA = !blinkPhaseA
            }
        }
    }

    // Compute comparison metrics
    val metrics = remember(scanA, scanB, noiseThreshold) {
        if (scanA == null || scanB == null) null
        else {
            val sA = scanA!!
            val sB = scanB!!
            val commonW = max(sA.gridWidth, sB.gridWidth).coerceAtLeast(8)
            val commonL = max(sA.gridLength, sB.gridLength).coerceAtLeast(8)
            val total = commonW * commonL

            val deltas = FloatArray(total)
            val aVals = FloatArray(total)
            val bVals = FloatArray(total)

            var maxPos = 0f
            var maxNeg = 0f
            var posCoord = Pair(0, 0)
            var negCoord = Pair(0, 0)
            var changedCount = 0

            var sumA = 0.0
            var sumB = 0.0

            for (y in 0 until commonL) {
                val vNorm = if (commonL > 1) y.toFloat() / (commonL - 1) else 0f
                for (x in 0 until commonW) {
                    val uNorm = if (commonW > 1) x.toFloat() / (commonW - 1) else 0f
                    val idx = y * commonW + x

                    val vA = sampleGridValue(sA.data, sA.gridWidth, sA.gridLength, uNorm, vNorm)
                    val vB = sampleGridValue(sB.data, sB.gridWidth, sB.gridLength, uNorm, vNorm)
                    val delta = vB - vA

                    deltas[idx] = delta
                    aVals[idx] = vA
                    bVals[idx] = vB

                    sumA += vA
                    sumB += vB

                    if (abs(delta) >= noiseThreshold) {
                        changedCount++
                    }

                    if (delta > maxPos) {
                        maxPos = delta
                        posCoord = Pair(x, y)
                    }
                    if (delta < maxNeg) {
                        maxNeg = delta
                        negCoord = Pair(x, y)
                    }
                }
            }

            // Pearson correlation
            val meanA = sumA / total
            val meanB = sumB / total
            var num = 0.0
            var denA = 0.0
            var denB = 0.0
            for (i in 0 until total) {
                val da = aVals[i] - meanA
                val db = bVals[i] - meanB
                num += da * db
                denA += da * da
                denB += db * db
            }
            val denom = sqrt(denA * denB)
            val r = if (denom > 0.0001) (num / denom).coerceIn(-1.0, 1.0) else 1.0
            val similarity = (((r + 1.0) / 2.0) * 100.0).toInt().coerceIn(0, 100)

            // Diagnostic summary text
            val diag = when {
                similarity >= 92 -> "پایداری بسیار بالا: هیچ تغییر ژئوفیزیکی یا ساختاری معناداری رخ نداده است."
                maxPos > 300f && maxNeg < -200f -> "آنومالی دوگانه زمانی: تشکیل حفره/ترانشه جدید همراه با تمرکز جسم فلزی پرقدرت."
                maxNeg < -200f -> "تشخیص حفاری یا فرونشست: افت شدید سیگنال مغناطیسی در مختصات (${negCoord.first + 1}, ${negCoord.second + 1})."
                maxPos > 250f -> "هدف فلزی جدید: افزایش بارز شدت شار مغناطیسی در مختصات (${posCoord.first + 1}, ${posCoord.second + 1})."
                else -> "تغییرات ملایم خاک: احتمالاً ناشی از رطوبت سطحی، نویز فصلی یا جابجایی طبیعی رسوبات."
            }

            ComparisonMetrics(
                commonW = commonW,
                commonL = commonL,
                deltaArray = deltas,
                aArray = aVals,
                bArray = bVals,
                maxPositiveDelta = maxPos,
                maxNegativeDelta = maxNeg,
                maxPosCoord = posCoord,
                maxNegCoord = negCoord,
                changedCellsCount = changedCount,
                totalCells = total,
                similarityPercent = similarity,
                diagnosticSummary = diag
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "ابزار مقایسه و هم‌پوشانی (Time-Lapse Overlay)",
                    color = CyberGold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "تشخیص تغییرات ساختار خاک و آنومالی‌های مغناطیسی در گذر زمان",
                    color = GrayText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { showSimulateDialog = true },
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                modifier = Modifier.testTag("simulate_timelapse_button")
            ) {
                Icon(Icons.Default.Addchart, contentDescription = "Simulate Rescan", tint = CyberCyan)
            }
        }

        // 2. Scan Selectors Row (Scan A ⇄ Scan B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Slot A Card
            Card(
                onClick = { showPickerForSlot = "A" },
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (scanA != null) CyberCyan.copy(alpha = 0.5f) else CardBorder),
                modifier = Modifier.weight(1f).testTag("select_scan_a_card")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اسکن A (پایه/مرجع)", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scanA?.name ?: "انتخاب اسکن...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (scanA != null) "${scanA!!.gridWidth}×${scanA!!.gridLength} | ${scanA!!.soilType.take(10)}" else "کلیک کنید",
                        color = GrayText,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }

            // Swap Button
            IconButton(
                onClick = { viewModel.swapComparisonScans() },
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg),
                modifier = Modifier.size(36.dp).testTag("swap_scans_button")
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = CyberGold, modifier = Modifier.size(20.dp))
            }

            // Slot B Card
            Card(
                onClick = { showPickerForSlot = "B" },
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (scanB != null) CyberGold.copy(alpha = 0.5f) else CardBorder),
                modifier = Modifier.weight(1f).testTag("select_scan_b_card")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اسکن B (جدید/مقایسه)", color = CyberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Update, contentDescription = null, tint = CyberGold, modifier = Modifier.size(12.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scanB?.name ?: "انتخاب اسکن...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (scanB != null) "${scanB!!.gridWidth}×${scanB!!.gridLength} | ${scanB!!.soilType.take(10)}" else "کلیک کنید",
                        color = GrayText,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 3. Comparison Mode Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                Pair("DELTA", "تفاضلی (Δ Delta)"),
                Pair("OVERLAY", "هم‌پوشانی شفاف (Ghost)"),
                Pair("BLINK", "چشمک‌زن (Blink)"),
                Pair("SPLIT_SWIPE", "پرده متحرک (Split)"),
                Pair("SIDE_BY_SIDE", "کنار هم (Side-by-Side)")
            )

            modes.forEach { (modeId, label) ->
                val isSelected = comparisonMode == modeId
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setComparisonMode(modeId) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                        selectedLabelColor = CyberGold,
                        containerColor = CardBg,
                        labelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) CyberGold else CardBorder),
                    modifier = Modifier.testTag("mode_$modeId")
                )
            }
        }

        // 4. Mode-Specific Interactive Sliders & Parameters
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (comparisonMode) {
                    "DELTA" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("آستانه فیلتر نویز تفاضلی:", color = GrayText, fontSize = 11.sp)
                            Text("±${noiseThreshold.toInt()} ADC", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = noiseThreshold,
                            onValueChange = { viewModel.setNoiseFilterThreshold(it) },
                            valueRange = 5f..100f,
                            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                            modifier = Modifier.height(28.dp).testTag("noise_threshold_slider")
                        )
                    }
                    "OVERLAY" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("میزان ترکیب شفافیت (Scan A ↔ Scan B):", color = GrayText, fontSize = 11.sp)
                            Text("${(overlayOpacity * 100).toInt()}% B", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = overlayOpacity,
                            onValueChange = { viewModel.setOverlayOpacity(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = CyberGold, activeTrackColor = CyberGold),
                            modifier = Modifier.height(28.dp).testTag("overlay_opacity_slider")
                        )
                    }
                    "BLINK" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { isBlinkActive = !isBlinkActive },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBlinkActive) Color.Red else CyberGold
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBlinkActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isBlinkActive) "توقف" else "پخش چشمک‌زن", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    color = if (blinkPhaseA) CyberCyan.copy(alpha = 0.2f) else CyberGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (blinkPhaseA) "اسکن A" else "اسکن B",
                                        color = if (blinkPhaseA) CyberCyan else CyberGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text("سرعت: ${blinkSpeedMs}ms", color = GrayText, fontSize = 10.sp)
                        }
                    }
                    "SPLIT_SWIPE" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("موقعیت پرده مقایسه (اسکن A در چپ | اسکن B در راست):", color = GrayText, fontSize = 11.sp)
                            Text("${(splitCurtainPos * 100).toInt()}%", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = splitCurtainPos,
                            onValueChange = { viewModel.setSplitCurtainPos(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                            modifier = Modifier.height(28.dp).testTag("split_curtain_slider")
                        )
                    }
                    "SIDE_BY_SIDE" -> {
                        Text(
                            text = "نمایش موازی دو اسکن با همگام‌سازی نشانگر مختصات. با لمس هر نقطه، اطلاعات دو زمان بررسی می‌شود.",
                            color = GrayText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 5. Main Visual Comparison Viewport Canvas
        if (metrics != null && scanA != null && scanB != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0F)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Coordinates Header & Probe Readout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("شبکه تطبیق‌یافته: ${metrics.commonW}×${metrics.commonL}", color = GrayText, fontSize = 10.sp)

                        if (selectedCoord != null) {
                            val (cx, cy) = selectedCoord!!
                            val idx = cy * metrics.commonW + cx
                            val valA = metrics.aArray.getOrElse(idx) { 0f }
                            val valB = metrics.bArray.getOrElse(idx) { 0f }
                            val valDelta = metrics.deltaArray.getOrElse(idx) { 0f }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("نقطه ($cx, $cy)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("A: ${valA.toInt()}", color = CyberCyan, fontSize = 10.sp)
                                Text("B: ${valB.toInt()}", color = CyberGold, fontSize = 10.sp)
                                Text(
                                    "${if (valDelta >= 0) "+" else ""}${valDelta.toInt()} Δ",
                                    color = if (valDelta >= 0) CyberRed else CyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text("جهت بررسی پروب، روی شبکه لمس کنید", color = CyberCyan.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rendering Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.15f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090D14))
                            .pointerInput(metrics.commonW, metrics.commonL) {
                                detectTapGestures { offset ->
                                    val cellW = size.width / metrics.commonW
                                    val cellH = size.height / metrics.commonL
                                    val col = (offset.x / cellW).toInt().coerceIn(0, metrics.commonW - 1)
                                    val row = (offset.y / cellH).toInt().coerceIn(0, metrics.commonL - 1)
                                    viewModel.setComparisonSelectedCoord(Pair(col, row))
                                }
                            }
                            .testTag("comparison_canvas_box")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val cols = metrics.commonW
                            val rows = metrics.commonL
                            val cellW = canvasW / cols
                            val cellH = canvasH / rows

                            when (comparisonMode) {
                                "DELTA" -> {
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val d = metrics.deltaArray[idx]
                                            val col = getDeltaColor(d, noiseThreshold)

                                            drawRect(
                                                color = col,
                                                topLeft = Offset(x * cellW + 1f, y * cellH + 1f),
                                                size = Size(cellW - 2f, cellH - 2f)
                                            )
                                        }
                                    }
                                }
                                "OVERLAY" -> {
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val vA = metrics.aArray[idx]
                                            val vB = metrics.bArray[idx]

                                            val colA = getTargetColor((vA - 250f) / 250f, 0f)
                                            val colB = getTargetColor((vB - 250f) / 250f, 0f)
                                            val blended = lerpColor(colA, colB, overlayOpacity)

                                            drawRect(
                                                color = blended,
                                                topLeft = Offset(x * cellW + 1f, y * cellH + 1f),
                                                size = Size(cellW - 2f, cellH - 2f)
                                            )
                                        }
                                    }
                                }
                                "BLINK" -> {
                                    val activeVals = if (blinkPhaseA) metrics.aArray else metrics.bArray
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val v = activeVals[idx]
                                            val col = getTargetColor((v - 250f) / 250f, 0f)

                                            drawRect(
                                                color = col,
                                                topLeft = Offset(x * cellW + 1f, y * cellH + 1f),
                                                size = Size(cellW - 2f, cellH - 2f)
                                            )
                                        }
                                    }
                                }
                                "SPLIT_SWIPE" -> {
                                    val splitX = canvasW * splitCurtainPos
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val cellCenterX = (x + 0.5f) * cellW
                                            val v = if (cellCenterX < splitX) metrics.aArray[idx] else metrics.bArray[idx]
                                            val col = getTargetColor((v - 250f) / 250f, 0f)

                                            drawRect(
                                                color = col,
                                                topLeft = Offset(x * cellW + 1f, y * cellH + 1f),
                                                size = Size(cellW - 2f, cellH - 2f)
                                            )
                                        }
                                    }
                                    // Draw split curtain line
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(splitX, 0f),
                                        end = Offset(splitX, canvasH),
                                        strokeWidth = 2.5f
                                    )
                                }
                                "SIDE_BY_SIDE" -> {
                                    val halfW = canvasW / 2f
                                    val subCellW = halfW / cols

                                    // Left: Scan A
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val col = getTargetColor((metrics.aArray[idx] - 250f) / 250f, 0f)
                                            drawRect(
                                                color = col,
                                                topLeft = Offset(x * subCellW + 0.5f, y * cellH + 0.5f),
                                                size = Size(subCellW - 1f, cellH - 1f)
                                            )
                                        }
                                    }

                                    // Right: Scan B
                                    for (y in 0 until rows) {
                                        for (x in 0 until cols) {
                                            val idx = y * cols + x
                                            val col = getTargetColor((metrics.bArray[idx] - 250f) / 250f, 0f)
                                            drawRect(
                                                color = col,
                                                topLeft = Offset(halfW + x * subCellW + 0.5f, y * cellH + 0.5f),
                                                size = Size(subCellW - 1f, cellH - 1f)
                                            )
                                        }
                                    }

                                    // Middle separator
                                    drawLine(
                                        color = CyberGold,
                                        start = Offset(halfW, 0f),
                                        end = Offset(halfW, canvasH),
                                        strokeWidth = 2f
                                    )
                                }
                            }

                            // Selected coordinate crosshair
                            if (selectedCoord != null) {
                                val (cx, cy) = selectedCoord!!
                                val crossLeft = cx * cellW
                                val crossTop = cy * cellH

                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(crossLeft, crossTop),
                                    size = Size(cellW, cellH),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(DeepNavy, RoundedCornerShape(2.dp)))
                            Text("-Δ افت/حفره", color = GrayText, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF141922), RoundedCornerShape(2.dp)))
                            Text("بدون تغییر", color = GrayText, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(CyberRed, RoundedCornerShape(2.dp)))
                            Text("+Δ فلز/افزایش", color = GrayText, fontSize = 9.sp)
                        }
                    }
                }
            }

            // 6. Geophysical Assessment Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = CyberGold, modifier = Modifier.size(20.dp))
                            Text("گزارش تحلیل ژئوفیزیکی تغییرات زمانی", color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            color = if (metrics.similarityPercent > 80) LaserGreen.copy(alpha = 0.15f) else CyberRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (metrics.similarityPercent > 80) LaserGreen else CyberRed)
                        ) {
                            Text(
                                "تشابه: ${metrics.similarityPercent}%",
                                color = if (metrics.similarityPercent > 80) LaserGreen else CyberRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Metric Indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBg, RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("تغییر مساحت", color = GrayText, fontSize = 9.sp, maxLines = 1)
                            val pct = ((metrics.changedCellsCount.toFloat() / metrics.totalCells) * 100).toInt()
                            Text("$pct% ($metrics.changedCellsCount نقطه)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("اوج افزایش (+Δ)", color = CyberRed, fontSize = 9.sp, maxLines = 1)
                            Text("+${metrics.maxPositiveDelta.toInt()} ADC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.White.copy(alpha = 0.12f))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("اوج کاهش (-Δ)", color = CyberCyan, fontSize = 9.sp, maxLines = 1)
                            Text("${metrics.maxNegativeDelta.toInt()} ADC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    // Diagnostic text
                    Surface(
                        color = CardBg.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = LaserGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = metrics.diagnosticSummary,
                                color = LightText,
                                fontSize = 11.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // AI Deep Inspection Button
                    Button(
                        onClick = onNavigateToAi,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("compare_to_ai_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاوره با هوش مصنوعی برای تفسیر تغییرات", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Empty State
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = CyberGold, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("دو اسکن جهت مقایسه انتخاب نشده‌اند", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "لطفاً در کارت‌های بالا، اسکن مبنا (A) و اسکن ثانویه (B) را برگزینید یا از دکمه شبیه‌ساز اسکن دوره‌ای استفاده کنید.",
                        color = GrayText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Modal Sheet / Dialog to Pick a Scan
    if (showPickerForSlot != null) {
        val slot = showPickerForSlot!!
        AlertDialog(
            onDismissRequest = { showPickerForSlot = null },
            title = {
                Text(
                    text = if (slot == "A") "انتخاب اسکن مبنا (اسکن A)" else "انتخاب اسکن ثانویه (اسکن B)",
                    color = CyberGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (savedScans.isEmpty()) {
                        Text("هیچ اسکنی در تاریخچه ذخیره نشده است.", color = GrayText, fontSize = 12.sp)
                    } else {
                        savedScans.forEach { scan ->
                            val isSelected = if (slot == "A") scan.id == scanA?.id else scan.id == scanB?.id
                            Card(
                                onClick = {
                                    if (slot == "A") viewModel.setComparisonScanA(scan)
                                    else viewModel.setComparisonScanB(scan)
                                    showPickerForSlot = null
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) CyberCyan.copy(alpha = 0.2f) else CardBg
                                ),
                                border = BorderStroke(1.dp, if (isSelected) CyberCyan else CardBorder),
                                modifier = Modifier.fillMaxWidth().testTag("pick_scan_${scan.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = scan.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(scan.timestamp))
                                        Text(
                                            text = "$dateStr | ${scan.gridWidth}×${scan.gridLength} | ${scan.soilType}",
                                            color = GrayText,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerForSlot = null }) {
                    Text("بستن", color = CyberCyan)
                }
            },
            containerColor = SurfaceBg
        )
    }

    // Modal Dialog to generate simulated time-lapse twin
    if (showSimulateDialog && scanA != null) {
        val base = scanA!!
        AlertDialog(
            onDismissRequest = { showSimulateDialog = false },
            title = {
                Text("شبیه‌سازی اسکن دوره‌ای (Generate Rescan)", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "یک اسکن مجدد بر اساس «${base.name.take(20)}...» با سناریوهای ژئوفیزیکی زیر شبیه‌سازی کنید:",
                        color = GrayText,
                        fontSize = 11.sp
                    )

                    Button(
                        onClick = {
                            viewModel.generateTimeLapseSimulatedScan(base, "EXCAVATION")
                            showSimulateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("۱. ترانشه حفاری و تخلیه خاک (افت سیگنال)", color = Color.White, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.generateTimeLapseSimulatedScan(base, "NEW_TARGET")
                            showSimulateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CyberGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("۲. پیدایش هدف فلزی جدید (رشد مغناطیسی)", color = Color.White, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.generateTimeLapseSimulatedScan(base, "SUBSIDENCE")
                            showSimulateDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, LaserGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("۳. نشست طبیعی و رطوبت زمین (تغییر تدریجی)", color = Color.White, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSimulateDialog = false }) {
                    Text("انصراف", color = GrayText)
                }
            },
            containerColor = SurfaceBg
        )
    }
}
