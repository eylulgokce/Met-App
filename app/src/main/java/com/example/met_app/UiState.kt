package com.example.met_app

data class UiState(
    val currentClass: MetClass = MetClass.SEDENTARY,
    val durationsSec: Map<MetClass, Long> = MetClass.values().associateWith { 0L },
    val lastUpdateEpochSec: Long = System.currentTimeMillis() / 1000
)
