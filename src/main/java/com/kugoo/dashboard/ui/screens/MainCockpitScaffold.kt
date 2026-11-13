package com.kugoo.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kugoo.dashboard.domain.BleConnectionState
import com.kugoo.dashboard.ui.theme.TelemetryAmber
import com.kugoo.dashboard.ui.theme.TelemetryGreen
import com.kugoo.dashboard.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainCockpitScaffold(viewModel: DashboardViewModel) {
    val state by viewModel.cockpitState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val pages = listOf("RIDE", "STATISTICS", "DIAGNOSTICS", "BATTERY", "BLUETOOTH", "SETTINGS")
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    var pixelShiftX by remember { mutableStateOf(0) }
    var pixelShiftY by remember { mutableStateOf(0) }
    LaunchedEffect(settings.burnInProtectionShift) {
        while (settings.burnInProtectionShift) {
            delay(60_000L)
            pixelShiftX = (-2..2).random()
            pixelShiftY = (-2..2).random()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .offset { IntOffset(pixelShiftX, pixelShiftY) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "KUGOO",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (state.isDemoMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TelemetryAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DEMO DATA",
                            color = TelemetryAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEachIndexed { idx, title ->
                    val selected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color(0xFF16161B)
                            )
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (selected) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BleStatusPill("ND72450", state.farDriverState.connectionState)
                BleStatusPill("JBD BMS", state.jbdState.connectionState)
            }
        }

        if (state.activeWarnings.isNotEmpty()) {
            val topWarn = state.activeWarnings.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (topWarn.isCritical) Color(0xFFB71C1C) else Color(0xFF4E342E)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠ ${topWarn.title} — ${topWarn.detail}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (state.activeWarnings.size > 1) {
                    Text(
                        text = "+${state.activeWarnings.size - 1} MORE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp)
        ) { pageIndex ->
            when (pageIndex) {
                0 -> RideCockpitScreen(state = state, settings = settings)
                1 -> StatisticsScreen(viewModel = viewModel, state = state)
                2 -> DiagnosticsScreen(state = state)
                3 -> BatteryDetailScreen(state = state)
                4 -> BleDevicesScreen(viewModel = viewModel, state = state)
                5 -> SettingsScreen(viewModel = viewModel, settings = settings)
            }
        }
    }
}

@Composable
private fun BleStatusPill(label: String, connectionState: BleConnectionState) {
    val dotColor = when (connectionState) {
        BleConnectionState.CONNECTED -> TelemetryGreen
        BleConnectionState.CONNECTING, BleConnectionState.RECONNECTING, BleConnectionState.SCANNING -> TelemetryAmber
        BleConnectionState.ERROR -> Color.Red
        BleConnectionState.DISCONNECTED -> Color.DarkGray
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
