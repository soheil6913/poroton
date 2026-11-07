package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.ScanRecord
import com.example.ui.theme.*
import kotlin.math.*

private data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val originalVal: Float,
    val gridX: Int,
    val gridY: Int,
    val color: Color
)

private data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val depthZ: Float,
    val pt: Point3D
)

private data class Quad3D(
    val p0: ProjectedPoint,
    val p1: ProjectedPoint,
    val p2: ProjectedPoint,
    val p3: ProjectedPoint,
    val avgZ: Float,
    val color: Color
)

@Composable
fun Native3DCanvas(
    scan: ScanRecord,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    zScale: Float,
    colorThreshold: Float,
    renderStyle: String,
    filterMode: String,
    sliceAxis: String,
    sliceIndex: Int,
    isTopDown2D: Boolean = false,
    onRotate: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    onSelectNode: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val width = scan.gridWidth
    val length = scan.gridLength
    val data = scan.data

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (gestureZoom != 1f) {
                        onZoom(gestureZoom)
                    }
                    if (pan != Offset.Zero) {
                        // If large drag, apply rotation; if slight, apply pan
                        onRotate(pan.x * 0.45f, -pan.y * 0.45f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW * 0.5f + panX
            val centerY = canvasH * 0.52f + panY
            val baseScale = (min(canvasW, canvasH) * 0.44f) * zoom

            val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
            val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)
            val cosPitch = cos(pitchRad)
            val sinPitch = sin(pitchRad)

            // Normalize signal data: baseline = 250
            val maxSignal = max(scan.maxSignal, 400f)
            val minSignal = min(scan.minSignal, 100f)
            val signalRange = (maxSignal - minSignal).coerceAtLeast(100f)

            // 1. Build 3D mesh points
            val gridPoints = mutableListOf<Point3D>()
            for (y in 0 until length) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val rawVal = data.getOrNull(idx) ?: 250f

                    // Filter mode: ALL, METAL_ONLY (high), CAVITY_ONLY (low)
                    val isFilteredOut = when (filterMode) {
                        "METAL_ONLY" -> rawVal < 350f
                        "CAVITY_ONLY" -> rawVal > 200f
                        else -> false
                    }

                    // Slice axis filter
                    val isSlicedOut = when (sliceAxis) {
                        "X_AXIS" -> x != sliceIndex
                        "Y_AXIS" -> y != sliceIndex
                        else -> false
                    }

                    // Normalized 3D coordinates: center is (0, 0)
                    val normX = if (width > 1) (x.toFloat() / (width - 1) - 0.5f) * 2f else 0f
                    val normY = if (length > 1) (y.toFloat() / (length - 1) - 0.5f) * 2f else 0f

                    // Normalized signal for height and color: map to -1.0 .. +2.0 as in Python script
                    val normValForColor = ((rawVal - 250f) / 200f).coerceIn(-1f, 2f)
                    val normZ = ((rawVal - 250f) / signalRange) * 1.5f * zScale

                    val color = if (isFilteredOut || isSlicedOut) {
                        Color(0xFF151922).copy(alpha = 0.2f)
                    } else {
                        getTargetColor(normValForColor, colorThreshold)
                    }

                    gridPoints.add(
                        Point3D(
                            x = normX,
                            y = normY,
                            z = normZ,
                            originalVal = rawVal,
                            gridX = x,
                            gridY = y,
                            color = color
                        )
                    )
                }
            }

            // 2. Project 3D points to 2D
            val projected = gridPoints.map { pt ->
                // Rotate around Z-up / Y-depth
                val rx1 = pt.x * cosYaw - pt.y * sinYaw
                val ry1 = pt.x * sinYaw + pt.y * cosYaw
                val rz1 = pt.z

                // Rotate around Pitch (X axis tilt)
                val rx2 = rx1
                val ry2 = ry1 * cosPitch - rz1 * sinPitch
                val rz2 = ry1 * sinPitch + rz1 * cosPitch

                val sx = centerX + rx2 * baseScale
                val sy = centerY + ry2 * baseScale

                ProjectedPoint(
                    screenX = sx,
                    screenY = sy,
                    depthZ = rz2,
                    pt = pt
                )
            }

            if (isTopDown2D) {
                // 2D Top-Down Orthographic Radar Matrix
                val gridSide = min(canvasW * 0.76f, canvasH * 0.56f) * zoom
                val cellW = gridSide / width
                val cellH = gridSide / length
                val startX = centerX - gridSide / 2f
                val startY = centerY - gridSide / 2f

                drawRect(
                    color = Color(0xFF0C1018),
                    topLeft = Offset(startX - 4f, startY - 4f),
                    size = androidx.compose.ui.geometry.Size(gridSide + 8f, gridSide + 8f)
                )

                for (y in 0 until length) {
                    for (x in 0 until width) {
                        val idx = y * width + x
                        val rawVal = data.getOrNull(idx) ?: 250f
                        val norm = ((rawVal - 250f) / 200f).coerceIn(-1f, 2f)
                        val cellColor = getTargetColor(norm, colorThreshold)

                        val cellX = startX + x * cellW
                        val cellY = startY + y * cellH

                        drawRect(
                            color = cellColor,
                            topLeft = Offset(cellX + 1f, cellY + 1f),
                            size = androidx.compose.ui.geometry.Size(cellW - 2f, cellH - 2f)
                        )
                    }
                }

                drawRect(
                    color = CyberCyan.copy(alpha = 0.6f),
                    topLeft = Offset(startX, startY),
                    size = androidx.compose.ui.geometry.Size(gridSide, gridSide),
                    style = Stroke(width = 2f)
                )
            } else {
                // 3. Draw Ground Base Wireframe Floor (Subsurface Grid reference)
                drawBaseWireframe(centerX, centerY, baseScale, cosYaw, sinYaw, cosPitch, sinPitch)

                // 4. Render Mesh based on style
                when (renderStyle) {
                    "SURFACE" -> {
                        // Form quads and sort by avgZ back to front (Painter's Algorithm)
                        val quads = mutableListOf<Quad3D>()
                        for (y in 0 until length - 1) {
                            for (x in 0 until width - 1) {
                                val idx0 = y * width + x
                                val idx1 = y * width + (x + 1)
                                val idx2 = (y + 1) * width + (x + 1)
                                val idx3 = (y + 1) * width + x

                                val p0 = projected[idx0]
                                val p1 = projected[idx1]
                                val p2 = projected[idx2]
                                val p3 = projected[idx3]

                                val avgZ = (p0.depthZ + p1.depthZ + p2.depthZ + p3.depthZ) / 4f
                                val avgVal = (p0.pt.originalVal + p1.pt.originalVal + p2.pt.originalVal + p3.pt.originalVal) / 4f
                                val normVal = ((avgVal - 250f) / 200f).coerceIn(-1f, 2f)
                                val quadColor = getTargetColor(normVal, colorThreshold)

                                quads.add(Quad3D(p0, p1, p2, p3, avgZ, quadColor))
                            }
                        }

                        // Sort back-to-front
                        quads.sortBy { it.avgZ }

                        quads.forEach { quad ->
                            val path = Path().apply {
                                moveTo(quad.p0.screenX, quad.p0.screenY)
                                lineTo(quad.p1.screenX, quad.p1.screenY)
                                lineTo(quad.p2.screenX, quad.p2.screenY)
                                lineTo(quad.p3.screenX, quad.p3.screenY)
                                close()
                            }
                            // Fill polygon with high alpha for rich vibrant color
                            drawPath(path, quad.color.copy(alpha = 0.96f))
                            // Draw subtle wire edge
                            drawPath(path, Color.White.copy(alpha = 0.20f), style = Stroke(width = 1.2f))
                        }
                    }
                    "WIREFRAME" -> {
                        // Draw horizontal and vertical connecting lines
                        for (y in 0 until length) {
                            for (x in 0 until width) {
                                val curr = projected[y * width + x]
                                // Right neighbor
                                if (x < width - 1) {
                                    val right = projected[y * width + (x + 1)]
                                    drawLine(
                                        color = curr.pt.color.copy(alpha = 0.92f),
                                        start = Offset(curr.screenX, curr.screenY),
                                        end = Offset(right.screenX, right.screenY),
                                        strokeWidth = 2.5f
                                    )
                                }
                                // Bottom neighbor
                                if (y < length - 1) {
                                    val bottom = projected[(y + 1) * width + x]
                                    drawLine(
                                        color = curr.pt.color.copy(alpha = 0.92f),
                                        start = Offset(curr.screenX, curr.screenY),
                                        end = Offset(bottom.screenX, bottom.screenY),
                                        strokeWidth = 2.5f
                                    )
                                }
                                // Node dot
                                drawCircle(
                                    color = curr.pt.color,
                                    radius = 3.2f,
                                    center = Offset(curr.screenX, curr.screenY)
                                )
                            }
                        }
                    }
                    "VOXEL" -> {
                        // Draw vertical extruded pillars
                        val sortedPoints = projected.sortedBy { it.depthZ }
                        sortedPoints.forEach { pt ->
                            val rx1 = pt.pt.x * cosYaw - pt.pt.y * sinYaw
                            val ry1 = pt.pt.x * sinYaw + pt.pt.y * cosYaw
                            val groundY = centerY + (ry1 * cosPitch) * baseScale
                            val groundX = centerX + rx1 * baseScale

                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(pt.pt.color, pt.pt.color.copy(alpha = 0.25f)),
                                    startY = min(pt.screenY, groundY),
                                    endY = max(pt.screenY, groundY)
                                ),
                                start = Offset(groundX, groundY),
                                end = Offset(pt.screenX, pt.screenY),
                                strokeWidth = (baseScale / width) * 0.7f
                            )
                            drawCircle(
                                color = pt.pt.color,
                                radius = (baseScale / width) * 0.4f,
                                center = Offset(pt.screenX, pt.screenY)
                            )
                        }
                    }
                    "POINTS" -> {
                        projected.forEach { pt ->
                            val radius = (3.5f + (pt.pt.originalVal / 120f)).coerceIn(3f, 10f)
                            drawCircle(
                                color = pt.pt.color.copy(alpha = 0.35f),
                                radius = radius * 1.8f,
                                center = Offset(pt.screenX, pt.screenY)
                            )
                            drawCircle(
                                color = pt.pt.color,
                                radius = radius,
                                center = Offset(pt.screenX, pt.screenY)
                            )
                        }
                    }
                }
            }

            // 5. Draw Left Proton Colormap Colorbar (Signal Strength legend)
            drawProtonColorBar(canvasH, maxSignal, minSignal)
        }
    }
}

