package com.example.met_app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.time.LocalDate

class ActivityTrackingService : Service() {

    private lateinit var accelerometerManager: AccelerometerManager
    private lateinit var predictor: MLPredictor
    private lateinit var database: AppDatabase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channelId = "activity_tracking_channel"
    private val notificationId = 1

    private var currentActivity = MetClass.SEDENTARY
    private var currentConfidence = 0.0f
    private var lastSaveTime = System.currentTimeMillis()
    private var sessionStartTime = System.currentTimeMillis()
    private val saveIntervalMs = 60000L // every minute
    private val batteryCheckIntervalMs = 300000L // Check battery every 5 min

    companion object {
        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"

        fun startTracking(context: Context) {
            val intent = Intent(context, ActivityTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, ActivityTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        predictor = MLPredictor()
        try {
            predictor.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle model initialization failure
            stopSelf()
            return
        }

        accelerometerManager = AccelerometerManager(this)
        database = AppDatabase.getDatabase(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
        }
        return START_STICKY // Restart if killed
    }

    private fun startTracking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = createNotification("Starting activity tracking...")
            startForeground(notificationId, notification)
        }

        sessionStartTime = System.currentTimeMillis()
        accelerometerManager.startRecording()

        // Main prediction loop
        serviceScope.launch {
            accelerometerManager.features.collect { features ->
                features?.let {
                    try {
                        val predResult = predictor.predictWithConfidence(it)

                        // Only update if activity changed or confidence is high enough
                        if (predResult.metClass != currentActivity ||
                            predResult.confidence > currentConfidence + 0.1f) {

                            // Save current session before switching
                            if (currentActivity != predResult.metClass) {
                                saveCurrentSession()
                            }

                            currentActivity = predResult.metClass
                            currentConfidence = predResult.confidence
                            sessionStartTime = System.currentTimeMillis()

                            updateNotification(
                                "Current: ${currentActivity.label} (${(currentConfidence * 100).toInt()}%)"
                            )
                        }

                        // Save to database each min
                        if (System.currentTimeMillis() - lastSaveTime >= saveIntervalMs) {
                            saveCurrentActivity()
                            lastSaveTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        //sedentary on prediction errors
                        currentActivity = MetClass.SEDENTARY
                        currentConfidence = 0.0f
                    }
                }
            }
        }

        // Battery optimization monitoring
        serviceScope.launch {
            while (isActive) {
                delay(batteryCheckIntervalMs)
                adjustSamplingRateBasedOnBattery()
            }
        }

        // Auto-save and cleanup on new day
        serviceScope.launch {
            while (isActive) {
                delay(60000) // by min
                checkForNewDay()
            }
        }
    }

    private fun stopTracking() {
        accelerometerManager.stopRecording()
        serviceScope.launch {
            saveCurrentSession()
            saveCurrentActivity()
            stopSelf()
        }
    }

    private suspend fun saveCurrentActivity() {
        val today = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val activityRecord = ActivityRecord(
            date = today,
            metClass = currentActivity,
            durationMinutes = 1,
            confidence = currentConfidence
        )

        try {
            database.activityDao().insertOrUpdateWithConfidence(activityRecord)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveCurrentSession() {
        val currentTime = System.currentTimeMillis()
        val sessionDuration = currentTime - sessionStartTime

        // save only when sessions are longer than 30s
        if (sessionDuration > 30000) {
            val today = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDate.now()
            } else {
                TODO("VERSION.SDK_INT < O")
            }
            val session = ActivitySession(
                startTime = sessionStartTime,
                endTime = currentTime,
                metClass = currentActivity,
                confidence = currentConfidence,
                date = today
            )

            try {
                database.activityDao().insertSession(session)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun checkForNewDay() {
        val currentHour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.time.LocalTime.now().hour
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        // Reset at midnight (2400)
        if (currentHour == 0 &&
            (System.currentTimeMillis() - lastSaveTime) >= saveIntervalMs) {

            saveCurrentSession()
            saveCurrentActivity()

            // Clean old data (older than 1 month)
            try {
                database.activityDao().deleteOldRecords(LocalDate.now().minusMonths(1))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun adjustSamplingRateBasedOnBattery() {
        try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val newSamplingRate = when {
                batteryLevel < 20 -> 40000 // 25Hz when low battery
                batteryLevel < 50 -> 30000 // 33Hz when medium battery
                else -> 20000 // 50Hz when good battery
            }

            // Restart accelerometer with new rate if changed
            if (accelerometerManager.getCurrentSamplingRate() != newSamplingRate) {
                accelerometerManager.stopRecording()
                accelerometerManager.setSamplingRate(newSamplingRate)
                accelerometerManager.startRecording()

                updateNotification(
                    "Battery optimized: ${currentActivity.label} (${batteryLevel}%)"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Activity Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your physical activity in background"
                setShowBadge(false)
                setSound(null, null) // notification no sound
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ActivityTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("MET Activity Tracker")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = createNotification(contentText)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(notificationId, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        accelerometerManager.stopRecording()
        predictor.cleanup() // Clean up ML resources
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep running even if app is removed from recent apps
        // The service will continue until explicitly stopped
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }
}