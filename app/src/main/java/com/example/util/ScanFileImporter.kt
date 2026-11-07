package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.ScanRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Robust geophysical survey data file importer.
 * Parses external ground scan files in multiple formats:
 * 1. OKM Ground Radar Exported CSV files (with metadata, 2D matrix, and point logs)
 * 2. General 2D numeric matrices (CSV, TSV, space-separated values)
 * 3. JSON geophysical dataset objects
 * 4. Flat telemetry impulse streams (ADC logs)
 */
object ScanFileImporter {

    fun importScanFromUri(context: Context, uri: Uri): Result<ScanRecord> {
        return try {
            val fileName = queryFileName(context, uri) ?: "External_Scan_${System.currentTimeMillis() % 10000}"
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: return Result.failure(Exception("امکان خواندن محتوای فایل وجود ندارد."))

            val cleanContent = content.trim().removePrefix("\uFEFF") // remove UTF-8 BOM if present
            if (cleanContent.isBlank()) {
                return Result.failure(Exception("فایل انتخاب شده خالی است."))
            }

            val scan = parseContent(fileName, cleanContent)
            Result.success(scan)
        } catch (e: Exception) {
            Result.failure(Exception("خطا در بازخوانی فایل اسکن: ${e.localizedMessage ?: "فرمت نامعتبر"}"))
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIdx >= 0) {
                            name = cursor.getString(colIdx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name
    }

    fun parseContent(fileName: String, content: String): ScanRecord {
        val clean = content.trim().removePrefix("\uFEFF")

        // 1. Try parsing as JSON
        if (clean.startsWith("{") && clean.contains("\"data\"")) {
            val jsonScan = parseJson(fileName, clean)
            if (jsonScan != null) return jsonScan
        }

        // 2. Try parsing as OKM / Specialized CSV (with headers)
        if (clean.contains("OKM", ignoreCase = true) ||
            clean.contains("Grid Width", ignoreCase = true) ||
            clean.contains("Row_Line", ignoreCase = true) ||
            clean.contains("Scan Title", ignoreCase = true)
        ) {
            val okmScan = parseOkmCsv(fileName, clean)
            if (okmScan != null) return okmScan
        }

        // 3. Try parsing as a 2D Matrix (CSV / TSV / Space-delimited rows of numbers)
        val matrixScan = parseMatrixOrStream(fileName, clean)
        if (matrixScan != null) return matrixScan

        throw IllegalArgumentException("فرمت فایل اسکن شناسایی نشد. لطفاً یک فایل معتبر CSV یا JSON حاوی داده‌های رادار وارد کنید.")
    }

    private fun parseJson(fileName: String, content: String): ScanRecord? {
        return try {
            val obj = JSONObject(content)
            val baseName = obj.optString("name", fileName.substringBeforeLast('.'))
            var w = obj.optInt("gridWidth", 0)
            var l = obj.optInt("gridLength", 0)
            val soil = obj.optString("soilType", "خاک کشاورزی (Soil)")
            val pattern = obj.optString("scanPattern", "موازی (Parallel)")
            val notes = obj.optString("notes", "اسکن وارد شده از فایل خارجی")

            val dataArr = obj.optJSONArray("data") ?: return parseJsonSimple(fileName, content)
            val dataList = mutableListOf<Float>()
            for (i in 0 until dataArr.length()) {
                dataList.add(dataArr.getDouble(i).toFloat())
            }

            if (dataList.size < 4) return null

            if (w <= 0 || l <= 0 || w * l != dataList.size) {
                // Determine plausible dimensions
                val dims = inferDimensions(dataList.size)
                w = dims.first
                l = dims.second
            }

            buildScanRecord(
                name = baseName,
                width = w,
                length = l,
                soil = soil,
                pattern = pattern,
                data = dataList,
                notes = notes
            )
        } catch (_: Throwable) {
            parseJsonSimple(fileName, content)
        }
    }

    private fun parseJsonSimple(fileName: String, content: String): ScanRecord? {
        return try {
            val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1)
            val wMatch = Regex("\"gridWidth\"\\s*:\\s*(\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val lMatch = Regex("\"gridLength\"\\s*:\\s*(\\d+)").find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val soilMatch = Regex("\"soilType\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: "خاک کشاورزی (Soil)"
            val patternMatch = Regex("\"scanPattern\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: "موازی (Parallel)"
            val notesMatch = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: "اسکن وارد شده از فایل خارجی"

            val dataMatch = Regex("\"data\"\\s*:\\s*\\[([^\\]]+)\\]").find(content)?.groupValues?.get(1) ?: return null
            val dataList = dataMatch.split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (dataList.size < 4) return null

            val w = if (wMatch > 0) wMatch else inferDimensions(dataList.size).first
            val l = if (lMatch > 0) lMatch else (dataList.size / w).coerceAtLeast(1)

            buildScanRecord(
                name = nameMatch ?: fileName.substringBeforeLast('.'),
                width = w,
                length = l,
                soil = soilMatch,
                pattern = patternMatch,
                data = dataList,
                notes = notesMatch
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseOkmCsv(fileName: String, content: String): ScanRecord? {
        val lines = content.lines()
        var title: String? = null
        var gridWidth: Int? = null
        var gridLength: Int? = null
        var soilType = "خاک کشاورزی (Soil)"
        var scanPattern = "موازی (Parallel)"
        var notes = "وارد شده از گزارش OKM"

        val matrixRows = mutableListOf<List<Float>>()
        val pointLogValues = mutableListOf<Float>()

        var inMatrixSection = false
        var inPointLogSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Metadata parsing
            if (trimmed.startsWith("Scan Title,", ignoreCase = true)) {
                title = trimmed.substringAfter("Scan Title,").trim().removeSurrounding("\"")
            } else if (trimmed.startsWith("Grid Width (Cols),", ignoreCase = true)) {
                gridWidth = trimmed.substringAfter("Grid Width (Cols),").trim().toIntOrNull()
            } else if (trimmed.startsWith("Grid Length (Rows),", ignoreCase = true)) {
                gridLength = trimmed.substringAfter("Grid Length (Rows),").trim().toIntOrNull()
            } else if (trimmed.startsWith("Soil Calibration,", ignoreCase = true)) {
                soilType = trimmed.substringAfter("Soil Calibration,").trim().removeSurrounding("\"")
            } else if (trimmed.startsWith("Traverse Pattern,", ignoreCase = true)) {
                scanPattern = trimmed.substringAfter("Traverse Pattern,").trim().removeSurrounding("\"")
            } else if (trimmed.startsWith("Field Notes,", ignoreCase = true)) {
                notes = trimmed.substringAfter("Field Notes,").trim().removeSurrounding("\"")
            }

            // Section markers
            if (trimmed.contains("2D MATRIX HEATMAP GRID", ignoreCase = true)) {
                inMatrixSection = true
                inPointLogSection = false
                continue
            }
            if (trimmed.contains("POINT-BY-POINT MEASUREMENT LOG", ignoreCase = true)) {
                inPointLogSection = true
                inMatrixSection = false
                continue
            }

            // Matrix parsing
            if (inMatrixSection) {
                if (trimmed.startsWith("#") || trimmed.startsWith("Row_Line", ignoreCase = true)) continue
                val parts = trimmed.split(',').map { it.trim() }
                if (parts.size >= 2) {
                    val rowNums = parts.drop(1).mapNotNull { it.toFloatOrNull() }
                    if (rowNums.isNotEmpty()) {
                        matrixRows.add(rowNums)
                    }
                }
            }

            // Point log parsing: Impulse_Index, X, Y, Raw_ADC, ...
            if (inPointLogSection) {
                if (trimmed.startsWith("#") || trimmed.startsWith("Impulse_Index", ignoreCase = true)) continue
                val parts = trimmed.split(',').map { it.trim() }
                if (parts.size >= 4) {
                    val adc = parts[3].toFloatOrNull()
                    if (adc != null) {
                        pointLogValues.add(adc)
                    }
                }
            }
        }

        val finalName = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')

        // Check if matrix was parsed
        if (matrixRows.isNotEmpty()) {
            val w = gridWidth ?: matrixRows.first().size
            val l = gridLength ?: matrixRows.size
            val flatData = mutableListOf<Float>()
            for (r in matrixRows) {
                flatData.addAll(r)
            }
            if (flatData.size >= 4) {
                return buildScanRecord(finalName, w, l, soilType, scanPattern, flatData, notes)
            }
        }

        // Check if point log was parsed
        if (pointLogValues.size >= 4) {
            val w = gridWidth ?: 5
            val l = gridLength ?: (pointLogValues.size / w).coerceAtLeast(1)
            return buildScanRecord(finalName, w, l, soilType, scanPattern, pointLogValues, notes)
        }

        return null
    }

    private fun parseMatrixOrStream(fileName: String, content: String): ScanRecord? {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

        if (lines.isEmpty()) return null

        val rowsOfNumbers = mutableListOf<List<Float>>()
        val allNumbers = mutableListOf<Float>()

        for (line in lines) {
            // Split by comma, semicolon, tab, or whitespace
            val tokens = line.split(Regex("[,;\\t\\s]+")).filter { it.isNotBlank() }
            val nums = tokens.mapNotNull { it.toFloatOrNull() }
            if (nums.isNotEmpty()) {
                rowsOfNumbers.add(nums)
                allNumbers.addAll(nums)
            }
        }

        if (allNumbers.size < 4) return null

        // If multiple rows with consistent column count -> 2D Matrix
        val firstRowSize = rowsOfNumbers.first().size
        val isConsistentMatrix = rowsOfNumbers.size >= 2 &&
                rowsOfNumbers.all { it.size == firstRowSize } &&
                firstRowSize >= 2

        val baseName = fileName.substringBeforeLast('.')

        return if (isConsistentMatrix) {
            val w = firstRowSize
            val l = rowsOfNumbers.size
            buildScanRecord(
                name = baseName,
                width = w,
                length = l,
                soil = "خاک کشاورزی (Soil)",
                pattern = "موازی (Parallel)",
                data = allNumbers,
                notes = "ماتریس عددی اسکن ژئوفیزیک ($w × $l)"
            )
        } else {
            // Inferred grid dimensions from 1D data stream
            val (w, l) = inferDimensions(allNumbers.size)
            // Trim or pad if needed so size == w * l
            val targetSize = w * l
            val adjustedData = if (allNumbers.size >= targetSize) {
                allNumbers.take(targetSize)
            } else {
                allNumbers + List(targetSize - allNumbers.size) { 250f }
            }
            buildScanRecord(
                name = baseName,
                width = w,
                length = l,
                soil = "خاک کشاورزی (Soil)",
                pattern = "موازی (Parallel)",
                data = adjustedData,
                notes = "جریان داده‌های خام اسکن زمین"
            )
        }
    }

    private fun inferDimensions(total: Int): Pair<Int, Int> {
        // Try common OKM grid widths: 5, 6, 8, 10, 12, 15, 20
        val candidates = listOf(10, 8, 5, 6, 12, 15, 20, 4)
        for (cand in candidates) {
            if (total % cand == 0 && (total / cand) >= 2) {
                return Pair(cand, total / cand)
            }
        }
        // Fallback: nearest square
        val sqrtVal = sqrt(total.toDouble()).roundToInt().coerceAtLeast(2)
        val l = (total / sqrtVal).coerceAtLeast(2)
        return Pair(sqrtVal, l)
    }

    private fun buildScanRecord(
        name: String,
        width: Int,
        length: Int,
        soil: String,
        pattern: String,
        data: List<Float>,
        notes: String
    ): ScanRecord {
        val maxVal = data.maxOrNull() ?: 250f
        val minVal = data.minOrNull() ?: 250f
        val depth = if (maxVal > 300f || minVal < 180f) {
            val spread = maxVal - minVal
            ((spread / 180f) * 1.8f).coerceIn(0.6f, 5.5f)
        } else {
            0.0f
        }

        val classification = when {
            maxVal >= 550f -> "هدف فلزی با رسانایی بسیار بالا (طلا / مس / فلز گرانبها)"
            maxVal >= 380f && minVal < 180f -> "ناهنجاری ساختاری ترکیبی (اتاقک / ورودی مدفون به همراه فلز)"
            maxVal >= 360f -> "هدف رسانای فلزی (آلیاژ / برنز / نقره)"
            minVal <= 160f -> "حفره زیرزمینی / فضای خالی یا مقبره خاکی"
            maxVal >= 300f -> "توده مغناطیسی کانی / سنگ معدنی"
            else -> "زمین طبیعی و یکدست (Uniform Soil)"
        }

        return ScanRecord(
            id = UUID.randomUUID().toString(),
            name = name,
            timestamp = System.currentTimeMillis(),
            gridWidth = width,
            gridLength = length,
            soilType = soil,
            scanPattern = pattern,
            data = data,
            notes = notes,
            maxSignal = maxVal,
            minSignal = minVal,
            estimatedDepthMeters = depth,
            anomalyClassification = classification
        )
    }
}
