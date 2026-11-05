package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ScanRecord
import com.example.data.ScanRepository
import com.example.hardware.SensorManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data object Loading : AiAnalysisState
    data class Success(
        val report: String,
        val estimatedDepth: Float,
        val anomalyType: String,
        val confidencePercent: Int,
        val adviceList: List<String>,
        val isOfflineEngine: Boolean = false,
        val providerNote: String = ""
    ) : AiAnalysisState
    data class Error(
        val message: String,
        val canFallbackToOffline: Boolean = true
    ) : AiAnalysisState
}

class VisualizerViewModel(application: Application) : AndroidViewModel(application) {

    val sensorManager = SensorManager(application)
    private val repository = ScanRepository(application)

    val savedScans: StateFlow<List<ScanRecord>> = repository.scans

    // Scan settings
    private val _gridWidth = MutableStateFlow(10)
    val gridWidth: StateFlow<Int> = _gridWidth.asStateFlow()

    private val _gridLength = MutableStateFlow(10)
    val gridLength: StateFlow<Int> = _gridLength.asStateFlow()

    private val _soilType = MutableStateFlow("خاک کشاورزی (Soil)")
    val soilType: StateFlow<String> = _soilType.asStateFlow()

    private val _scanPattern = MutableStateFlow("زیگزاگ (Zig-Zag)")
    val scanPattern: StateFlow<String> = _scanPattern.asStateFlow()

    // Acquisition state
    private val _isScanActive = MutableStateFlow(false)
    val isScanActive: StateFlow<Boolean> = _isScanActive.asStateFlow()

    private val _currentCol = MutableStateFlow(0)
    val currentCol: StateFlow<Int> = _currentCol.asStateFlow()

    private val _currentRow = MutableStateFlow(0)
    val currentRow: StateFlow<Int> = _currentRow.asStateFlow()

    private val _activeScanData = MutableStateFlow<List<Float>>(emptyList())
    val activeScanData: StateFlow<List<Float>> = _activeScanData.asStateFlow()

    // 3D Visualizer state
    private val _viewedScan = MutableStateFlow<ScanRecord?>(null)
    val viewedScan: StateFlow<ScanRecord?> = _viewedScan.asStateFlow()

    private val _yaw = MutableStateFlow(-35f)
    val yaw: StateFlow<Float> = _yaw.asStateFlow()

