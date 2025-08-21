package com.example.met_app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class AccelerometerManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Buffer to store accelerometer readings
    private val dataBuffer = mutableListOf<AccelData>()
    private val windowSizeMs = 5000L // 5 seconds
    private val samplingRateUs = 20000 // 50Hz (1,000,000 / 50 = 20,000 microseconds)

    private val _features = MutableStateFlow<FloatArray?>(null)
    val features: StateFlow<FloatArray?> = _features.asStateFlow()

    private var isRecording = false

    data class AccelData(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )

    fun startRecording() {

        if (!isRecording && accelerometer != null) {
            isRecording = true
            sensorManager.registerListener(this, accelerometer, samplingRateUs)
        }
    }

    fun stopRecording() {
        if (isRecording) {
            isRecording = false
            sensorManager.unregisterListener(this)
            dataBuffer.clear()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val currentTime = System.currentTimeMillis()

                // Add new data point
                dataBuffer.add(
                    AccelData(
                        timestamp = currentTime,
                        x = it.values[0],
                        y = it.values[1],
                        z = it.values[2]
                    )
                )

                // Remove old data points outside the window
                val cutoffTime = currentTime - windowSizeMs
                dataBuffer.removeAll { data -> data.timestamp < cutoffTime }

                // Calculate features if we have enough data
                if (dataBuffer.size >= 50) { // At least 1 second of data at 50Hz
                    calculateFeatures()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun calculateFeatures() {
        if (dataBuffer.isEmpty()) return

        val xValues = dataBuffer.map { it.x }
        val yValues = dataBuffer.map { it.y }
        val zValues = dataBuffer.map { it.z }

        // Calculate means
        val xMean = xValues.average().toFloat()
        val yMean = yValues.average().toFloat()
        val zMean = zValues.average().toFloat()

        // Calculate variances
        val xVar = xValues.map { (it - xMean) * (it - xMean) }.average().toFloat()
        val yVar = yValues.map { (it - yMean) * (it - yMean) }.average().toFloat()
        val zVar = zValues.map { (it - zMean) * (it - zMean) }.average().toFloat()

        // Calculate standard deviations
        val xStd = sqrt(xVar)
        val yStd = sqrt(yVar)
        val zStd = sqrt(zVar)

        // Create feature array matching your model's expected input
        // ['x_mean', 'y_mean', 'z_mean', 'x_var', 'y_var', 'z_var', 'x_std', 'y_std', 'z_std']
        val featuresArray = floatArrayOf(
            xMean, yMean, zMean,
            xVar, yVar, zVar,
            xStd, yStd, zStd
        )

        _features.value = featuresArray
    }

    fun isAccelerometerAvailable(): Boolean {
        return accelerometer != null
    }
}