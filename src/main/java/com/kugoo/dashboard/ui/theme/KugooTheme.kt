package com.kugoo.dashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KugooRed = Color(0xFFFF1E1E)
val StarkSurfaceDark = Color(0xFF0E0E11)
val StarkBorderGray = Color(0xFF232329)
val TelemetryAmber = Color(0xFFFFB300)
val TelemetryGreen = Color(0xFF00E676)

@Composable
fun KugooCockpitTheme(
    accentColorHex: Long = 0xFFFF1E1E,
    pureBlackOled: Boolean = true,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColorHex)
    val bg = if (pureBlackOled) Color.Black else Color(0xFF0B0C0E)

    val scheme = darkColorScheme(
        primary = accent,
        secondary = accent,
        background = bg,
        surface = StarkSurfaceDark,
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
