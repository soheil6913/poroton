package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Cyber Radar Palette
val CyberGold = Color(0xFFFFC837)
val CyberGoldVariant = Color(0xFFFF8008)
val CyberCyan = Color(0xFF00E5FF)
val CyberCyanDark = Color(0xFF00838F)
val CyberRed = Color(0xFFFF2A55)
val LaserGreen = Color(0xFF00FF66)
val DeepNavy = Color(0xFF000080)
val ElectricBlue = Color(0xFF0055FF)
val BrightYellow = Color(0xFFFFEE00)

val DarkBg = Color(0xFF0B0E14)
val SurfaceBg = Color(0xFF131722)
val CardBg = Color(0xFF1B2232)
val CardBgElevated = Color(0xFF232B3E)
val CardBorder = Color(0xFF2C384E)
val GrayText = Color(0xFF90A0B7)
val LightText = Color(0xFFF0F4F8)

/**
 * Returns color according to the Proton/OKM 6-color Neon Colormap:
 * -1.0 .. 0.0 -> #000080 Deep Navy (Cavity / Void / Negative anomaly)
 * 0.0 .. 0.2  -> #0000FF Blue (Low / Void boundary)
 * 0.2 .. 0.4  -> #00FFFF Cyan (Damp / Ferrous / Non-magnetic)
 * 0.4 .. 0.6  -> #00FF00 Laser Green (Normal background soil)
 * 0.6 .. 0.8  -> #FFFF00 Yellow (Mineralized / Medium target)
 * 0.8 .. 1.0+ -> #FF0000 Red (High conductivity / Gold / Precious metal target)
 */
fun getTargetColor(normalized: Float, threshold: Float = 0.0f): Color {
    val clamped = normalized.coerceIn(-1f, 2f)
    
    // If threshold filtering is enabled and signal magnitude is below threshold, dim it
    if (threshold > 0f && kotlin.math.abs(clamped) < threshold) {
        return Color(0xFF18202A)
    }

    // Map -1.0..2.0 to 0.0..1.0
    val t = ((clamped + 1f) / 3f).coerceIn(0f, 1f)

    return when {
        t < 0.2f -> {
            val factor = t / 0.2f
            lerpColor(DeepNavy, ElectricBlue, factor)
        }
        t < 0.4f -> {
            val factor = (t - 0.2f) / 0.2f
            lerpColor(ElectricBlue, CyberCyan, factor)
        }
        t < 0.6f -> {
            val factor = (t - 0.4f) / 0.2f
            lerpColor(CyberCyan, LaserGreen, factor)
        }
        t < 0.8f -> {
            val factor = (t - 0.6f) / 0.2f
            lerpColor(LaserGreen, BrightYellow, factor)
        }
        else -> {
            val factor = ((t - 0.8f) / 0.2f).coerceIn(0f, 1f)
            lerpColor(BrightYellow, CyberRed, factor)
        }
    }
}

/**
 * Linear color interpolation
 */
fun lerpColor(c1: Color, c2: Color, factor: Float): Color {
    val f = factor.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * f,
        green = c1.green + (c2.green - c1.green) * f,
        blue = c1.blue + (c2.blue - c1.blue) * f,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * f
    )
}