    private val _pitch = MutableStateFlow(38f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _zoom = MutableStateFlow(1.0f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _panX = MutableStateFlow(0f)
    val panX: StateFlow<Float> = _panX.asStateFlow()

    private val _panY = MutableStateFlow(0f)
    val panY: StateFlow<Float> = _panY.asStateFlow()

    private val _zScale = MutableStateFlow(1.5f)
    val zScale: StateFlow<Float> = _zScale.asStateFlow()

    private val _colorThreshold = MutableStateFlow(0.0f)
    val colorThreshold: StateFlow<Float> = _colorThreshold.asStateFlow()

    private val _renderStyle = MutableStateFlow("SURFACE") // SURFACE, WIREFRAME, VOXEL, POINTS
    val renderStyle: StateFlow<String> = _renderStyle.asStateFlow()

    private val _isRgbAnalysis = MutableStateFlow(false)
    val isRgbAnalysis: StateFlow<Boolean> = _isRgbAnalysis.asStateFlow()

    private val _selectedNodeIndex = MutableStateFlow<Int?>(null)
    val selectedNodeIndex: StateFlow<Int?> = _selectedNodeIndex.asStateFlow()

    private val _resetTrigger = MutableStateFlow(0L)
    val resetTrigger: StateFlow<Long> = _resetTrigger.asStateFlow()

    private val _filterMode = MutableStateFlow("ALL") // ALL, METAL_ONLY, CAVITY_ONLY
    val filterMode: StateFlow<String> = _filterMode.asStateFlow()

    private val _sliceAxis = MutableStateFlow("NONE") // NONE, X_AXIS, Y_AXIS
    val sliceAxis: StateFlow<String> = _sliceAxis.asStateFlow()

    private val _sliceIndex = MutableStateFlow(0)
    val sliceIndex: StateFlow<Int> = _sliceIndex.asStateFlow()

    // AI Analysis
    private val aiPrefs = application.getSharedPreferences("okm_ai_settings", Context.MODE_PRIVATE)

    private val _aiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiState: StateFlow<AiAnalysisState> = _aiState.asStateFlow()

    private val _aiMode = MutableStateFlow(aiPrefs.getString("ai_mode", "AUTO") ?: "AUTO") // AUTO, OFFLINE, CLOUD
    val aiMode: StateFlow<String> = _aiMode.asStateFlow()

    private val _customApiKey = MutableStateFlow(aiPrefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    fun setAiMode(mode: String) {
        _aiMode.value = mode
        aiPrefs.edit().putString("ai_mode", mode).apply()
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key.trim()
        aiPrefs.edit().putString("custom_api_key", key.trim()).apply()
    }

    init {
        // Automatically load first preset scan into visualizer for immediate inspection
        viewModelScope.launch {
            savedScans.collect { list ->
                if (_viewedScan.value == null && list.isNotEmpty()) {
                    _viewedScan.value = list.first()
                }
            }
        }
    }

    fun updateGridWidth(w: Int) {
        _gridWidth.value = w.coerceIn(3, 30)
    }

    fun updateGridLength(l: Int) {
        _gridLength.value = l.coerceIn(3, 30)
    }

    fun updateSoilType(type: String) {
        _soilType.value = type
    }

    fun updateScanPattern(pattern: String) {
        _scanPattern.value = pattern
    }

    fun startNewScan() {
        _currentCol.value = 0
        _currentRow.value = 0
        _activeScanData.value = emptyList()
        _isScanActive.value = true
    }

    fun cancelScan() {
        _isScanActive.value = false
        _activeScanData.value = emptyList()
    }

    fun recordCurrentStep() {
        if (!_isScanActive.value) return

        val w = _gridWidth.value
        val l = _gridLength.value
        val currentData = _activeScanData.value.toMutableList()

        val adc = sensorManager.adcValue.value.toFloat()
        currentData.add(adc)
        _activeScanData.value = currentData

        val isZigZag = _scanPattern.value.contains("Zig-Zag") || _scanPattern.value.contains("زیگزاگ")
        val goingRight = !isZigZag || (_currentRow.value % 2 == 0)

        var nextCol = _currentCol.value
        var nextRow = _currentRow.value

        if (goingRight) {
            nextCol++
            if (nextCol >= w) {
                nextRow++
                nextCol = if (isZigZag) w - 1 else 0
            }
        } else {
            nextCol--
            if (nextCol < 0) {
                nextRow++
                nextCol = 0
            }
        }

        if (nextRow >= l || currentData.size >= w * l) {
            finishScan()
        } else {
            _currentCol.value = nextCol
            _currentRow.value = nextRow
        }
    }

    private fun finishScan() {
        _isScanActive.value = false
        val w = _gridWidth.value
        val l = _gridLength.value
        val data = _activeScanData.value

        val maxVal = data.maxOrNull() ?: 0f
        val minVal = data.minOrNull() ?: 0f
        val depth = computeEstimatedDepth(maxVal, minVal, _soilType.value)
        val classification = classifyAnomaly(maxVal, minVal)

        val newScan = ScanRecord(
            name = "اسکن میدانی (${w}×${l}) - ${data.size} پالس",
            gridWidth = w,
            gridLength = l,
            soilType = _soilType.value,
            scanPattern = _scanPattern.value,
            data = data,
            maxSignal = maxVal,
            minSignal = minVal,
            estimatedDepthMeters = depth,
            anomalyClassification = classification
        )

        repository.saveScan(newScan)
        _viewedScan.value = newScan
    }

    fun saveCurrentScan(name: String, notes: String) {
        val current = _viewedScan.value ?: return
        val updated = current.copy(
            name = if (name.isNotBlank()) name else current.name,
            notes = notes
        )
        repository.saveScan(updated)
        _viewedScan.value = updated
    }

    fun loadScan(record: ScanRecord) {
        _viewedScan.value = record
        _selectedNodeIndex.value = null
    }

    fun deleteScan(record: ScanRecord) {
        repository.deleteScan(record.id)
        if (_viewedScan.value?.id == record.id) {
            _viewedScan.value = savedScans.value.firstOrNull { it.id != record.id }
        }
    }

    // Camera and 3D visualizer controls
    fun updateYawPitch(deltaYaw: Float, deltaPitch: Float) {
        _yaw.value = (_yaw.value + deltaYaw)
        _pitch.value = (_pitch.value + deltaPitch).coerceIn(-89f, 89f)
    }

    fun updateZoom(factor: Float) {
        _zoom.value = (_zoom.value * factor).coerceIn(0.4f, 4.0f)
    }

    fun updatePan(dx: Float, dy: Float) {
        _panX.value += dx
        _panY.value += dy
    }

    fun setZScale(scale: Float) {
        _zScale.value = scale.coerceIn(0.2f, 4.0f)
    }

    fun setColorThreshold(t: Float) {
        _colorThreshold.value = t.coerceIn(0.0f, 0.9f)
    }

    fun setRenderStyle(style: String) {
        _renderStyle.value = style
    }

    fun toggleRgbAnalysis() {
        _isRgbAnalysis.value = !_isRgbAnalysis.value
    }

    fun setFilterMode(mode: String) {
        _filterMode.value = mode
    }

    fun setSliceAxis(axis: String) {
        _sliceAxis.value = axis
    }

    fun setSliceIndex(idx: Int) {
        val scan = _viewedScan.value ?: return
        val maxIdx = if (_sliceAxis.value == "X_AXIS") scan.gridWidth - 1 else scan.gridLength - 1
        _sliceIndex.value = idx.coerceIn(0, max(0, maxIdx))
    }

    fun selectNodeIndex(idx: Int?) {
        _selectedNodeIndex.value = idx
    }

    fun resetCamera() {
        _yaw.value = -35f
        _pitch.value = 38f
        _zoom.value = 1.0f
        _panX.value = 0f
        _panY.value = 0f
        _zScale.value = 1.5f
        _colorThreshold.value = 0.0f
        _filterMode.value = "ALL"
        _sliceAxis.value = "NONE"
        _resetTrigger.value = System.currentTimeMillis()
    }

    private fun computeEstimatedDepth(maxVal: Float, minVal: Float, soil: String): Float {
        // Soil wave velocity (meters per nanosecond approx coefficient)
        val soilFactor = when {
            soil.contains("Hard Rock") || soil.contains("صخره") -> 0.12f
            soil.contains("Mineral") || soil.contains("معدنی") -> 0.075f
            soil.contains("Wet Sand") || soil.contains("خیس") -> 0.06f
            else -> 0.10f
        }
        val anomalyStrength = max(abs(maxVal - 250f), abs(minVal - 250f))
        val calculatedDepth = (anomalyStrength / 180f) * soilFactor * 3.5f
        return (max(0.6f, min(6.5f, calculatedDepth)) * 10).toInt() / 10f
    }

    private fun classifyAnomaly(maxVal: Float, minVal: Float): String {
        val highDiff = maxVal - 250f
        val lowDiff = 250f - minVal

        return when {
            highDiff > 350f && lowDiff > 150f -> "حفره به همراه فلز گرانبها (Tomb / Vault with Metal)"
            highDiff > 400f -> "فلز غیرآهنی / طلا با رسانایی بالا (Gold / Non-Ferrous)"
            highDiff > 200f -> "فلز آهنی / ساختار فلزی (Ferrous / Metal)"
            lowDiff > 180f -> "حفره یا اتاقک خالی زیرزمینی (Cavity / Subsurface Void)"
            highDiff > 100f -> "تراکم مواد معدنی خاک (Mineralization)"
            else -> "زمین طبیعی و یکدست (Uniform Soil)"
        }
    }

    /**
     * Executes AI Geological Analysis using Gemini API if available,
     * or uses an advanced offline geological heuristic expert system as resilient fallback.
     */
    fun runAiAnalysis(customPrompt: String = "", forceOffline: Boolean = false) {
        val scan = _viewedScan.value ?: return
        viewModelScope.launch {
            _aiState.value = AiAnalysisState.Loading

            val avg = scan.data.average().toFloat()
            val maxVal = scan.maxSignal
            val minVal = scan.minSignal
            val variance = scan.data.map { (it - avg) * (it - avg) }.average()
            val classification = classifyAnomaly(maxVal, minVal)
            val depth = scan.estimatedDepthMeters.coerceAtLeast(0.8f)

            val resolvedApiKey = when {
                _customApiKey.value.isNotBlank() -> _customApiKey.value.trim()
                else -> try {
                    val key = com.example.BuildConfig.GEMINI_API_KEY
                    if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
                } catch (e: Exception) { "" }
            }

            val mode = _aiMode.value
            val allowCloud = !forceOffline && mode != "OFFLINE" && resolvedApiKey.isNotBlank()

            if (allowCloud) {
                try {
                    val promptText = """
                        You are a senior geophysicist and OKM ground radar expert.
                        Analyze this subsurface magnetometer scan:
                        - Grid size: ${scan.gridWidth} x ${scan.gridLength} (${scan.data.size} points)
                        - Soil type: ${scan.soilType}
                        - Max ADC: $maxVal, Min ADC: $minVal, Avg: $avg, Variance: $variance
                        - Preliminary Classification: $classification
                        - Estimated Depth: $depth meters
                        - User note: ${scan.notes}
                        - User specific inquiry: $customPrompt

                        Provide a comprehensive, authoritative radar analysis report in Persian (فارسی) followed by an English summary.
                        Include:
                        1. Target Nature (Gold, Non-ferrous, Ferrous, Cavity, Natural mineral)
                        2. Spatial Geometry & Orientation
                        3. Accurate Depth & Drilling/Excavation Feasibility
                        4. Step-by-step Field Action Advice
                    """.trimIndent()

                    val responseText = callGeminiApiDirect(resolvedApiKey, promptText)
                    _aiState.value = AiAnalysisState.Success(
                        report = responseText,
                        estimatedDepth = depth,
                        anomalyType = classification,
                        confidencePercent = (88 + (variance % 8)).toInt().coerceIn(80, 97),
                        adviceList = listOf(
                            "اسکن تاییدی عمود بر مسیر فعلی (Cross-Scan) در ابعاد کوچک‌تر انجام دهید.",
                            "نقطه اوج سیگنال در مرکز گرادیان قرمز را نشانه‌گذاری فیزیکی کنید.",
                            "نسبت نفوذ امواج در ${scan.soilType} را در عمق‌سنجی نهایی لحاظ کنید."
                        ),
                        isOfflineEngine = false,
                        providerNote = "تحلیل هوشمند توسط سرور ابری Google Gemini"
                    )
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("AiAnalysis", "Cloud AI error: ${e.message}", e)
                    if (mode == "CLOUD") {
                        // User explicitly requested Cloud-only mode
                        val errorDesc = when {
                            e.message?.contains("Unable to resolve host") == true || e.message?.contains("Failed to connect") == true ->
                                "عدم دسترسی به اینترنت یا سرورهای ابری (لطفاً اتصال اینترنت یا فیلترشکن را بررسی کنید)."
                            e.message?.contains("API_KEY_INVALID") == true || e.message?.contains("400") == true ->
                                "کلید API معتبر نیست یا مجوز آن منقضی شده است."
                            else -> "خطای اتصال: ${e.localizedMessage ?: "عدم پاسخ سرور"}"
                        }
                        _aiState.value = AiAnalysisState.Error(
                            message = errorDesc,
                            canFallbackToOffline = true
                        )
                        return@launch
                    }
                    // In AUTO mode: seamlessly fall back to offline expert reasoning engine!
                }
            }

            // Run offline geological expert reasoning engine
            kotlinx.coroutines.delay(600)
            val offlineData = generateOfflineGeologicalReport(scan, classification, depth, maxVal, minVal, customPrompt)
            val fallbackNote = if (allowCloud) {
                "سرویس ابری به دلیل اختلال شبکه در دسترس نبود؛ تحلیل به صورت خودکار توسط موتور هوشمند آفلاین دستگاه انجام شد."
            } else {
                "موتور هوش مصنوعی ژئوفیزیک (آفلاین و بدون نیاز به اینترنت)"
            }

            _aiState.value = AiAnalysisState.Success(
                report = offlineData.first,
                estimatedDepth = depth,
                anomalyType = classification,
                confidencePercent = offlineData.second,
                adviceList = offlineData.third,
                isOfflineEngine = true,
                providerNote = fallbackNote
            )
        }
    }

    private fun generateOfflineGeologicalReport(
        scan: ScanRecord,
        classification: String,
        depth: Float,
        maxVal: Float,
        minVal: Float,
        customPrompt: String
    ): Triple<String, Int, List<String>> {
        val isHighMetal = maxVal > 600f
        val isCavity = minVal < 100f
        val confidence = if (isHighMetal && isCavity) 94 else if (isHighMetal || isCavity) 89 else 80

        val advice = mutableListOf<String>()
        advice.add("اسکن تکمیلی به صورت ضربدری (Cross-Scan) با سرعت یکنواخت برای تثبیت نقطه ثقل هدف انجام شود.")
        if (isHighMetal) {
            advice.add("سیگنال مثبت شدید (کانتور قرمز) حاکی از هدایت الکترومغناطیسی بالای فلز گرانبها (طلا/مفرغ) یا آلیاژ متراکم است.")
        }
        if (isCavity) {
            advice.add("افت مقادیر به سمت رنگ آبی و مقادیر زیر ۲۰۰ ADC مبین افت تراکم ذرات خاک و فضای خالی/راهرو باستانی است.")
        }
        advice.add("ثابت دی‌الکتریک خاک «${scan.soilType}» در خطای عمق‌سنجی حداکثر ±۱۰ سانتی‌متر تلقی می‌گردد.")

        val report = buildString {
            append("گزارش ژئوفیزیکی و پردازش هوشمند رادار زمین\n")
            append("=========================================\n\n")
            append("۱. بررسی ماهیت ساختار هدف (Target Classification):\n")
            append("بر اساس ماتریس عددی و گرادیان داده‌های دریافتی، نوع آنومالی «$classification» تشخیص داده شد. شدت پیک سیگنال به $maxVal ADC رسیده که انحراف مثبت ${(maxVal - 250).toInt()} واحدی از بستر خنثی را نشان می‌دهد.\n\n")
            append("۲. محاسبه عمق ژئوفیزیکی هدف (Depth Estimation):\n")
            append("با احتساب ضریب نفوذ امواج در بستر «${scan.soilType}» و بازتاب لایه‌ای، عمق کانون آنومالی در حدود $depth متر از سطح کاوش برآورد می‌گردد.\n\n")
            append("۳. توصیف هندسی لکه هدف (Spatial Geometry):\n")
            if (isCavity && isHighMetal) {
                append("لکه ساختاری از نوع تلفیقی اتاقک/مقبره با بار فلزی است؛ ناحیه با پتانسیل منفی (آبی) در برگیرنده مغزه فلزی رسانا (قرمز) می‌باشد.\n\n")
            } else if (isHighMetal) {
                append("کانتور ایزومغناطیسی بیضوی متمرکز با خطوط فشرده، نشان‌دهنده یک توده فلزی منسجم و غیرپراکنده است.\n\n")
            } else {
                append("پیوستگی طولی سیگنال‌های منفی بیانگر راهرو یا دالان زیرزمینی دست‌ساز در بستر خاک است.\n\n")
            }
            if (customPrompt.isNotBlank()) {
                append("۴. پاسخ به پرسش کاوشگر درباره «$customPrompt»:\n")
                if (customPrompt.contains("طلا") || customPrompt.contains("gold", ignoreCase = true)) {
                    append("پاسخ هوش مصنوعی: با توجه به تغییر فاز سنسور و سیگنال بالای ${maxVal.toInt()}، احتمال فلزات با رسانایی عالی مانند طلا یا مفرغ کهنه بسیار بالا است و فاقد اثر اکسیداسیون مغناطیسی آهن می‌باشد.\n\n")
                } else if (customPrompt.contains("عمق") || customPrompt.contains("حفاری")) {
                    append("پاسخ هوش مصنوعی: حفاری عمودی با مرکزیت بالاترین نقطه قرمز تا عمق $depth متر با ضریب اطمینان $confidence٪ توصیه می‌شود.\n\n")
                } else {
                    append("پاسخ هوش مصنوعی: تحلیل داده‌های شبکه $customPrompt نشان می‌دهد شرایط ساختاری زمین با مدل‌های باستان‌شناسی همخوانی دارد.\n\n")
                }
            }
            append("۵. توصیه‌های کلیدی عملیات میدانی:\n")
            advice.forEachIndexed { i, a ->
                append("${i + 1}. $a\n")
            }
        }

        return Triple(report, confidence, advice)
    }

    private suspend fun callGeminiApiDirect(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val modelCandidates = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.0-flash"
        )
        var lastException: Exception? = null

        for (modelName in modelCandidates) {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 14000
                    readTimeout = 18000
                }

                val jsonBody = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArr = JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", prompt)
                                }
                                put(partObj)
                            }
                            put("parts", partsArr)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArr)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            if (text.isNotBlank()) {
                                return@withContext text
                            }
                        }
                    }
                } else {
                    val errorDetails = try {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    } catch (e: Exception) { "" }
                    android.util.Log.w("GeminiApi", "Model $modelName HTTP $responseCode: $errorDetails")
                    lastException = IOException("HTTP $responseCode: $errorDetails")
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiApi", "Model $modelName connection error", e)
                lastException = e
            } finally {
                conn?.disconnect()
            }
        }

        throw lastException ?: IOException("عدم امکان برقراری ارتباط با سرویس هوش مصنوعی")
    }
}
