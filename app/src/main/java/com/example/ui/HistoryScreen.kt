package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ScanRecord
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: VisualizerViewModel,
    onLoadScan: () -> Unit
) {
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val filteredScans = remember(savedScans, searchQuery) {
        if (searchQuery.isBlank()) savedScans
        else savedScans.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.anomalyClassification.contains(searchQuery, ignoreCase = true) ||
            it.soilType.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "تاریخچه اسکن‌های زمین",
                    color = CyberGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${savedScans.size} اسکن ثبت‌شده و الگوهای کالیبراسیون",
                    color = GrayText,
                    fontSize = 11.sp
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجو در اسکن‌ها یا نوع هدف...", color = GrayText, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GrayText) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CyberGold,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = SurfaceBg,
                unfocusedContainerColor = SurfaceBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        if (filteredScans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("موردی یافت نشد", color = GrayText, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredScans, key = { it.id }) { scan ->
                    ScanHistoryCard(
                        scan = scan,
                        onLoad = {
                            viewModel.loadScan(scan)
                            onLoadScan()
                        },
                        onDelete = {
                            viewModel.deleteScan(scan)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScanHistoryCard(
    scan: ScanRecord,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(scan.timestamp) { dateFormat.format(Date(scan.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini 2D Heatmap Thumbnail
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C1018))
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = scan.gridWidth
                    val l = scan.gridLength
                    val cellW = size.width / w
                    val cellH = size.height / l

                    for (y in 0 until l) {
                        for (x in 0 until w) {
                            val idx = y * w + x
                            val v = scan.data.getOrNull(idx) ?: 250f
                            val norm = ((v - 250f) / 200f).coerceIn(-1f, 2f)
                            val col = getTargetColor(norm, 0f)

                            drawRect(
                                color = col,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }

            // Info details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "$formattedDate | ${scan.gridWidth}×${scan.gridLength}",
                    color = GrayText,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = CyberGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = scan.anomalyClassification,
                            color = CyberGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = CyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "عمق: ${scan.estimatedDepthMeters}m",
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("load_scan_${scan.id}")
                ) {
                    Text("مشاهده ۳D", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
