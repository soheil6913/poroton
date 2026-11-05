package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.ScanRecord
import com.example.ui.theme.getTargetColor
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

object ReportExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exports full detailed geophysical survey data as a professional CSV file
     * with metadata, 2D matrix layout, and point-by-point impulse telemetry.
     */
    fun exportCsv(context: Context, scan: ScanRecord, shareIntent: Boolean = true): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val dateStamp = fileDateFormat.format(Date(scan.timestamp))
        val sanitizedName = scan.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(25)
        val file = File(exportDir, "Scan_${sanitizedName}_$dateStamp.csv")

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                // Write UTF-8 BOM so Microsoft Excel automatically recognizes UTF-8 text without corrupted characters
                writer.write("\uFEFF")

                // 1. Header Information
                writer.write("# =========================================================================\n")
                writer.write("# OKM 3D GROUND SCAN RADAR - PROFESSIONAL GEOPHYSICAL SITE DATASET\n")
                writer.write("# =========================================================================\n")
                writer.write("Document Type,Ground Penetrating Radar / Magnetometer Site Log\n")
                writer.write("Scan Title,\"${scan.name.replace("\"", "\"\"")}\"\n")
                writer.write("Scan ID,${scan.id}\n")
                writer.write("Survey Date,${dateFormat.format(Date(scan.timestamp))}\n")
                writer.write("Grid Width (Cols),${scan.gridWidth}\n")
                writer.write("Grid Length (Rows),${scan.gridLength}\n")
                writer.write("Total Impulses,${scan.data.size}\n")
                writer.write("Soil Calibration,\"${scan.soilType}\"\n")
                writer.write("Traverse Pattern,\"${scan.scanPattern}\"\n")
                writer.write("Max Peak Signal (+ADC),${scan.maxSignal}\n")
                writer.write("Min Void Signal (-ADC),${scan.minSignal}\n")
                writer.write("Estimated Target Depth (m),${scan.estimatedDepthMeters}\n")
                writer.write("Anomaly Classification,\"${scan.anomalyClassification.replace("\"", "\"\"")}\"\n")
                writer.write("Field Notes,\"${scan.notes.replace("\"", "\"\"")}\"\n\n")

                // 2. 2D Heatmap Matrix Table
                writer.write("# =========================================================================\n")
                writer.write("# 2D MATRIX HEATMAP GRID (Rows 1..${scan.gridLength}, Columns 1..${scan.gridWidth})\n")
                writer.write("# =========================================================================\n")

                // Column Headers: Y / X -> Col 1, Col 2, ...
                val colHeaders = (1..scan.gridWidth).joinToString(",") { "Col_$it" }
                writer.write("Row_Line,$colHeaders\n")

                for (y in 0 until scan.gridLength) {
                    val rowValues = (0 until scan.gridWidth).map { x ->
                        val idx = y * scan.gridWidth + x
                        val v = scan.data.getOrNull(idx) ?: 250f
                        String.format(Locale.US, "%.1f", v)
                    }.joinToString(",")
                    writer.write("Line_${y + 1},$rowValues\n")
                }
                writer.write("\n")

                // 3. Tabular Point-by-Point Telemetry
                writer.write("# =========================================================================\n")
                writer.write("# POINT-BY-POINT MEASUREMENT LOG\n")
                writer.write("# =========================================================================\n")
                writer.write("Impulse_Index,X_Coordinate,Y_Coordinate,Raw_ADC,Normalized_Z,Depth_Est_M,Classification_Tag\n")

                for (y in 0 until scan.gridLength) {
                    for (x in 0 until scan.gridWidth) {
                        val idx = y * scan.gridWidth + x
                        val rawVal = scan.data.getOrNull(idx) ?: 250f
                        val norm = (rawVal - 250f) / 200f
                        val depthEst = if (rawVal > 350f) scan.estimatedDepthMeters else 0f
                        val tag = when {
                            rawVal > 450f -> "Precious Metal / Gold Peak"
                            rawVal > 300f -> "Mineralized / Conductive Anomaly"
                            rawVal < 180f -> "Cavity / Void / Subsidence"
                            else -> "Normal Background Ground"
                        }
                        writer.write("${idx + 1},${x + 1},${y + 1},${String.format(Locale.US, "%.1f", rawVal)},${String.format(Locale.US, "%.2f", norm)},${String.format(Locale.US, "%.2f", depthEst)},$tag\n")
                    }
                }
            }
        }

        if (shareIntent) {
            shareFile(context, file, "text/csv", "اشتراک فایل داده‌های اسکن (CSV)")
        }

        return file
    }

    /**
     * Copies the 2D matrix data to Android clipboard for instant pasting into spreadsheet software.
     */
    fun copyCsvToClipboard(context: Context, scan: ScanRecord) {
        val sb = StringBuilder()
        sb.append("# ${scan.name} (${scan.gridWidth}x${scan.gridLength})\n")
        for (y in 0 until scan.gridLength) {
            val row = (0 until scan.gridWidth).joinToString(",") { x ->
                val idx = y * scan.gridWidth + x
                scan.data.getOrNull(idx)?.toInt()?.toString() ?: "250"
            }
            sb.append(row).append("\n")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OKM Scan Data", sb.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "ماتریس داده‌های اسکن در کلیپ‌بورد کپی شد (${scan.data.size} نقطه)", Toast.LENGTH_SHORT).show()
    }

    /**
     * Generates a high-resolution, professional PDF Geophysical Site Survey Report
     * containing site metadata, anomaly diagnosis, depth estimation, and a 2D visual radar heatmap
     * rendered with the 6-color Neon Laser Colormap.
     */
    fun exportPdf(context: Context, scan: ScanRecord, shareIntent: Boolean = true): File {
        val reportDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val dateStamp = fileDateFormat.format(Date(scan.timestamp))
        val sanitizedName = scan.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(25)
        val file = File(reportDir, "Report_${sanitizedName}_$dateStamp.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842 pt)
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawPdfReport(canvas, scan)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        if (shareIntent) {
            shareFile(context, file, "application/pdf", "اشتراک گزارش حرفه‌ای زمین‌شناسی (PDF)")
        }

        return file
    }

    /**
     * Draws the professional geophysical report layout onto the PDF Canvas.
     */
    private fun drawPdfReport(canvas: Canvas, scan: ScanRecord) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Page Background
        canvas.drawColor(android.graphics.Color.WHITE)

        // 2. Top Header Banner (Deep Navy #101626)
        paint.color = android.graphics.Color.rgb(16, 22, 38)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Cyber Gold Accent Line
        paint.color = android.graphics.Color.rgb(255, 200, 55)
        paint.strokeWidth = 3f
        canvas.drawLine(0f, 90f, 595f, 90f, paint)

        // App Title in Header
        paint.color = android.graphics.Color.rgb(255, 200, 55)
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("OKM 3D GROUND SCAN RADAR SYSTEM", 30f, 38f, paint)

        paint.color = android.graphics.Color.rgb(0, 229, 255)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("PROFESSIONAL GEOPHYSICAL SITE SURVEY & ANOMALY DEPTH REPORT", 30f, 54f, paint)

        paint.color = android.graphics.Color.rgb(180, 190, 205)
        paint.textSize = 8.5f
        canvas.drawText("CERTIFIED SUBSURFACE GEOLOGICAL MAPPING • PROTON LASER ENGINE", 30f, 70f, paint)

        // Document ID & Timestamp on right side
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("REPORT REF: OKM-${scan.id.take(8).uppercase(Locale.US)}", 565f, 38f, paint)
        canvas.drawText("DATE: ${dateFormat.format(Date(scan.timestamp))}", 565f, 54f, paint)
        canvas.drawText("STATUS: VERIFIED TELEMETRY", 565f, 70f, paint)
        paint.textAlign = Paint.Align.LEFT

        // 3. Section 1: Survey Metadata & Site Specs
        var curY = 115f
        drawSectionTitle(canvas, "1. SURVEY SPECIFICATIONS & LOCATION PARAMETERS", 30f, curY)
        curY += 15f

        // Metadata Box
        paint.color = android.graphics.Color.rgb(245, 247, 250)
        canvas.drawRoundRect(30f, curY, 565f, curY + 68f, 6f, 6f, paint)
        paint.color = android.graphics.Color.rgb(215, 222, 232)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(30f, curY, 565f, curY + 68f, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        // Table details in 2 columns
        val col1X = 45f
        val col2X = 310f
        var textY = curY + 18f

        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Project / Scan Title:", col1X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(scan.name.take(38), col1X + 100f, textY, paint)

        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Soil Type:", col2X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(scan.soilType, col2X + 80f, textY, paint)

        textY += 18f
        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Grid Dimensions:", col1X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${scan.gridWidth} Cols × ${scan.gridLength} Rows (${scan.data.size} Total Points)", col1X + 100f, textY, paint)

        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Traverse Pattern:", col2X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(scan.scanPattern, col2X + 80f, textY, paint)

        textY += 18f
        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Telemetry Unit:", col1X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Gold Radar X20 Fluxgate", col1X + 100f, textY, paint)

        paint.color = android.graphics.Color.rgb(80, 90, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Survey Protocol:", col2X, textY, paint)
        paint.color = android.graphics.Color.rgb(20, 25, 35)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Standard Calibration (250 ADC Baseline)", col2X + 80f, textY, paint)

        curY += 82f

        // 4. Section 2: Anomaly Diagnosis & Geophysical Analytics
        drawSectionTitle(canvas, "2. ANOMALY CLASSIFICATION & GEOPHYSICAL ANALYSIS", 30f, curY)
        curY += 15f

        // Summary Card Box
        paint.color = android.graphics.Color.rgb(238, 244, 255)
        canvas.drawRoundRect(30f, curY, 565f, curY + 60f, 6f, 6f, paint)
        paint.color = android.graphics.Color.rgb(180, 205, 245)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(30f, curY, 565f, curY + 60f, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        // 3 Key Badges inside
        val badgeW = 165f
        val badgeH = 44f
        val badgeY = curY + 8f

        // Badge 1: Anomaly Classification
        drawMetricBadge(
            canvas, 42f, badgeY, badgeW, badgeH,
            "PRIMARY TARGET DIAGNOSIS",
            scan.anomalyClassification,
            android.graphics.Color.rgb(180, 120, 10)
        )

        // Badge 2: Estimated Depth
        drawMetricBadge(
            canvas, 215f, badgeY, badgeW, badgeH,
            "ESTIMATED TARGET DEPTH",
            String.format(Locale.US, "%.2f METERS", scan.estimatedDepthMeters),
            android.graphics.Color.rgb(0, 120, 180)
        )

        // Badge 3: Signal Range
        drawMetricBadge(
            canvas, 388f, badgeY, badgeW, badgeH,
            "SIGNAL EXTREMES (ADC)",
            "+${scan.maxSignal.toInt()} / ${scan.minSignal.toInt()}",
            android.graphics.Color.rgb(180, 30, 60)
        )

        curY += 76f

        // 5. Section 3: 2D Radar Matrix Heatmap (Visual Color representation)
        drawSectionTitle(canvas, "3. 2D SUBSURFACE RADAR HEATMAP (PROTON LASER COLORMAP)", 30f, curY)
        curY += 15f

        // Heatmap Container Box
        val mapAreaLeft = 30f
        val mapAreaTop = curY
        val mapAreaWidth = 535f
        val mapAreaHeight = 270f

        paint.color = android.graphics.Color.rgb(12, 16, 24)
        canvas.drawRoundRect(mapAreaLeft, mapAreaTop, mapAreaLeft + mapAreaWidth, mapAreaTop + mapAreaHeight, 8f, 8f, paint)

        // Draw 2D Grid Cells
        val gridMargin = 20f
        val gridDrawW = mapAreaWidth - gridMargin * 2f
        val gridDrawH = mapAreaHeight - 48f
        val cellW = gridDrawW / scan.gridWidth
        val cellH = gridDrawH / scan.gridLength

        for (y in 0 until scan.gridLength) {
            for (x in 0 until scan.gridWidth) {
                val idx = y * scan.gridWidth + x
                val rawVal = scan.data.getOrNull(idx) ?: 250f
                val norm = ((rawVal - 250f) / 200f).coerceIn(-1f, 2f)
                val composeCol = getTargetColor(norm, 0f)

                paint.color = android.graphics.Color.argb(
                    (composeCol.alpha * 255).toInt(),
                    (composeCol.red * 255).toInt(),
                    (composeCol.green * 255).toInt(),
                    (composeCol.blue * 255).toInt()
                )

                val cLeft = mapAreaLeft + gridMargin + x * cellW
                val cTop = mapAreaTop + 14f + y * cellH
                canvas.drawRect(cLeft + 0.8f, cTop + 0.8f, cLeft + cellW - 0.8f, cTop + cellH - 0.8f, paint)
            }
        }

        // Draw Heatmap Color Legend Bar at the bottom of the map container
        val barTop = mapAreaTop + mapAreaHeight - 24f
        val barLeft = mapAreaLeft + 40f
        val barWidth = mapAreaWidth - 80f
        val barHeight = 8f

        // Legend Gradient: Navy -> Blue -> Cyan -> Laser Green -> Yellow -> Red
        val shader = LinearGradient(
            barLeft, barTop, barLeft + barWidth, barTop,
            intArrayOf(
                android.graphics.Color.rgb(0, 0, 128),
                android.graphics.Color.rgb(0, 0, 255),
                android.graphics.Color.rgb(0, 229, 255),
                android.graphics.Color.rgb(0, 255, 102),
                android.graphics.Color.rgb(255, 238, 0),
                android.graphics.Color.rgb(255, 0, 51)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, 3f, 3f, paint)
        paint.shader = null

        // Legend text marks
        paint.color = android.graphics.Color.rgb(180, 190, 205)
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Cavity / Void (-200 ADC)", barLeft, barTop - 3f, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Normal Ground (Laser Green)", barLeft + barWidth / 2f, barTop - 3f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Precious Metal / Gold (+750 ADC)", barLeft + barWidth, barTop - 3f, paint)
        paint.textAlign = Paint.Align.LEFT

        curY += mapAreaHeight + 16f

        // 6. Section 4: Site Notes & Geological Interpretation
        drawSectionTitle(canvas, "4. FIELD NOTES & GEOLOGICAL INTERPRETATION", 30f, curY)
        curY += 15f

        paint.color = android.graphics.Color.rgb(248, 249, 251)
        canvas.drawRoundRect(30f, curY, 565f, curY + 54f, 6f, 6f, paint)
        paint.color = android.graphics.Color.rgb(220, 225, 232)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(30f, curY, 565f, curY + 54f, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = android.graphics.Color.rgb(40, 50, 65)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val notesContent = if (scan.notes.isNotBlank()) {
            scan.notes
        } else {
            "آنومالی ثبت‌شده توسط سیستم رادار OKM 3D نشان‌دهنده تغییر بارز در ساختار مغناطیسی زمین در عمق برآورد شده است. بررسی‌های فیزیکی تکمیلی جهت تفکیک دقیق آلیاژ و مشخصات دیواره حفره توصیه می‌شود."
        }
        canvas.drawText(notesContent.take(120), 42f, curY + 20f, paint)
        if (notesContent.length > 120) {
            canvas.drawText(notesContent.substring(120).take(120), 42f, curY + 36f, paint)
        }

        curY += 66f

        // 7. Signature & Certification Block
        paint.color = android.graphics.Color.rgb(215, 222, 232)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, curY + 36f, 220f, curY + 36f, paint)
        canvas.drawLine(375f, curY + 36f, 555f, curY + 36f, paint)

        paint.color = android.graphics.Color.rgb(90, 100, 115)
        paint.textSize = 8f
        canvas.drawText("Field Radar Operator Signature", 40f, curY + 48f, paint)
        canvas.drawText("Certified Senior Geophysicist Stamp & Seal", 375f, curY + 48f, paint)

        // 8. Footer Legal Note
        paint.color = android.graphics.Color.rgb(150, 160, 175)
        paint.textSize = 7.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("OKM 3D GROUND VISUALIZER • CONFIDENTIAL TECHNICAL SURVEY REPORT • ALL RIGHTS RESERVED", 297f, 825f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(18, 25, 40)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, x, y, paint)
    }

    private fun drawMetricBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        accentColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Badge background
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(x, y, x + width, y + height, 5f, 5f, paint)

        // Border
        paint.color = android.graphics.Color.rgb(215, 225, 238)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(x, y, x + width, y + height, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        // Label
        paint.color = android.graphics.Color.rgb(110, 120, 135)
        paint.textSize = 7f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(label, x + 8f, y + 14f, paint)

        // Value
        paint.color = accentColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value.take(24), x + 8f, y + 32f, paint)
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در اشتراک فایل: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
