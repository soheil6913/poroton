package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

class ScanRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("okm_scans_store", Context.MODE_PRIVATE)
    private val _scans = MutableStateFlow<List<ScanRecord>>(emptyList())
    val scans: StateFlow<List<ScanRecord>> = _scans.asStateFlow()

    init {
        loadAllScans()
    }

    private fun loadAllScans() {
        val jsonStr = prefs.getString("saved_scans_json", null)
        if (jsonStr.isNullOrBlank()) {
            val initialDemos = createPresetDemoScans()
            saveScansList(initialDemos)
            _scans.value = initialDemos
        } else {
            try {
                val list = mutableListOf<ScanRecord>()
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val dataArr = obj.getJSONArray("data")
                    val dataList = mutableListOf<Float>()
                    for (j in 0 until dataArr.length()) {
                        dataList.add(dataArr.getDouble(j).toFloat())
                    }
                    list.add(
                        ScanRecord(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            timestamp = obj.getLong("timestamp"),
                            gridWidth = obj.getInt("gridWidth"),
                            gridLength = obj.getInt("gridLength"),
                            soilType = obj.getString("soilType"),
                            scanPattern = obj.getString("scanPattern"),
                            data = dataList,
                            notes = obj.optString("notes", ""),
                            maxSignal = obj.optDouble("maxSignal", 0.0).toFloat(),
                            minSignal = obj.optDouble("minSignal", 0.0).toFloat(),
                            estimatedDepthMeters = obj.optDouble("estimatedDepthMeters", 0.0).toFloat(),
                            anomalyClassification = obj.optString("anomalyClassification", "هدف ناشناخته")
                        )
                    )
                }
                val hasPhase2 = list.any { it.name.contains("فاز ۲") || it.name.contains("Phase 2") }
                if (!hasPhase2) {
                    val presets = createPresetDemoScans()
                    val p4 = presets.firstOrNull { it.name.contains("فاز ۲") || it.name.contains("Phase 2") }
                    if (p4 != null) {
                        list.add(1.coerceAtMost(list.size), p4)
                        saveScansList(list)
                    }
                }
                _scans.value = list
            } catch (e: Exception) {
                val initialDemos = createPresetDemoScans()
                saveScansList(initialDemos)
                _scans.value = initialDemos
            }
        }
    }

    fun saveScan(record: ScanRecord) {
        val current = _scans.value.toMutableList()
        val index = current.indexOfFirst { it.id == record.id }
        if (index >= 0) {
            current[index] = record
        } else {
            current.add(0, record)
        }
        _scans.value = current
        saveScansList(current)
    }

    fun deleteScan(id: String) {
        val updated = _scans.value.filter { it.id != id }
        _scans.value = updated
        saveScansList(updated)
    }

    private fun saveScansList(list: List<ScanRecord>) {
        val array = JSONArray()
        for (scan in list) {
            val obj = JSONObject()
            obj.put("id", scan.id)
            obj.put("name", scan.name)
            obj.put("timestamp", scan.timestamp)
            obj.put("gridWidth", scan.gridWidth)
            obj.put("gridLength", scan.gridLength)
            obj.put("soilType", scan.soilType)
            obj.put("scanPattern", scan.scanPattern)
            obj.put("notes", scan.notes)
            obj.put("maxSignal", scan.maxSignal.toDouble())
            obj.put("minSignal", scan.minSignal.toDouble())
            obj.put("estimatedDepthMeters", scan.estimatedDepthMeters.toDouble())
            obj.put("anomalyClassification", scan.anomalyClassification)

            val dataArr = JSONArray()
            for (v in scan.data) {
                dataArr.put(v.toDouble())
            }
            obj.put("data", dataArr)
            array.put(obj)
        }
        prefs.edit().putString("saved_scans_json", array.toString()).apply()
    }

    /**
     * Creates realistic ground radar datasets with mathematical Gaussian anomalies
     * exactly implementing the user's Python target logic:
     * High red Gaussian peak for metal/gold target,
     * Negative blue Gaussian depression for cavity/void,
     * Green low-noise background for normal soil.
     */
    private fun createPresetDemoScans(): List<ScanRecord> {
        // Preset 1: Ancient Chamber & Gold Target
        val width1 = 12
        val length1 = 12
        val data1 = ArrayList<Float>(width1 * length1)
        for (y in 0 until length1) {
            for (x in 0 until width1) {
                // Background noise: 0.1 .. 0.25 (scaled to 150..220 ADC)
                val noise = Random.nextFloat() * 40f + 180f
                // Target 1: Gold at x=8, y=4 -> strong peak (+650 ADC)
                val distTarget1Sq = ((x - 8f).pow(2) + (y - 4f).pow(2)) / (2f * 1.5f.pow(2))
                val t1 = exp(-distTarget1Sq) * 650f
                // Target 2: Cavity at x=4, y=8 -> void drop (-220 ADC)
                val distTarget2Sq = ((x - 4f).pow(2) + (y - 8f).pow(2)) / (2f * 2.0f.pow(2))
                val t2 = exp(-distTarget2Sq) * 220f

                val valAtPoint = (noise + t1 - t2).coerceIn(-100f, 1000f)
                data1.add(valAtPoint)
            }
        }
        val scan1 = ScanRecord(
            name = "اسکن اتاقک باستانی و هدف فلزی (Ancient Chamber & Gold)",
            timestamp = System.currentTimeMillis() - 86400000L * 2,
            gridWidth = width1,
            gridLength = length1,
            soilType = "خاک کشاورزی (Soil)",
            scanPattern = "زیگزاگ (Zig-Zag)",
            data = data1,
            notes = "آنومالی دوگانه شامل حفره بزرگ در جنوب‌غربی و فلز با رسانایی بالا (طلا/مفرغ) در شمال‌شرقی.",
            estimatedDepthMeters = 2.4f,
            anomalyClassification = "فلز گرانبها در مجاورت حفره (Gold in Cavity)"
        )

        // Preset 2: Deep Anomaly in Hard Rock
        val width2 = 10
        val length2 = 10
        val data2 = ArrayList<Float>(width2 * length2)
        for (y in 0 until length2) {
            for (x in 0 until width2) {
                val noise = Random.nextFloat() * 50f + 210f
                val distSq = ((x - 5f).pow(2) + (y - 5f).pow(2)) / (2f * 1.8f.pow(2))
                val peak = exp(-distSq) * 780f
                data2.add(noise + peak)
            }
        }
        val scan2 = ScanRecord(
            name = "توده فلزی متمرکز در بستر صخره‌ای (Deep Target in Rock)",
            timestamp = System.currentTimeMillis() - 86400000L * 5,
            gridWidth = width2,
            gridLength = length2,
            soilType = "سنگ و صخره (Hard Rock)",
            scanPattern = "موازی (Parallel)",
            data = data2,
            notes = "سیگنال متقارن کروی در مرکز شبکه با شدت بالای مغناطیسی.",
            estimatedDepthMeters = 3.6f,
            anomalyClassification = "فلز غیرآهنی / طلا (Gold / Non-Ferrous)"
        )

        // Preset 3: Subsurface Tunnel & Cave
        val width3 = 14
        val length3 = 8
        val data3 = ArrayList<Float>(width3 * length3)
        for (y in 0 until length3) {
            for (x in 0 until width3) {
                val noise = Random.nextFloat() * 30f + 200f
                // Elongated tunnel in row 3 and 4
                val tunnelDistY = (y - 3.5f).pow(2)
                val isInsideTunnel = tunnelDistY < 1.5f && x in 2..11
                val drop = if (isInsideTunnel) 240f else 0f
                data3.add((noise - drop).coerceAtLeast(-50f))
            }
        }
        val scan3 = ScanRecord(
            name = "کانال و راهرو زیرزمینی (Subsurface Corridor & Tunnel)",
            timestamp = System.currentTimeMillis() - 86400000L * 10,
            gridWidth = width3,
            gridLength = length3,
            soilType = "خاک معدنی (Mineral)",
            scanPattern = "زیگزاگ (Zig-Zag)",
            data = data3,
            notes = "ساختار طولی ممتد با سیگنال منفی یکدست، نشان‌دهنده تونل دست‌ساز.",
            estimatedDepthMeters = 1.8f,
            anomalyClassification = "حفره / راهرو ممتد (Corridor / Void)"
        )

        // Preset 4: Time-Lapse Phase 2 of Ancient Chamber (Same 12x12 grid, with excavation at y=6, x=8 and second metal anomaly)
        val data4 = ArrayList<Float>(width1 * length1)
        for (y in 0 until length1) {
            for (x in 0 until width1) {
                val noise = Random.nextFloat() * 35f + 185f
                // Original gold target remained
                val distTarget1Sq = ((x - 8f).pow(2) + (y - 4f).pow(2)) / (2f * 1.5f.pow(2))
                val t1 = exp(-distTarget1Sq) * 630f
                // Excavation trench created around x=8, y=6 (new void anomaly of -280 ADC)
                val distTrenchSq = ((x - 8f).pow(2) + (y - 6.5f).pow(2)) / (2f * 1.2f.pow(2))
                val trenchDrop = exp(-distTrenchSq) * 310f
                // Cavity at x=4, y=8 is partially filled with backfill soil (void drop reduced to -140 ADC)
                val distTarget2Sq = ((x - 4f).pow(2) + (y - 8f).pow(2)) / (2f * 2.0f.pow(2))
                val t2 = exp(-distTarget2Sq) * 140f
                // New surface coin/metal drop at x=2, y=2 (+420 ADC)
                val distNewSq = ((x - 2f).pow(2) + (y - 2f).pow(2)) / (2f * 1.0f.pow(2))
                val tNew = exp(-distNewSq) * 420f

                val valAtPoint = (noise + t1 - trenchDrop - t2 + tNew).coerceIn(-100f, 1000f)
                data4.add(valAtPoint)
            }
        }
        val scan4 = ScanRecord(
            name = "اسکن فاز ۲ اتاقک باستانی (Chamber Phase 2 Rescan)",
            timestamp = System.currentTimeMillis() - 86400000L * 1, // 1 day ago vs 2 days ago
            gridWidth = width1,
            gridLength = length1,
            soilType = "خاک کشاورزی (Soil)",
            scanPattern = "زیگزاگ (Zig-Zag)",
            data = data4,
            notes = "اسکن مجدد پس از ۳۰ روز: ترانشه حفاری جدید در عمق ۱.۵ متری و پدیدار شدن آنومالی سطحی در شمال غربی.",
            estimatedDepthMeters = 2.2f,
            anomalyClassification = "تغییر ساختاری ناشی از حفاری (Excavation Shift)"
        )

        return listOf(scan1, scan4, scan2, scan3)
    }
}
