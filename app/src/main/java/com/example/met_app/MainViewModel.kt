package com.example.met_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(private val predictor: MLPredictor) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null
    private var simulateJob: Job? = null

    init {
        startDemoPredicting()
    }

    private fun startDemoPredicting() = viewModelScope.launch {
        while (isActive) {
            delay(1_000)
            val dummyFeatures = FloatArray(9) { (0..100).random() / 50f }
            val prediction = predictor.predict(dummyFeatures)
            updateState(prediction)
        }
    }

    fun onNewPrediction(met: MetClass) {
        updateState(met)
    }
    private fun updateState(predicted: MetClass) {
        _uiState.update { state ->
            val updatedDurations = state.durationsSec.toMutableMap()
            updatedDurations[predicted] = (updatedDurations[predicted] ?: 0) + 1
            state.copy(currentClass = predicted, durationsSec = updatedDurations)
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                _uiState.update { state ->
                    val updated = state.durationsSec.toMutableMap()
                    val cur = state.currentClass
                    updated[cur] = (updated[cur] ?: 0L) + 1L
                    state.copy(
                        durationsSec = updated,
                        lastUpdateEpochSec = state.lastUpdateEpochSec + 1
                    )
                }
            }
        }
    }

    // Temporary simulator: rotates through classes every ~15s.
    private fun startSimulatingClassChanges() {
        simulateJob?.cancel()
        simulateJob = viewModelScope.launch {
            val order = listOf(
                MetClass.SEDENTARY, MetClass.LIGHT, MetClass.MODERATE,
                MetClass.SEDENTARY, MetClass.LIGHT
            )
            var i = 0
            while (isActive) {
                delay(15_000L)
                i = (i + 1) % order.size
                _uiState.update { it.copy(currentClass = order[i]) }
            }
        }
    }

    fun resetToday() {
        _uiState.update { it.copy(durationsSec = MetClass.values().associateWith { 0L }) }
    }
}
