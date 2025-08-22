package com.example.met_app

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ActivityDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ActivityRecord)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ActivitySession)

    @Query("SELECT * FROM activity_sessions WHERE date = :date ORDER BY startTime ASC")
    suspend fun getSessionsForDate(date: LocalDate): List<ActivitySession>

    @Transaction
    suspend fun insertOrUpdateWithConfidence(record: ActivityRecord) {
        val existing = getRecordForDateAndClass(record.date, record.metClass)
        if (existing != null && record.confidence >= existing.confidence) {
            val updated = existing.copy(
                durationMinutes = existing.durationMinutes + record.durationMinutes,
                confidence = record.confidence,
                timestamp = record.timestamp
            )
            insert(updated)
        } else if (existing == null) {
            insert(record)
        }
    }
    @Transaction
    suspend fun insertOrUpdate(record: ActivityRecord) {
        val existing = getRecordForDateAndClass(record.date, record.metClass)
        if (existing != null && record.confidence >= existing.confidence) {
            val updated = existing.copy(
                durationMinutes = existing.durationMinutes + record.durationMinutes,
                confidence = record.confidence,
                timestamp = record.timestamp
            )
            insert(updated)
        } else if (existing == null) {
            insert(record)
        }
    }


    @Query("SELECT * FROM activity_records WHERE date = :date AND metClass = :metClass LIMIT 1")
    suspend fun getRecordForDateAndClass(date: LocalDate, metClass: MetClass): ActivityRecord?

    @Query("""
        SELECT 
            date,
            COALESCE(SUM(CASE WHEN metClass = 'SEDENTARY' THEN durationMinutes END), 0) as sedentaryMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'LIGHT' THEN durationMinutes END), 0) as lightMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'MODERATE' THEN durationMinutes END), 0) as moderateMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'VIGOROUS' THEN durationMinutes END), 0) as vigorousMinutes
        FROM activity_records 
        WHERE date = :date 
        GROUP BY date
    """)
    suspend fun getDailySummary(date: LocalDate): DailySummary?

    @Query("""
        SELECT 
            date,
            COALESCE(SUM(CASE WHEN metClass = 'SEDENTARY' THEN durationMinutes END), 0) as sedentaryMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'LIGHT' THEN durationMinutes END), 0) as lightMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'MODERATE' THEN durationMinutes END), 0) as moderateMinutes,
            COALESCE(SUM(CASE WHEN metClass = 'VIGOROUS' THEN durationMinutes END), 0) as vigorousMinutes
        FROM activity_records 
        WHERE date >= :startDate AND date <= :endDate 
        GROUP BY date 
        ORDER BY date ASC
    """)
    suspend fun getWeeklySummary(startDate: LocalDate, endDate: LocalDate): List<DailySummary>

    @Query("SELECT * FROM activity_records WHERE date >= :fromDate ORDER BY date DESC, timestamp DESC")
    fun getRecordsFlow(fromDate: LocalDate): Flow<List<ActivityRecord>>

    @Query("DELETE FROM activity_records WHERE date < :beforeDate")
    suspend fun deleteOldRecords(beforeDate: LocalDate)

    @Query("SELECT * FROM activity_records WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getRecordsForDate(date: LocalDate): List<ActivityRecord>
}
