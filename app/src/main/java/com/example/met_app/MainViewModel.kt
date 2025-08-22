package com.example.met_app

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@RequiresApi(Build.VERSION_CODES.O)
class MainViewModel(
    private val predictor: MLPredictor,
    private val accelerometerManager: AccelerometerManager,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _dailySummary = MutableStateFlow<DailySummary?>(null)
    val dailySummary: StateFlow<DailySummary?> = _dailySummary.asStateFlow()

    private val _weeklySummary = MutableStateFlow<List<DailySummary>>(emptyList())
    val weeklySummary: StateFlow<List<DailySummary>> = _weeklySummary.asStateFlow()

    private val _showDailyExpanded = MutableStateFlow(false)
    val showDailyExpanded: StateFlow<Boolean> = _showDailyExpanded.asStateFlow()

    private val _showWeeklyExpanded = MutableStateFlow(false)
    val showWeeklyExpanded: StateFlow<Boolean> = _showWeeklyExpanded.asStateFlow()

    init {
        observeAccelerometerFeatures()
        loadTodaysSummary()
        loadWeeklySummary()

    }

    private fun observeAccelerometerFeatures() {
        viewModelScope.launch {
            accelerometerManager.features.collect { features ->
                features?.let {
                    try {
                        val predResult = predictor.predictWithConfidence(it)
                        updateState(predResult.metClass, predResult.confidence)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun startTracking() {
        if (accelerometerManager.isAccelerometerAvailable()) {
            _isTracking.value = true
            viewModelScope.launch {
                accelerometerManager.startRecording()
            }
        }
    }

    fun stopTracking() {
        _isTracking.value = false
        accelerometerManager.stopRecording()
    }

    private fun updateState(predicted: MetClass, confidence: Float) {
        if (confidence < 0.3f) return

        _uiState.update { state ->
            val updatedDurations = state.durationsSec.toMutableMap()
            updatedDurations[predicted] = (updatedDurations[predicted] ?: 0) + 1
            state.copy(
                currentClass = predicted,
                durationsSec = updatedDurations,
                lastUpdateEpochSec = System.currentTimeMillis() / 1000
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun resetToday() {
        _uiState.update {
            it.copy(
                durationsSec = MetClass.values().associateWith { 0L },
                lastUpdateEpochSec = System.currentTimeMillis() / 1000
            )
        }

        // clear todays db records
        viewModelScope.launch {
            val today = LocalDate.now()
            database.activityDao().getRecordsForDate(today).forEach { record ->
                // TODO delete method / reset durations method in db
            }
            loadTodaysSummary()
        }
    }

    private fun loadTodaysSummary() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val summary = database.activityDao().getDailySummary(today)
            _dailySummary.value = summary
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadWeeklySummary() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val endOfWeek = startOfWeek.plusDays(6)

            val summary = database.activityDao().getWeeklySummary(startOfWeek, endOfWeek)
            _weeklySummary.value = summary
        }
    }

    fun toggleDailyExpanded() {
        _showDailyExpanded.value = !_showDailyExpanded.value
        if (_showDailyExpanded.value) {
            loadTodaysSummary()
        }
    }

    fun toggleWeeklyExpanded() {
        _showWeeklyExpanded.value = !_showWeeklyExpanded.value
        if (_showWeeklyExpanded.value) {
            loadWeeklySummary()
        }
    }

    fun refreshData() {
        loadTodaysSummary()
        loadWeeklySummary()
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}