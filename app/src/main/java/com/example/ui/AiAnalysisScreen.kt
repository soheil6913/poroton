package com.example.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@Composable
fun AiAnalysisScreen(viewModel: VisualizerViewModel) {
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val aiMode by viewModel.aiMode.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()

    var userPrompt by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(customApiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyberGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberGold, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(
                        text = "تحلیل هوش مصنوعی رادار (AI Geological Inspector)",
                        color = CyberGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تفسیر امواج مغناطیسی، تشخیص طلا، حفره و تعیین عمق",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = {
                    tempApiKey = customApiKey
                    showApiKeyDialog = true
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
            ) {
                Icon(Icons.Default.Key, contentDescription = "API Key", tint = CyberCyan, modifier = Modifier.size(20.dp))
            }
        }

        if (viewedScan == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("هیچ اسکن فعالی جهت تحلیل انتخاب نشده است.", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ابتدا از تب تاریخچه یا اسکن، یک اسکن را انتخاب کنید.", color = GrayText, fontSize = 11.sp)
                }
            }
        } else {
            val scan = viewedScan!!

            // Active Scan Summary Card
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
                        Text(scan.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Surface(
                            color = CyberCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "شبکه: ${scan.gridWidth}×${scan.gridLength}",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBg, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("نوع خاک", color = GrayText, fontSize = 9.sp)
                            Text(scan.soilType, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("اوج مثبت (فلز)", color = CyberRed, fontSize = 9.sp)
                            Text("${scan.maxSignal.toInt()} ADC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("حداقل (حفره)", color = CyberCyan, fontSize = 9.sp)
                            Text("${scan.minSignal.toInt()} ADC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Mode Selector Chips
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("موتور هوش مصنوعی (AI Engine Mode):", color = GrayText, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "AUTO" to "هوشمند / خودکار",
                                "OFFLINE" to "آفلاین (بدون اینترنت)",
                                "CLOUD" to "ابری Google"
                            ).forEach { (mKey, mLabel) ->
                                val selected = aiMode == mKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAiMode(mKey) },
                                    label = { Text(mLabel, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberGold,
                                        containerColor = CardBg,
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Prompt Input
                    OutlinedTextField(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        placeholder = { Text("سوال یا دستور خاص (مثلاً: آیا احتمال طلای دست‌ساز هست؟)", color = GrayText, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runAiAnalysis(userPrompt, forceOffline = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("run_ai_analysis_button")
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شروع تحلیل ژئوفیزیکی", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.runAiAnalysis(userPrompt, forceOffline = true) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("run_offline_analysis_button")
                        ) {
                            Icon(Icons.Default.OfflinePin, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحلیل آفلاین", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Results State Display
            when (val state = aiState) {
                is AiAnalysisState.Idle -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(28.dp))
                            Text(
                                "هوش مصنوعی با پردازش همبستگی شدت سیگنال، شیب گرادیان و سرعت امواج در لایه‌های خاک، ماهیت و عمق هدف را مشخص می‌کند. در مناطق بدون پوشش اینترنت، موتور آفلاین به صورت کامل و دقیق عمل خواهد کرد.",
                                color = GrayText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                is AiAnalysisState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = CyberGold, strokeWidth = 3.dp)
                            Text("در حال مدل‌سازی ۳بعدی و تحلیل ناهنجاری‌ها...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("بررسی قطبش مغناطیسی و میرایی پالس در بستر خاک", color = GrayText, fontSize = 10.sp)
                        }
                    }
                }
                is AiAnalysisState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Provider / Status Note
                        if (state.providerNote.isNotBlank()) {
                            Surface(
                                color = if (state.isOfflineEngine) CardBg else CyberCyan.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (state.isOfflineEngine) CyberGold.copy(alpha = 0.4f) else CyberCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (state.isOfflineEngine) Icons.Default.OfflinePin else Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = if (state.isOfflineEngine) CyberGold else CyberCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        state.providerNote,
                                        color = if (state.isOfflineEngine) CyberGold else CyberCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Confidence & Target Nature Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CyberGold),
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
                                    Text("نتیجه تشخیص هوش مصنوعی", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Surface(
                                        color = CyberGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${state.confidencePercent}% اطمینان",
                                            color = CyberGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBg, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = LaserGreen, modifier = Modifier.size(24.dp))
                                    Column {
                                        Text("نوع ساختار هدف:", color = GrayText, fontSize = 10.sp)
                                        Text(state.anomalyType, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBg, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Straighten, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                                    Column {
                                        Text("عمق تخمینی دقیق:", color = GrayText, fontSize = 10.sp)
                                        Text("${state.estimatedDepth} متر زیر سطح زمین", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Detailed Report Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("گزارش فنی و تحلیل ژئوفیزیکی", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Text(
                                    text = state.report,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        // Actionable Advice Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("توصیه‌های میدانی کاوش (Field Advice)", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                state.adviceList.forEach { advice ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LaserGreen, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                        Text(advice, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                is AiAnalysisState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                            Text("خطا در برقراری ارتباط با سرویس ابری", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                state.message,
                                color = GrayText,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.canFallbackToOffline) {
                                    Button(
                                        onClick = { viewModel.runAiAnalysis(userPrompt, forceOffline = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OfflinePin, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تحلیل با موتور آفلاین", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.runAiAnalysis(userPrompt, forceOffline = false) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Text("تلاش مجدد", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = SurfaceBg,
            title = {
                Text("تنظیم کلید اختصاصی Google Gemini API", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "در صورت تمایل می‌توانید کلید API شخصی خود را از Google AI Studio وارد کنید. برنامه در صورت قطعی اینترنت به صورت هوشمند از موتور آفلاین استفاده می‌کند.",
                        color = GrayText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("Gemini API Key", color = GrayText) },
                        placeholder = { Text("AIzaSy...", color = GrayText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = CardBorder
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomApiKey(tempApiKey)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold)
                ) {
                    Text("ذخیره کلید", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("انصراف", color = GrayText)
                }
            }
        )
    }
}
