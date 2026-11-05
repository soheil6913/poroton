package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun TrackerScreen(viewModel: VisualizerViewModel) {
    val adcValue by viewModel.sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by viewModel.sensorManager.phaseShift.collectAsStateWithLifecycle()
    val magneticFluxUt by viewModel.sensorManager.magneticFluxUt.collectAsStateWithLifecycle()
    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val compassHeading by viewModel.sensorManager.compassHeading.collectAsStateWithLifecycle()

    var audioFeedbackEnabled by remember { mutableStateOf(true) }
    var peakMaxAdc by remember { mutableStateOf(250) }
    var peakMinAdc by remember { mutableStateOf(250) }

    // Keep rolling history of last 50 ADC values for the live oscilloscope
    val history = remember { mutableStateListOf<Int>() }
    LaunchedEffect(adcValue) {
        history.add(adcValue)
        if (history.size > 50) {
            history.removeAt(0)
        }
        if (adcValue > peakMaxAdc) peakMaxAdc = adcValue
        if (adcValue < peakMinAdc) peakMinAdc = adcValue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "رهگیری زنده و اوسیلوسکوپ (Live Tracker)",
                    color = CyberGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "نمایش بی‌درنگ شار مغناطیسی سنسور مگنتومتر",
                    color = GrayText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    peakMaxAdc = adcValue
                    peakMinAdc = adcValue
                    history.clear()
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Peaks", tint = CyberCyan)
            }
        }

        // Live Flux Gauge Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Arc Gauge
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 12f
                        val radius = (size.minDimension - strokeW) / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Background arc
                        drawArc(
                            color = CardBg,
                            startAngle = 140f,
                            sweepAngle = 260f,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeW)
                        )

                        // Active arc
                        val sweep = ((adcValue / 1024f) * 260f).coerceIn(0f, 260f)
                        val gaugeColor = when {
                            adcValue > 550 -> CyberRed
                            adcValue > 350 -> BrightYellow
                            adcValue < 150 -> DeepNavy
                            else -> LaserGreen
                        }

                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(CyberCyan, LaserGreen, BrightYellow, CyberRed),
                                center = center
                            ),
                            startAngle = 140f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeW)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$adcValue",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.2f µT", magneticFluxUt),
                            color = CyberCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (adcValue > 300) "+ فلز مثبت" else if (adcValue < 200) "- حفره منفی" else "خاک نرمال",
                            color = if (adcValue > 300) CyberRed else if (adcValue < 200) CyberCyan else LaserGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Max and Min Peak Readouts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("بیشترین قله (+Peak)", color = CyberRed, fontSize = 10.sp)
                        Text("$peakMaxAdc ADC", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.White.copy(alpha = 0.15f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("کمترین حفره (-Void)", color = CyberCyan, fontSize = 10.sp)
                        Text("$peakMinAdc ADC", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.White.copy(alpha = 0.15f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("فاز متناظر", color = CyberGold, fontSize = 10.sp)
                        Text("$phaseShift°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Oscilloscope Waveform Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نمودار موج رادیویی (Oscilloscope)", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("پایش زمان واقعی", color = GrayText, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Wave Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C1018))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(8.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Grid lines
                        val gridCols = 8
                        for (i in 0..gridCols) {
                            val x = (w / gridCols) * i
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1f
                            )
                        }
                        // Center baseline
                        val midY = h * 0.5f
                        drawLine(
                            color = LaserGreen.copy(alpha = 0.25f),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1.5f
                        )

                        // Draw live wave line
                        if (history.size > 1) {
                            val path = Path()
                            val stepX = w / 49f

                            history.forEachIndexed { i, valSample ->
                                val normY = (1f - ((valSample - 100f) / 700f)).coerceIn(0.05f, 0.95f)
                                val x = i * stepX
                                val y = normY * h
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = CyberCyan,
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons: Ground Balance Calibration & Audio Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.sensorManager.calibrateGroundBalance() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ground_balance_button")
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("بالانس خاک (Ground Balance)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { audioFeedbackEnabled = !audioFeedbackEnabled },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (audioFeedbackEnabled) CyberGold else CardBorder),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    if (audioFeedbackEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = if (audioFeedbackEnabled) CyberGold else GrayText
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (audioFeedbackEnabled) "صدا فعال" else "بی‌صدا",
                    color = if (audioFeedbackEnabled) CyberGold else GrayText,
                    fontSize = 11.sp
                )
            }
        }
    }
}
