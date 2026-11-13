package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.data.local.UserCockpitSettings
import com.kugoo.dashboard.domain.SourcedMetric
import com.kugoo.dashboard.domain.UnifiedCockpitState
import com.kugoo.dashboard.ui.theme.StarkBorderGray
import com.kugoo.dashboard.ui.theme.StarkSurfaceDark
import com.kugoo.dashboard.ui.theme.TelemetryAmber
import com.kugoo.dashboard.ui.theme.TelemetryGreen
import java.util.Locale
import kotlin.math.abs

@Composable
fun RideCockpitScreen(
    state: UnifiedCockpitState,
    settings: UserCockpitSettings
) {
    val now = System.currentTimeMillis()
    val accent = MaterialTheme.colorScheme.primary
    val unitSpeedLabel = if (settings.useMphUnits) "MPH" else "KM/H"
    val unitFactor = if (settings.useMphUnits) 0.621371 else 1.0

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.showLeftPowerColumn) {
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    title = "BATTERY SOC",
                    valueText = state.batteryPercent.value?.let { "$it%" } ?: "--",
                    subText = state.batteryVoltageV.value?.let {
                        String.format(Locale.US, "%.1f V", it)
                    } ?: "VOLTAGE N/A",
                    metric = state.batteryPercent,
                    nowMs = now,
                    accentColor = accent
                )
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    title = "POWER OUTPUT",
                    valueText = state.powerWatts.value?.let { pwr ->
                        if (abs(pwr) >= 1000.0) String.format(Locale.US, "%.2f kW", pwr / 1000.0)
                        else String.format(Locale.US, "%.0f W", pwr)
                    } ?: "--",
                    subText = state.batteryCurrentA.value?.let {
                        String.format(Locale.US, "%.1f A", it)
                    } ?: "CURRENT N/A",
                    metric = state.powerWatts,
                    nowMs = now,
                    accentColor = accent
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(16.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val powerKw = (state.powerWatts.value ?: 0.0) / 1000.0
            val normalizedPower = (powerKw / 14.0).coerceIn(-0.3, 1.0).toFloat()

            Canvas(modifier = Modifier.size(230.dp)) {
                val strokeWidth = 12.dp.toPx()
                drawArc(
                    color = Color(0xFF1E1E24),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                val sweep = 270f * abs(normalizedPower)
                drawArc(
                    color = if (normalizedPower < 0f) TelemetryGreen else accent,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "MODE ${state.gearDisplay}",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val displaySpeed = state.speedKmh.value?.let { (it * unitFactor).toInt().toString() } ?: "--"
                Text(
                    text = displaySpeed,
                    color = if (state.speedKmh.isStale(now)) TelemetryAmber else Color.White,
                    fontSize = (92 * settings.widgetScaleFactor).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = unitSpeedLabel,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceTag(
                        label = state.speedKmh.source.label,
                        isStale = state.speedKmh.isStale(now)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TRIP", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.US, "%.2f km", state.tripDistanceKm * unitFactor),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ODO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.US, "%.1f km", state.totalOdometerKm * unitFactor),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (settings.showRightThermalColumn) {
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    title = "MOTOR NTC 10K",
                    valueText = state.motorTempC.value?.let {
                        String.format(Locale.US, "%.0f°C", it)
                    } ?: "--",
                    subText = "SOTION FW23 8kW",
                    metric = state.motorTempC,
                    nowMs = now,
                    accentColor = accent
                )
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    title = "CONTROLLER / BMS",
                    valueText = state.controllerTempC.value?.let {
                        String.format(Locale.US, "%.0f°C", it)
                    } ?: "--",
                    subText = state.batteryTempC.value?.let {
                        String.format(Locale.US, "BATT: %.1f°C", it)
                    } ?: "BATT TEMP N/A",
                    metric = state.controllerTempC,
                    nowMs = now,
                    accentColor = accent
                )
            }
        }
    }
}

@Composable
private fun <T> TelemetryCard(
    modifier: Modifier = Modifier,
    title: String,
    valueText: String,
    subText: String,
    metric: SourcedMetric<T>,
    nowMs: Long,
    accentColor: Color
) {
    val stale = metric.isStale(nowMs)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StarkSurfaceDark)
            .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            SourceTag(label = metric.source.label, isStale = stale)
        }

        Text(
            text = valueText,
            color = if (stale) TelemetryAmber else Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = subText,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SourceTag(label: String, isStale: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isStale) TelemetryAmber.copy(alpha = 0.25f) else Color(0xFF232329))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isStale) "$label (STALE)" else label,
            color = if (isStale) TelemetryAmber else Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
