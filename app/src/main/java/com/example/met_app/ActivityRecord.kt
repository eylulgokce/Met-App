package com.example.met_app

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "activity_sessions")
data class ActivitySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val metClass: MetClass,
    val confidence: Float = 1.0f,
    val date: LocalDate
)


@Entity(tableName = "activity_records")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val metClass: MetClass,
    val durationMinutes: Int,
    val confidence: Float = 1.0f, // Add this field
    val timestamp: Long = System.currentTimeMillis()
)

// Daily summary data class
data class DailySummary(
    val date: LocalDate,
    val sedentaryMinutes: Int,
    val lightMinutes: Int,
    val moderateMinutes: Int,
    val vigorousMinutes: Int
) {
    val totalMinutes: Int
        get() = sedentaryMinutes + lightMinutes + moderateMinutes + vigorousMinutes

    fun getDurationForClass(metClass: MetClass): Int {
        return when (metClass) {
            MetClass.SEDENTARY -> sedentaryMinutes
            MetClass.LIGHT -> lightMinutes
            MetClass.MODERATE -> moderateMinutes
            MetClass.VIGOROUS -> vigorousMinutes
        }
    }
}

