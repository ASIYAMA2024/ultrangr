package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.kugoo.dashboard.ui.theme.TelemetryGreen

@Composable
fun DiagnosticsScreen(state: UnifiedCockpitState) {
    val fd = state.farDriverState
    val jbd = state.jbdState
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("FARDRIVER ND72450 DIAGNOSTICS", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
            DiagRow("BLE Status", fd.connectionState.name)
            DiagRow("Access Mode", "STRICT READ-ONLY (SAFE)")
            DiagRow("Device MAC", fd.deviceAddress ?: "Not Paired")
            DiagRow("Valid Packets RX", fd.packetsReceived.toString())
            DiagRow("CRC / Frame Errors", fd.crcErrors.toString())
            DiagRow("Sotion FW23 Hall RPM", fd.rpm?.toString() ?: "UNAVAILABLE")
            DiagRow("Motor NTC 10K Sensor", fd.motorTempC?.let { "$it °C" } ?: "UNAVAILABLE")
            DiagRow("MOSFET Bridge Temp", fd.controllerTempC?.let { "$it °C" } ?: "UNAVAILABLE")
            DiagRow(
                "Active Controller Faults",
                if (fd.activeFaults.isEmpty()) "NONE (0x0000)" else fd.activeFaults.joinToString()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("JBD BMS (20S 72V) DIAGNOSTICS", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
            DiagRow("BLE Status", jbd.connectionState.name)
            DiagRow("GATT Service", "0000ff00-0000-1000-8000-00805f9b34fb")
            DiagRow("Device MAC", jbd.deviceAddress ?: "Not Paired")
            DiagRow("Valid Packets RX", jbd.packetsReceived.toString())
            DiagRow("Checksum Errors", jbd.crcErrors.toString())
            DiagRow("Charge / Discharge MOS", "CHG: ${jbd.chargeMosEnabled ?: "--"} | DSG: ${jbd.dischargeMosEnabled ?: "--"}")
            DiagRow("Cell Strings Detected", "${jbd.cellVoltagesMv.size} / ${jbd.cellCount}S")
            DiagRow("NTC Probes Detected", jbd.temperaturesCelsius.size.toString())
            DiagRow(
                "Protection Bitmask",
                if (jbd.activeFaults.isEmpty()) "OK (0x0000)" else jbd.activeFaults.joinToString()
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(
            text = value,
            color = if (value.contains("NONE") || value.contains("OK") || value.contains("CONNECTED")) TelemetryGreen else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
