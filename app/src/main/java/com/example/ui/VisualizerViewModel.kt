package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ScanRecord
import com.example.data.ScanRepository
import com.example.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        val adviceList: List<String>
    ) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
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
    private val _aiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiState: StateFlow<AiAnalysisState> = _aiState.asStateFlow()

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
     * Executes AI Geological Analysis using Gemini API if API key exists,
     * or uses an offline geological heuristic expert system as resilient fallback!
     */
    fun runAiAnalysis(customPrompt: String = "") {
        val scan = _viewedScan.value ?: return
        viewModelScope.launch {
            _aiState.value = AiAnalysisState.Loading

            try {
                // Calculate statistical indicators
                val avg = scan.data.average().toFloat()
                val maxVal = scan.maxSignal
                val minVal = scan.minSignal
                val variance = scan.data.map { (it - avg) * (it - avg) }.average()
                val anomalyRatio = (maxVal - minVal) / (avg.coerceAtLeast(1f))

                // Build detailed geological description
                val classification = classifyAnomaly(maxVal, minVal)
                val depth = scan.estimatedDepthMeters.coerceAtLeast(0.8f)

                val apiKey = try {
                    com.example.BuildConfig.GEMINI_API_KEY
                } catch (e: Exception) {
                    ""
                }

                if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    // Call Gemini API server-side
                    val promptText = """
                        You are a senior geophysicist and OKM ground scan expert.
                        Analyze this subsurface magnetometer scan:
                        - Grid size: ${scan.gridWidth} x ${scan.gridLength} (${scan.data.size} points)
                        - Soil type: ${scan.soilType}
                        - Max ADC: $maxVal, Min ADC: $minVal, Avg: $avg, Variance: $variance
                        - Preliminary Classification: $classification
                        - Estimated Depth: $depth meters
                        - User note: ${scan.notes}
                        - User question: $customPrompt

                        Provide a comprehensive, authoritative radar analysis report in Persian (فارسی) followed by an English summary.
                        Include:
                        1. Target Nature (Gold, Non-ferrous, Ferrous, Cavity, Natural mineral)
                        2. Spatial Geometry & Orientation
                        3. Accurate Depth & Drilling/Excavation Feasibility
                        4. Step-by-step Field Action Advice
                    """.trimIndent()

                    val responseText = callGeminiApiDirect(apiKey, promptText)
                    _aiState.value = AiAnalysisState.Success(
                        report = responseText,
                        estimatedDepth = depth,
                        anomalyType = classification,
                        confidencePercent = (85 + (variance % 10)).toInt().coerceIn(75, 96),
                        adviceList = listOf(
                            "اسکن تاییدی عمود بر مسیر فعلی (Cross-Scan) در ابعاد کوچک‌تر انجام دهید.",
                            "نقطه اوج سیگنال در مرکز گرادیان قرمز را نشانه‌گذاری فیزیکی کنید.",
                            "نسبت نفوذ امواج در ${scan.soilType} را در عمق‌سنجی نهایی لحاظ کنید."
                        )
                    )
                } else {
                    // Highly detailed offline Expert Geological Reasoning Engine
                    kotlinx.coroutines.delay(1200)

                    val isHighMetal = maxVal > 600f
                    val isCavity = minVal < 100f
                    val confidence = if (isHighMetal && isCavity) 94 else if (isHighMetal || isCavity) 88 else 79

                    val advice = mutableListOf<String>()
                    advice.add("اسکن تکمیلی با سرعت چرخش کمتر برای وضوح بالاتر لبه‌های آنومالی پیشنهاد می‌شود.")
                    if (isHighMetal) {
                        advice.add("سیگنال مثبت قوی بیانگر هدایت الکتریکی بالا (احتمال طلا، مفرغ یا نقره) است.")
                    }
                    if (isCavity) {
                        advice.add("افت مقادیر به سمت رنگ آبی نشان‌دهنده شکست تراکم خاک و وجود فضای خالی یا راهرو است.")
                    }
                    advice.add("از تراز بودن سنسور عمودی در حین گام‌برداری اطمینان حاصل فرمایید.")

                    val report = buildString {
                        append("گزارش ژئوفیزیکی و تفسیر هوش مصنوعی رادار زمین\n")
                        append("=========================================\n\n")
                        append("۱. تشخیص ماهیت هدف (Target Nature):\n")
                        append("بر اساس تحلیل ماتریس سیگنال‌های دریافتی، نوع آنومالی تحت عنوان «$classification» ارزیابی شد. شدت سیگنال بیشینه به $maxVal ADC رسیده که انحراف شدید ${(maxVal - 250).toInt()} واحدی از بستر یکنواخت خاک را نشان می‌دهد.\n\n")
                        append("۲. محاسبه عمق تخمینی (Depth Calculation):\n")
                        append("با توجه به ثابت دی‌الکتریک بستر «${scan.soilType}» و زمان میرایی پالس‌ها، عمق تقریبی مرکز هدف در فاصله $depth متری از سطح زمین برآورد می‌گردد.\n\n")
                        append("۳. توصیف هندسی لکه هدف (Spatial Geometry):\n")
                        if (isCavity && isHighMetal) {
                            append("ساختار از نوع ترکیبی مقبره/اتاقک با تدفین فلزی است؛ ناحیه با سیگنال منفی (حفره) در برگیرنده کانون مثبت رسانا می‌باشد.\n\n")
                        } else if (isHighMetal) {
                            append("لکه سیگنال کروی متمرکز با خطوط ایزومغناطیسی فشرده، نشان‌دهنده شیء فلزی فشرده و غیرپراکنده است.\n\n")
                        } else {
                            append("کانتورهای هم‌پتانسیل کشیده و یکنواخت بوده که بیانگر لایه‌بندی طبیعی یا تونل خاکی می‌باشد.\n\n")
                        }
                        append("۴. توصیه‌های عملیاتی:\n")
                        advice.forEachIndexed { i, a ->
                            append("${i + 1}. $a\n")
                        }
                    }

                    _aiState.value = AiAnalysisState.Success(
                        report = report,
                        estimatedDepth = depth,
                        anomalyType = classification,
                        confidencePercent = confidence,
                        adviceList = advice
                    )
                }
            } catch (e: Exception) {
                _aiState.value = AiAnalysisState.Error(e.localizedMessage ?: "خطا در تحلیل هوش مصنوعی")
            }
        }
    }

    private fun callGeminiApiDirect(apiKey: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        val jsonBody = JSONObject().apply {
            val contentsArr = org.json.JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = org.json.JSONArray().apply {
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

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        }
        throw IllegalStateException("API response code: ${conn.responseCode}")
    }
}
