package com.example

import com.example.ui.theme.DeepNavy
import com.example.ui.theme.CyberRed
import com.example.ui.theme.LaserGreen
import com.example.ui.theme.getTargetColor
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testProtonColormapExtremes() {
        // -1.0 should map to the deepest void color (DeepNavy)
        val cavityColor = getTargetColor(-1.0f)
        assertEquals(DeepNavy.red, cavityColor.red, 0.05f)

        // +2.0 should map to the highest metal/gold color (CyberRed)
        val goldColor = getTargetColor(2.0f)
        assertEquals(CyberRed.red, goldColor.red, 0.05f)

        // Baseline (normal ground) should be near laser green
        val groundColor = getTargetColor(0.2f)
        assertTrue(groundColor.green > 0.5f)
    }

    @Test
    fun testThresholdDamping() {
        // Small values below threshold should return dark background
        val dimmedColor = getTargetColor(0.02f, threshold = 0.1f)
        assertTrue(dimmedColor.red < 0.2f && dimmedColor.green < 0.2f && dimmedColor.blue < 0.25f)
    }

    @Test
    fun testScanFileImporterMatrix() {
        val sampleCsv = """
            240, 245, 250, 260
            242, 248, 520, 262
            240, 244, 250, 258
        """.trimIndent()

        val scan = com.example.util.ScanFileImporter.parseContent("survey_site_1.csv", sampleCsv)
        assertEquals("survey_site_1", scan.name)
        assertEquals(4, scan.gridWidth)
        assertEquals(3, scan.gridLength)
        assertEquals(12, scan.data.size)
        assertEquals(520f, scan.maxSignal, 0.1f)
        assertTrue(scan.anomalyClassification.contains("طلا") || scan.anomalyClassification.contains("فلز"))
    }

    @Test
    fun testScanFileImporterJson() {
        val sampleJson = """
            {
              "name": "Buried Chamber Tomb",
              "gridWidth": 3,
              "gridLength": 3,
              "soilType": "سنگ و صخره (Hard Rock)",
              "data": [240, 240, 240, 240, 120, 240, 240, 240, 240]
            }
        """.trimIndent()

        val scan = com.example.util.ScanFileImporter.parseContent("tomb.json", sampleJson)
        assertEquals("Buried Chamber Tomb", scan.name)
        assertEquals(3, scan.gridWidth)
        assertEquals(3, scan.gridLength)
        assertEquals(9, scan.data.size)
        assertEquals(120f, scan.minSignal, 0.1f)
        assertTrue(scan.anomalyClassification.contains("حفره") || scan.anomalyClassification.contains("خالی"))
    }
}
