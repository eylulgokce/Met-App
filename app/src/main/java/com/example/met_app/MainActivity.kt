package com.example.met_app

import kotlinx.coroutines.flow.collect
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val predictor = MLPredictor()
    private lateinit var accelerometerManager: AccelerometerManager
    private lateinit var database: AppDatabase

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bodySourcesGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false
        if (bodySourcesGranted) {
            ActivityTrackingService.startTracking(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        predictor.init(this)
        accelerometerManager = AccelerometerManager(this)
        database = AppDatabase.getDatabase(this)

        requestPermissions()

        setContent {
            val vm: MainViewModel = viewModel(
                factory = MainViewModelFactory(predictor, accelerometerManager, database)
            )
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(vm = vm)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            ActivityTrackingService.startTracking(this)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DashboardScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val isTracking by vm.isTracking.collectAsState()
    val dailySummary by vm.dailySummary.collectAsState()
    val weeklySummary by vm.weeklySummary.collectAsState()
    val showDailyExpanded by vm.showDailyExpanded.collectAsState()
    val showWeeklyExpanded by vm.showWeeklyExpanded.collectAsState()
    val context = LocalContext.current

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
                FloatingActionButton(
                    onClick = {
                        if (isTracking) {
                            vm.stopTracking()
                            ActivityTrackingService.stopTracking(context)
                        } else {
                            vm.startTracking()
                            ActivityTrackingService.startTracking(context)
                        }
                    },
                    containerColor = if (isTracking) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking"
                    )
                }
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
                TrackingStatusChip(isTracking = isTracking)
            }

            CurrentClassChip(
                current = state.currentClass,
                color = colors[state.currentClass] ?: MaterialTheme.colorScheme.primary
            )

            DistributionBar(
                durations = state.durationsSec,
                colorMap = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DashboardCard(MetClass.SEDENTARY.label, formatHms(state.durationsSec[MetClass.SEDENTARY] ?: 0L), colors[MetClass.SEDENTARY]!!)
                    DashboardCard(MetClass.LIGHT.label, formatHms(state.durationsSec[MetClass.LIGHT] ?: 0L), colors[MetClass.LIGHT]!!)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DashboardCard(MetClass.MODERATE.label, formatHms(state.durationsSec[MetClass.MODERATE] ?: 0L), colors[MetClass.MODERATE]!!)
                    DashboardCard(MetClass.VIGOROUS.label, formatHms(state.durationsSec[MetClass.VIGOROUS] ?: 0L), colors[MetClass.VIGOROUS]!!)
                }
            }

            DailySummarySection(dailySummary, colors, showDailyExpanded, vm::toggleDailyExpanded)
            WeeklyHistorySection(weeklySummary, colors, showWeeklyExpanded, vm::toggleWeeklyExpanded)

            Spacer(Modifier.weight(1f))
            Text(
                text = if (isTracking) "Background tracking active • Data saved every minute"
                else "Tap play to start background tracking",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
        }
    }
}

@Composable
fun TrackingStatusChip(isTracking: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")

    Surface(
        color = (if (isTracking) Color(0xFF34C759) else Color(0xFF8E8E93)).copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isTracking) Color(0xFF34C759) else Color(0xFF8E8E93), CircleShape)
                    .alpha(pulse.value)
            )
            Text(if (isTracking) "Tracking" else "Stopped",
                style = MaterialTheme.typography.labelMedium,
                color = if (isTracking) Color(0xFF34C759) else Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun CurrentClassChip(current: MetClass, color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(12.dp).background(color, CircleShape).alpha(pulse.value))
            Text("Current: ${current.label}", fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, accent: Color) {
    ElevatedCard(
        modifier = Modifier.heightIn(min = 120.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(color = accent))
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun DistributionBar(durations: Map<MetClass, Long>, colorMap: Map<MetClass, Color>, modifier: Modifier = Modifier) {
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

@Composable
fun DailySummarySection(dailySummary: DailySummary?, colors: Map<MetClass, Color>, isExpanded: Boolean, onToggleExpanded: () -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                if (dailySummary == null) {
                    Text("No data yet.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        SummaryRow(MetClass.SEDENTARY, dailySummary.sedentaryMinutes, colors)
                        SummaryRow(MetClass.LIGHT, dailySummary.lightMinutes, colors)
                        SummaryRow(MetClass.MODERATE, dailySummary.moderateMinutes, colors)
                        SummaryRow(MetClass.VIGOROUS, dailySummary.vigorousMinutes, colors)
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                        Text("Total: ${formatHms(dailySummary.totalMinutes * 60L)}", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(metClass: MetClass, minutes: Int, colors: Map<MetClass, Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(metClass.label, color = colors[metClass] ?: Color.Unspecified)
        Text(formatHms(minutes * 60L))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WeeklyHistorySection(weeklySummary: List<DailySummary>, colors: Map<MetClass, Color>, isExpanded: Boolean, onToggleExpanded: () -> Unit) {
    val df = remember { DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault()) }
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onToggleExpanded() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (weeklySummary.isEmpty()) {
                        Text("No history yet.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
                    } else {
                        weeklySummary.forEach { day ->
                            Column {
                                Text(day.date.format(df), fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                SummaryRow(MetClass.SEDENTARY, day.sedentaryMinutes, colors)
                                SummaryRow(MetClass.LIGHT, day.lightMinutes, colors)
                                SummaryRow(MetClass.MODERATE, day.moderateMinutes, colors)
                                SummaryRow(MetClass.VIGOROUS, day.vigorousMinutes, colors)
                                HorizontalDivider(
                                    Modifier.padding(vertical = 8.dp),
                                    DividerDefaults.Thickness,
                                    DividerDefaults.color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
