package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ScanRecord
import com.example.ui.theme.*
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: VisualizerViewModel,
    onLoadScan: () -> Unit,
    onCompareScan: (ScanRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var exportScanTarget by remember { mutableStateOf<ScanRecord?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importScanFromUri(context, uri, autoAnalyze = false) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

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

            // Import scan file button
            FilledTonalButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf("*/*", "text/*", "text/csv", "application/json", "text/plain")
                    )
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CyberCyan.copy(alpha = 0.15f),
                    contentColor = CyberCyan
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("وارد کردن فایل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

        // Comparison Launch Banner
        Card(
            onClick = {
                val first = savedScans.firstOrNull()
                if (first != null) onCompareScan(first)
            },
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("history_compare_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ابزار مقایسه و هم‌پوشانی اسکن‌ها (Time-Lapse Overlay)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("انطباق دو اسکن جهت تشخیص حفاری و تغییرات آنومالی در گذر زمان", color = GrayText, fontSize = 9.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GrayText)
            }
        }

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
                        onCompare = {
                            onCompareScan(scan)
                        },
                        onExport = {
                            exportScanTarget = scan
                        },
                        onDelete = {
                            viewModel.deleteScan(scan)
                        }
                    )
                }
            }
        }

        // Export Dialog for Selected History Scan
        if (exportScanTarget != null) {
            val target = exportScanTarget!!
            AlertDialog(
                onDismissRequest = { exportScanTarget = null },
                containerColor = SurfaceBg,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CyberGold, modifier = Modifier.size(22.dp))
                        Text("خروجی و گزارش رسمی (Site Export)", color = CyberGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "فرمت گزارش فنی برای «${target.name.take(22)}...» را انتخاب کنید:",
                            color = GrayText,
                            fontSize = 11.sp
                        )

                        // PDF Report Card
                        Card(
                            onClick = {
                                val t = target
                                exportScanTarget = null
                                ReportExporter.exportPdf(context, t)
                            },
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberRed.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CyberRed, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("گزارش مهندسی رسمی (PDF Report)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("شامل نقشه حرارتی ۲D، جدول اهداف، تخمین عمق، متادیتا و امضا.", color = GrayText, fontSize = 9.5.sp)
                                }
                            }
                        }

                        // CSV Data Card
                        Card(
                            onClick = {
                                val t = target
                                exportScanTarget = null
                                ReportExporter.exportCsv(context, t)
                            },
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TableChart, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("مجموعه داده‌های خام (CSV Dataset)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("ماتریس شبکه و جدول تک‌تک پالس‌ها (X, Y, ADC) سازگار با اکسل.", color = GrayText, fontSize = 9.5.sp)
                                }
                            }
                        }

                        // Quick Copy to Clipboard
                        OutlinedButton(
                            onClick = {
                                ReportExporter.copyCsvToClipboard(context, target)
                                exportScanTarget = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GrayText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("کپی سریع ماتریس در کلیپ‌بورد", color = LightText, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exportScanTarget = null }) {
                        Text("انصراف", color = GrayText)
                    }
                }
            )
        }
    }
}

@Composable
fun ScanHistoryCard(
    scan: ScanRecord,
    onLoad: () -> Unit,
    onCompare: () -> Unit = {},
    onExport: () -> Unit = {},
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = scan.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$formattedDate | ${scan.gridWidth}×${scan.gridLength}",
                    color = GrayText,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CyberGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = scan.anomalyClassification,
                            color = CyberGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
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
                            maxLines = 1,
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCompare,
                        modifier = Modifier.size(28.dp).testTag("compare_scan_${scan.id}")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Compare", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(28.dp).testTag("export_scan_${scan.id}")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Report", tint = CyberGold, modifier = Modifier.size(16.dp))
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
}