/**
 * Draws the ground plane wireframe bounding box at Z = 0
 */
private fun DrawScope.drawBaseWireframe(
    centerX: Float,
    centerY: Float,
    baseScale: Float,
    cosYaw: Float,
    sinYaw: Float,
    cosPitch: Float,
    sinPitch: Float
) {
    val corners = listOf(
        Pair(-1f, -1f),
        Pair(1f, -1f),
        Pair(1f, 1f),
        Pair(-1f, 1f)
    )
    val projCorners = corners.map { (cx, cy) ->
        val rx = cx * cosYaw - cy * sinYaw
        val ry = cx * sinYaw + cy * cosYaw
        Offset(
            centerX + rx * baseScale,
            centerY + (ry * cosPitch) * baseScale
        )
    }

    // Outer boundary of subsurface grid
    for (i in 0 until 4) {
        val next = (i + 1) % 4
        drawLine(
            color = CyberCyan.copy(alpha = 0.35f),
            start = projCorners[i],
            end = projCorners[next],
            strokeWidth = 1.8f
        )
    }
}

/**
 * Draws the vertical Proton Colormap colorbar on the left with tick marks and signal units
 */
private fun DrawScope.drawProtonColorBar(canvasH: Float, maxSignal: Float, minSignal: Float) {
    val barX = 28f
    val barTop = canvasH * 0.22f
    val barHeight = canvasH * 0.48f
    val barWidth = 14f

    // Gradient bar from Red (top) -> Yellow -> Green -> Cyan -> Blue -> Navy (bottom)
    val colorStops = listOf(
        CyberRed,
        BrightYellow,
        LaserGreen,
        CyberCyan,
        ElectricBlue,
        DeepNavy
    )

    drawRect(
        brush = Brush.verticalGradient(
            colors = colorStops,
            startY = barTop,
            endY = barTop + barHeight
        ),
        topLeft = Offset(barX, barTop),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
    )

    // Border
    drawRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(barX, barTop),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
        style = Stroke(width = 1.2f)
    )

    // Top Tick (+ Peak / Gold)
    drawCircle(color = CyberRed, radius = 3.5f, center = Offset(barX + barWidth + 6f, barTop))
    // Center Tick (Normal Soil)
    drawCircle(color = LaserGreen, radius = 2.5f, center = Offset(barX + barWidth + 6f, barTop + barHeight * 0.5f))
    // Bottom Tick (- Void / Cavity)
    drawCircle(color = DeepNavy, radius = 3.5f, center = Offset(barX + barWidth + 6f, barTop + barHeight))
}
