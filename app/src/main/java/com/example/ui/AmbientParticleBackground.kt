package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.DarkBg
import com.example.ui.theme.SurfaceBg

@Composable
fun AmbientParticleBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundParticles")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseAnim"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep gradient backdrop
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    SurfaceBg,
                    DarkBg,
                    Color(0xFF06080B)
                ),
                center = Offset(w * 0.5f, h * 0.4f),
                radius = w.coerceAtLeast(h) * 0.8f
            )
        )

        // Subtle tech grid lines
        val step = 48f
        var x = 0f
        while (x < w) {
            drawLine(
                color = Color.White.copy(alpha = 0.02f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color.White.copy(alpha = 0.02f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
            y += step
        }

        // Ambient cyber floating energy orbs
        val numParticles = 16
        for (i in 0 until numParticles) {
            val progress = (phase + (i.toFloat() / numParticles)) % 1f
            val px = (w * (0.15f + 0.7f * ((i * 73) % 100) / 100f))
            val py = h * (1f - progress)
            val radius = 2.5f + (i % 4) * 1.5f
            val alpha = (1f - (progress - 0.5f) * (progress - 0.5f) * 4f).coerceIn(0f, 0.45f)
            val color = if (i % 2 == 0) CyberGold else CyberCyan

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}
