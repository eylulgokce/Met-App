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
    private var samplingRateUs = 20000 // 50Hz (1,000,000 / 50 = 20,000 microseconds)

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

                dataBuffer.add(
                    AccelData(
                        timestamp = currentTime,
                        x = it.values[0],
                        y = it.values[1],
                        z = it.values[2]
                    )
                )

                val cutoffTime = currentTime - windowSizeMs
                dataBuffer.removeAll { data -> data.timestamp < cutoffTime }

                // Validate sampling rate before calculating features
                if (dataBuffer.size >= 30 && isValidSamplingRate()) {
                    calculateFeatures()
                }
            }
        }
    }

    private fun isValidSamplingRate(): Boolean {
        if (dataBuffer.size < 10) return false

        val timeSpan = dataBuffer.last().timestamp - dataBuffer.first().timestamp
        val actualSamplingRate = (dataBuffer.size - 1) * 1000.0 / timeSpan

        return actualSamplingRate >= 20.0 // At least 20Hz
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun calculateFeatures() {
        if (dataBuffer.isEmpty()) return

        val xValues = dataBuffer.map { it.x }
        val yValues = dataBuffer.map { it.y }
        val zValues = dataBuffer.map { it.z }

        // Calculate magnitudes
        val magnitudes = dataBuffer.map { sqrt(it.x*it.x + it.y*it.y + it.z*it.z) }

        // statistical features
        val xMean = xValues.average().toFloat()
        val yMean = yValues.average().toFloat()
        val zMean = zValues.average().toFloat()
        val magnitudeMean = magnitudes.average().toFloat()

        val xVar = xValues.map { (it - xMean) * (it - xMean) }.average().toFloat()
        val yVar = yValues.map { (it - yMean) * (it - yMean) }.average().toFloat()
        val zVar = zValues.map { (it - zMean) * (it - zMean) }.average().toFloat()
        val magnitudeVar = magnitudes.map { (it - magnitudeMean) * (it - magnitudeMean) }.average().toFloat()

        // movement intensity
        val movementIntensity = calculateMovementIntensity(magnitudes)

        val featuresArray = floatArrayOf(
            xMean, yMean, zMean, magnitudeMean,
            xVar, yVar, zVar, magnitudeVar,
            sqrt(xVar), sqrt(yVar), sqrt(zVar), sqrt(magnitudeVar),
            movementIntensity
        )

        _features.value = featuresArray
    }

    private fun calculateMovementIntensity(magnitudes: List<Float>): Float {
        if (magnitudes.size < 2) return 0f

        var totalChange = 0f
        for (i in 1 until magnitudes.size) {
            totalChange += kotlin.math.abs(magnitudes[i] - magnitudes[i-1])
        }
        return totalChange / (magnitudes.size - 1)
    }

    fun isAccelerometerAvailable(): Boolean {
        return accelerometer != null
    }

    fun getCurrentSamplingRate(): Int = samplingRateUs

    fun setSamplingRate(newRate: Int) {
        samplingRateUs = newRate
    }

}