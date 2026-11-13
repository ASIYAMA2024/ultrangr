package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.domain.DetectedDeviceType
import com.kugoo.dashboard.domain.UnifiedCockpitState
import com.kugoo.dashboard.ui.theme.StarkBorderGray
import com.kugoo.dashboard.ui.theme.StarkSurfaceDark
import com.kugoo.dashboard.ui.viewmodel.DashboardViewModel

@Composable
fun BleDevicesScreen(
    viewModel: DashboardViewModel,
    state: UnifiedCockpitState
) {
    val devices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SIMULTANEOUS DUAL BLE CONNECTION MANAGER", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(
                    "ND72450: ${state.farDriverState.connectionState} | JBD BMS: ${state.jbdState.connectionState}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.farDriverManager.disconnect() }) {
                    Text("DISC CTRL", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { viewModel.jbdManager.disconnect() }) {
                    Text("DISC BMS", fontSize = 11.sp)
                }
                Button(
                    onClick = { if (isScanning) viewModel.stopBleScan() else viewModel.startBleScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(if (isScanning) "STOP SCAN" else "SCAN BLE DEVICES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { dev ->
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dev.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (dev.detectedType != DetectedDeviceType.UNKNOWN) {
                                Text(
                                    text = dev.detectedType.name,
                                    color = accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text("${dev.address} • RSSI ${dev.rssi} dBm", color = Color.Gray, fontSize = 11.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.connectAsFarDriver(dev) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238))
                        ) {
                            Text("BIND ND72450", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.connectAsJbdBms(dev) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text("BIND JBD BMS", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
