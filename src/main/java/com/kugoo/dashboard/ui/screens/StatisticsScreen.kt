package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.data.local.RideSessionEntity
import com.kugoo.dashboard.domain.UnifiedCockpitState
import com.kugoo.dashboard.ui.theme.StarkBorderGray
import com.kugoo.dashboard.ui.theme.StarkSurfaceDark
import com.kugoo.dashboard.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatisticsScreen(
    viewModel: DashboardViewModel,
    state: UnifiedCockpitState
) {
    val rides by viewModel.rideHistoryFlow.collectAsState()
    val exportMsg by viewModel.lastExportMessage.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("CURRENT TRIP TELEMETRY", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatMetricBox("TRIP DIST", String.format(Locale.US, "%.2f km", state.tripDistanceKm))
                StatMetricBox("MAX SPEED", String.format(Locale.US, "%.1f km/h", state.maxSpeedKmh))
                StatMetricBox("AVG SPEED", String.format(Locale.US, "%.1f km/h", state.avgSpeedKmh))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatMetricBox("ENERGY USED", String.format(Locale.US, "%.1f Wh", state.wattHoursConsumed))
                StatMetricBox("DURATION", "${state.currentRideDurationSec / 60}m ${state.currentRideDurationSec % 60}s")
                StatMetricBox("TOTAL ODO", String.format(Locale.US, "%.1f km", state.totalOdometerKm))
            }

            Text("HISTORICAL MAX SPEED TREND", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(Color(0xFF09090C), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                val recent = rides.take(15).reversed()
                if (recent.size >= 2) {
                    val maxSpd = (recent.maxOf { it.maxSpeedKmh }).coerceAtLeast(10.0)
                    val stepX = size.width / (recent.size - 1).coerceAtLeast(1)
                    for (i in 0 until recent.size - 1) {
                        val y1 = size.height - (recent[i].maxSpeedKmh / maxSpd).toFloat() * size.height
                        val y2 = size.height - (recent[i + 1].maxSpeedKmh / maxSpd).toFloat() * size.height
                        drawLine(
                            color = accent,
                            start = Offset(i * stepX, y1),
                            end = Offset((i + 1) * stepX, y2),
                            strokeWidth = 4f
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LOCAL RIDE LOGS (${rides.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                exportMsg?.let {
                    Text(it.takeLast(38), color = accent, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (rides.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved rides yet. Connect BLE or ride with GPS to log automatically.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rides, key = { it.id }) { ride ->
                        RideHistoryRow(
                            ride = ride,
                            onExportCsv = { viewModel.exportRide(ride, asJson = false) },
                            onExportJson = { viewModel.exportRide(ride, asJson = true) },
                            onDelete = { viewModel.deleteRide(ride.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RideHistoryRow(
    ride: RideSessionEntity,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF16161C))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${sdf.format(Date(ride.startTimeEpochMs))} — ${String.format(Locale.US, "%.2f km", ride.distanceKm)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Max ${String.format(Locale.US, "%.1f km/h", ride.maxSpeedKmh)} • ${String.format(Locale.US, "%.0f Wh", ride.wattHoursUsed)}",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onExportCsv) { Text("CSV", fontSize = 11.sp) }
            TextButton(onClick = onExportJson) { Text("JSON", fontSize = 11.sp) }
            TextButton(onClick = onDelete) { Text("DEL", color = Color.Red, fontSize = 11.sp) }
        }
    }
}
