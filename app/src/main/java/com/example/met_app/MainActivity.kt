package com.example.met_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val predictor = MLPredictor()
    private lateinit var accelerometerManager: AccelerometerManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, can start recording
        } else {
            // Handle permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        predictor.init(this) // load model once
        accelerometerManager = AccelerometerManager(this)

        // Request sensor permission if not already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }

        setContent {
            val vm: MainViewModel = viewModel(
                factory = MainViewModelFactory(predictor, accelerometerManager)
            )

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(vm = vm)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val isRecording by vm.isRecording.collectAsState()

    val colors = remember {
        mapOf(
            MetClass.SEDENTARY to Color(0xFF8E8E93),
            MetClass.LIGHT to Color(0xFF34C759),
            MetClass.MODERATE to Color(0xFFFF9500),
            MetClass.VIGOROUS to Color(0xFFFF3B30)
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start/Stop Recording FAB
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            vm.stopRecording()
                        } else {
                            vm.startRecording()
                        }
                    },
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                    )
                }

                // Reset FAB
                ExtendedFloatingActionButton(
                    onClick = { vm.resetToday() },
                    text = { Text("Reset Today") },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = "Reset") }
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with recording status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MET Dashboard",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                RecordingStatusChip(isRecording = isRecording)
            }

            // Current class chip
            CurrentClassChip(
                current = state.currentClass,
                color = colors[state.currentClass] ?: MaterialTheme.colorScheme.primary
            )

            // Distribution bar
            DistributionBar(
                durations = state.durationsSec,
                colorMap = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
            )

            // 2x2 cards grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DashboardCard(
                        title = MetClass.SEDENTARY.label,
                        value = formatHms(state.durationsSec[MetClass.SEDENTARY] ?: 0L),
                        accent = colors[MetClass.SEDENTARY]!!
                    )
                    DashboardCard(
                        title = MetClass.LIGHT.label,
                        value = formatHms(state.durationsSec[MetClass.LIGHT] ?: 0L),
                        accent = colors[MetClass.LIGHT]!!
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DashboardCard(
                        title = MetClass.MODERATE.label,
                        value = formatHms(state.durationsSec[MetClass.MODERATE] ?: 0L),
                        accent = colors[MetClass.MODERATE]!!
                    )
                    DashboardCard(
                        title = MetClass.VIGOROUS.label,
                        value = formatHms(state.durationsSec[MetClass.VIGOROUS] ?: 0L),
                        accent = colors[MetClass.VIGOROUS]!!
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = if (isRecording)
                    "Recording accelerometer data • Real-time predictions"
                else
                    "Tap play button to start recording • Accelerometer ready",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
        }
    }
}

@Composable
fun RecordingStatusChip(isRecording: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha"
        )

    Surface(
        color = if (isRecording)
            Color(0xFFFF3B30).copy(alpha = 0.12f)
        else
            Color(0xFF8E8E93).copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF3B30), CircleShape)
                        .alpha(pulse.value)
                )
            }
            Text(
                text = if (isRecording) "Recording" else "Stopped",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRecording) Color(0xFFFF3B30) else Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun CurrentClassChip(current: MetClass, color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha"
        )

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
                    .alpha(pulse.value)
            )
            Text("Current: ${current.label}", fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, accent: Color) {
    ElevatedCard(
        modifier = Modifier
            .heightIn(min = 120.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(color = accent))
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun DistributionBar(
    durations: Map<MetClass, Long>,
    colorMap: Map<MetClass, Color>,
    modifier: Modifier = Modifier
) {
    val total = durations.values.sum().coerceAtLeast(1L)
    Row(modifier = modifier.background(Color(0x11000000), RoundedCornerShape(9.dp))) {
        MetClass.values().forEach { mc ->
            val part = (durations[mc] ?: 0L).toFloat() / total.toFloat()
            if (part > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(part)
                        .background((colorMap[mc] ?: Color.Gray).copy(alpha = 0.85f))
                )
            }
        }
    }
}