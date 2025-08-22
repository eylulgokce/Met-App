package com.example.met_app

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(
    private val predictor: MLPredictor,
    private val accelerometerManager: AccelerometerManager,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @RequiresApi(Build.VERSION_CODES.O)
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MainViewModel(predictor, accelerometerManager, database) as T
            } else {
                TODO("VERSION.SDK_INT < O")
            }
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}