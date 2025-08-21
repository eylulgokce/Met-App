package com.example.met_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val predictor: MLPredictor,
    private val accelerometerManager: AccelerometerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        observeAccelerometerFeatures()
    }

    private fun observeAccelerometerFeatures() {
        viewModelScope.launch {
            accelerometerManager.features.collect { features ->
                features?.let {
                    try {
                        val prediction = predictor.predict(it)
                        updateState(prediction)
                    } catch (e: Exception) {
                        // Handle prediction errors gracefully
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun startRecording() {
        if (accelerometerManager.isAccelerometerAvailable()) {
            accelerometerManager.startRecording()
            _isRecording.value = true
        }
    }

    fun stopRecording() {
        accelerometerManager.stopRecording()
        _isRecording.value = false
    }

    private fun updateState(predicted: MetClass) {
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

    fun resetToday() {
        _uiState.update {
            it.copy(
                durationsSec = MetClass.values().associateWith { 0L },
                lastUpdateEpochSec = System.currentTimeMillis() / 1000
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }
}