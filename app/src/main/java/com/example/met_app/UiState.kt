package com.example.met_app

data class UiState(
    val currentClass: MetClass = MetClass.SEDENTARY,
    val durationsSec: Map<MetClass, Long> = MetClass.values().associateWith { 0L },
    val lastUpdateEpochSec: Long = System.currentTimeMillis() / 1000,
    val isMLActive: Boolean = false,
    val modelAccuracy: Float = 0.85f, // For displaying model confidence
    val predictionConfidence: Float = 0.0f, // Confidence of current prediction
    val dailyGoals: Map<MetClass, Long> = mapOf(
        MetClass.SEDENTARY to 8 * 3600L, // 8 hours max
        MetClass.LIGHT to 2 * 3600L,     // 2 hours min
        MetClass.MODERATE to 1 * 3600L,   // 1 hour min
        MetClass.VIGOROUS to 30 * 60L     // 30 minutes min
    )
)