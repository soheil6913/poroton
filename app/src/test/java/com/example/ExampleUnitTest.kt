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
}
