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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


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

    // --- Demo mode toggles & state ---
    private var demoMode: Boolean = false
    private var demoJob: kotlinx.coroutines.Job? = null

    fun enableDemoMode(enable: Boolean) {
        demoMode = enable
    }


    fun startRecording() {
        if (demoMode || !isAccelerometerAvailable()) {
            startDemoEmission()
            return
        }
        if (!isRecording && accelerometer != null) {
            isRecording = true
            sensorManager.registerListener(this, accelerometer, samplingRateUs)
        }
    }

    fun stopRecording() {
        if (demoMode) {
            demoJob?.cancel()
            demoJob = null
            _features.value = null
            return
        }
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
    private fun startDemoEmission() {
        if (demoJob != null) return
        demoJob = GlobalScope.launch(Dispatchers.Default) {
            // Emit ~6–7 windows per second
            val dtMs = 150L

            // Each phase is tuned to push the model toward a target class
            // Feature order: [x_mean, y_mean, z_mean, x_var, y_var, z_var, x_std, y_std, z_std]
            data class Phase(val label: MetClass, val seconds: Int, val meanBias: Float, val varScale: Float, val noise: Float)

            val phases = listOf(
                // Very low variance/std, near-zero means -> Sedentary
                Phase(MetClass.SEDENTARY, seconds = 12, meanBias = 0.00f, varScale = 0.002f, noise = 0.001f),

                // Small rhythmic motion -> Light
                Phase(MetClass.LIGHT,     seconds = 12, meanBias = 0.02f, varScale = 0.015f, noise = 0.006f),

                // Medium rhythmic motion -> Moderate
                Phase(MetClass.MODERATE,  seconds = 12, meanBias = 0.04f, varScale = 0.045f, noise = 0.015f),

                // Highest amplitude + noise bursts -> Vigorous
                Phase(MetClass.VIGOROUS,  seconds = 12, meanBias = 0.06f, varScale = 0.10f,  noise = 0.035f)
            )

            var phaseIdx = 0
            var ticksInPhase = 0
            var t = 0.0

            fun nextFeatures(p: Phase, tt: Double): FloatArray {
                // small sinusoid around meanBias; variance/std derived from varScale; add white noise
                fun osc(freq: Double) = kotlin.math.sin(freq * tt).toFloat()
                val xm = p.meanBias * osc(1.0) + (p.noise * (Math.random() - 0.5f)).toFloat()
                val ym = p.meanBias * osc(0.8) + (p.noise * (Math.random() - 0.5f)).toFloat()
                val zm = p.meanBias * osc(1.3) + (p.noise * (Math.random() - 0.5f)).toFloat()

                val xv = p.varScale * 1.0f + p.noise * p.noise
                val yv = p.varScale * 1.2f + p.noise * p.noise
                val zv = p.varScale * 1.1f + p.noise * p.noise

                val xs = kotlin.math.sqrt(xv)
                val ys = kotlin.math.sqrt(yv)
                val zs = kotlin.math.sqrt(zv)

                return floatArrayOf(xm, ym, zm, xv, yv, zv, xs, ys, zs)
            }

            while (isActive) {
                val p = phases[phaseIdx]
                _features.value = nextFeatures(p, t)

                // advance time & phase
                t += 0.18
                ticksInPhase += 1
                val ticksPerPhase = (p.seconds * 1000) / dtMs
                if (ticksInPhase >= ticksPerPhase) {
                    ticksInPhase = 0
                    phaseIdx = (phaseIdx + 1) % phases.size
                }
                delay(dtMs)
            }
        }
    }


}