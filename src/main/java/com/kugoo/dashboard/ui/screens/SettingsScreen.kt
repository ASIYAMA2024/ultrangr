package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.data.local.UserCockpitSettings
import com.kugoo.dashboard.ui.theme.StarkBorderGray
import com.kugoo.dashboard.ui.theme.StarkSurfaceDark
import com.kugoo.dashboard.ui.viewmodel.DashboardViewModel

@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    settings: UserCockpitSettings
) {
    val accentChoices = listOf(
        0xFFFF1E1E to "Kugoo Red",
        0xFFFF6D00 to "Stark Orange",
        0xFF00E676 to "Volt Green",
        0xFF00B0FF to "Cyber Blue",
        0xFFFFFFFF to "Nothing White"
    )

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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("THEME & WIDGET EDITOR", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Black)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accent Colour", color = Color.White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accentChoices.forEach { (hex, _) ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (settings.accentColorHex == hex) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setAccentColor(hex) }
                        )
                    }
                }
            }

            SettingToggleRow("Pure Black OLED Background", settings.pureBlackOled) {
                viewModel.setPureBlack(it)
            }
            SettingToggleRow("Show Left Power Column", settings.showLeftPowerColumn) {
                viewModel.setWidgetVisibility(it, settings.showRightThermalColumn)
            }
            SettingToggleRow("Show Right Thermal Column", settings.showRightThermalColumn) {
                viewModel.setWidgetVisibility(settings.showLeftPowerColumn, it)
            }
            SettingToggleRow("Use Imperial Units (MPH)", settings.useMphUnits) {
                viewModel.setUseMph(it)
            }
            SettingToggleRow("Demo Telemetry Mode (When BLE Disconnected)", settings.demoModeEnabled) {
                viewModel.setDemoMode(it)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(StarkSurfaceDark)
                .border(1.dp, StarkBorderGray, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WARNING THRESHOLDS (USER CONFIGURABLE)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Black)

            Column {
                Text("Speedometer Font Scale: ${String.format("%.2fx", settings.widgetScaleFactor)}", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = settings.widgetScaleFactor,
                    onValueChange = { viewModel.setWidgetScale(it) },
                    valueRange = 0.8f..1.25f
                )
            }

            Column {
                Text("Low Battery Alert: ${settings.lowBatteryThresholdPct}%", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = settings.lowBatteryThresholdPct.toFloat(),
                    onValueChange = {
                        viewModel.updateWarningThresholds(
                            it.toInt(),
                            settings.highMotorTempWarningC,
                            settings.highControllerTempWarningC,
                            settings.highBatteryTempWarningC
                        )
                    },
                    valueRange = 5f..40f
                )
            }

            Column {
                Text("Motor NTC Warning Limit: ${settings.highMotorTempWarningC.toInt()}°C", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = settings.highMotorTempWarningC.toFloat(),
                    onValueChange = {
                        viewModel.updateWarningThresholds(
                            settings.lowBatteryThresholdPct,
                            it.toDouble(),
                            settings.highControllerTempWarningC,
                            settings.highBatteryTempWarningC
                        )
                    },
                    valueRange = 70f..150f
                )
            }

            Column {
                Text("Controller Temp Warning Limit: ${settings.highControllerTempWarningC.toInt()}°C", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = settings.highControllerTempWarningC.toFloat(),
                    onValueChange = {
                        viewModel.updateWarningThresholds(
                            settings.lowBatteryThresholdPct,
                            settings.highMotorTempWarningC,
                            it.toDouble(),
                            settings.highBatteryTempWarningC
                        )
                    },
                    valueRange = 55f..105f
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
