package com.kugoo.dashboard.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun StartupAnimationScreen(
    onStartupFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
        delay(150L)
        onStartupFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "K U G O O",
                color = Color.White.copy(alpha = progress.value),
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "WISH 01 // ND72450 INSTRUMENT SYSTEM",
                color = accent.copy(alpha = progress.value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Canvas(
                modifier = Modifier
                    .width(320.dp)
                    .height(4.dp)
            ) {
                drawLine(
                    color = Color(0xFF222222),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width * progress.value, size.height / 2),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (progress.value < 0.6f) "INITIALISING DUAL BLE GATT ENGINE..."
                else "COCKPIT READY",
                color = Color.Gray,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
