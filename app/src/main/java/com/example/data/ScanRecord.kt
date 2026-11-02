package com.example.data

import java.util.UUID

data class ScanRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val gridWidth: Int,
    val gridLength: Int,
    val soilType: String,
    val scanPattern: String,
    val data: List<Float>,
    val notes: String = "",
    val maxSignal: Float = data.maxOrNull() ?: 0f,
    val minSignal: Float = data.minOrNull() ?: 0f,
    val estimatedDepthMeters: Float = 0f,
    val anomalyClassification: String = "هدف ناشناخته (Unknown Anomaly)"
)
