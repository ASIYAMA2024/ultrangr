package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.domain.UnifiedCockpitState
import com.kugoo.dashboard.ui.theme.StarkBorderGray
import com.kugoo.dashboard.ui.theme.StarkSurfaceDark
import java.util.Locale

@Composable
fun BatteryDetailScreen(state: UnifiedCockpitState) {
    val jbd = state.jbdState
    val accent = MaterialTheme.colorScheme.primary
    val cells = jbd.cellVoltagesMv
    val minCell = cells.minOrNull()
    val maxCell = cells.maxOrNull()
    val deltaMv = if (minCell != null && maxCell != null) maxCell - minCell else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(StarkSurfaceDark)
            .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BattHeaderStat("PACK VOLTAGE", state.batteryVoltageV.value?.let { String.format(Locale.US, "%.2f V", it) } ?: "--")
            BattHeaderStat("CURRENT", state.batteryCurrentA.value?.let { String.format(Locale.US, "%.1f A", it) } ?: "--")
            BattHeaderStat("CAPACITY", if (jbd.remainingCapacityAh != null && jbd.nominalCapacityAh != null) "${jbd.remainingCapacityAh} / ${jbd.nominalCapacityAh} Ah" else "N/A")
            BattHeaderStat("CYCLES", jbd.cycleCount?.toString() ?: "N/A")
            BattHeaderStat("CELL DELTA", deltaMv?.let { "$it mV" } ?: "N/A")
            BattHeaderStat(
                "NTC TEMPS",
                if (jbd.temperaturesCelsius.isNotEmpty()) jbd.temperaturesCelsius.joinToString(" / ") { "${it.toInt()}°C" } else "N/A"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "20S LITHIUM-ION CELL VOLTAGES (CMD 0x04)",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (cells.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Awaiting JBD BMS Command 0x04 cell telemetry response...",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(cells) { idx, mv ->
                    val isHighest = mv == maxCell
                    val isLowest = mv == minCell
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF16161C))
                            .border(
                                1.dp,
                                when {
                                    isHighest -> accent
                                    isLowest -> Color(0xFF29B6F6)
                                    else -> StarkBorderGray
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text("CELL ${idx + 1}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            String.format(Locale.US, "%.3f V", mv / 1000.0),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BattHeaderStat(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
