package com.kugoo.dashboard.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kugoo_cockpit_settings")

data class UserCockpitSettings(
    val accentColorHex: Long = 0xFFFF1E1E,
    val pureBlackOled: Boolean = true,
    val useMphUnits: Boolean = false,
    val demoModeEnabled: Boolean = true,
    val burnInProtectionShift: Boolean = true,
    val widgetScaleFactor: Float = 1.0f,
    val showLeftPowerColumn: Boolean = true,
    val showRightThermalColumn: Boolean = true,
    val lowBatteryThresholdPct: Int = 20,
    val highMotorTempWarningC: Double = 110.0,
    val highControllerTempWarningC: Double = 85.0,
    val highBatteryTempWarningC: Double = 55.0,
    val savedFarDriverMac: String? = null,
    val savedJbdBmsMac: String? = null,
    val totalOdometerKm: Double = 0.0
)

class SettingsDataStore(private val context: Context) {
    companion object {
        val ACCENT_COLOR = longPreferencesKey("accent_color_hex")
        val PURE_BLACK_OLED = booleanPreferencesKey("pure_black_oled")
        val USE_MPH = booleanPreferencesKey("use_mph")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val BURN_IN_SHIFT = booleanPreferencesKey("burn_in_shift")
        val WIDGET_SCALE = floatPreferencesKey("widget_scale")
        val SHOW_LEFT_COL = booleanPreferencesKey("show_left_col")
        val SHOW_RIGHT_COL = booleanPreferencesKey("show_right_col")
        val LOW_BATT_PCT = intPreferencesKey("low_batt_pct")
        val HIGH_MOTOR_TEMP = doublePreferencesKey("high_motor_temp")
        val HIGH_CTRL_TEMP = doublePreferencesKey("high_ctrl_temp")
        val HIGH_BATT_TEMP = doublePreferencesKey("high_batt_temp")
        val SAVED_FARDRIVER_MAC = stringPreferencesKey("saved_fardriver_mac")
        val SAVED_JBD_MAC = stringPreferencesKey("saved_jbd_mac")
        val TOTAL_ODOMETER_KM = doublePreferencesKey("total_odometer_km")
    }

    val settingsFlow: Flow<UserCockpitSettings> = context.dataStore.data.map { prefs ->
        UserCockpitSettings(
            accentColorHex = prefs[ACCENT_COLOR] ?: 0xFFFF1E1E,
            pureBlackOled = prefs[PURE_BLACK_OLED] ?: true,
            useMphUnits = prefs[USE_MPH] ?: false,
            demoModeEnabled = prefs[DEMO_MODE] ?: true,
            burnInProtectionShift = prefs[BURN_IN_SHIFT] ?: true,
            widgetScaleFactor = prefs[WIDGET_SCALE] ?: 1.0f,
            showLeftPowerColumn = prefs[SHOW_LEFT_COL] ?: true,
            showRightThermalColumn = prefs[SHOW_RIGHT_COL] ?: true,
            lowBatteryThresholdPct = prefs[LOW_BATT_PCT] ?: 20,
            highMotorTempWarningC = prefs[HIGH_MOTOR_TEMP] ?: 110.0,
            highControllerTempWarningC = prefs[HIGH_CTRL_TEMP] ?: 85.0,
            highBatteryTempWarningC = prefs[HIGH_BATT_TEMP] ?: 55.0,
            savedFarDriverMac = prefs[SAVED_FARDRIVER_MAC],
            savedJbdBmsMac = prefs[SAVED_JBD_MAC],
            totalOdometerKm = prefs[TOTAL_ODOMETER_KM] ?: 0.0
        )
    }

    suspend fun updateAccentColor(hex: Long) {
        context.dataStore.edit { it[ACCENT_COLOR] = hex }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[DEMO_MODE] = enabled }
    }

    suspend fun setUseMph(useMph: Boolean) {
        context.dataStore.edit { it[USE_MPH] = useMph }
    }

    suspend fun setPureBlack(pureBlack: Boolean) {
        context.dataStore.edit { it[PURE_BLACK_OLED] = pureBlack }
    }

    suspend fun setWidgetScale(scale: Float) {
        context.dataStore.edit { it[WIDGET_SCALE] = scale }
    }

    suspend fun setWidgetVisibility(leftPower: Boolean, rightThermal: Boolean) {
        context.dataStore.edit {
            it[SHOW_LEFT_COL] = leftPower
            it[SHOW_RIGHT_COL] = rightThermal
        }
    }

    suspend fun updateThresholds(lowBatt: Int, motorC: Double, ctrlC: Double, battC: Double) {
        context.dataStore.edit {
            it[LOW_BATT_PCT] = lowBatt
            it[HIGH_MOTOR_TEMP] = motorC
            it[HIGH_CTRL_TEMP] = ctrlC
            it[HIGH_BATT_TEMP] = battC
        }
    }

    suspend fun saveDeviceAddress(isFarDriver: Boolean, address: String) {
        context.dataStore.edit {
            if (isFarDriver) it[SAVED_FARDRIVER_MAC] = address
            else it[SAVED_JBD_MAC] = address
        }
    }

    suspend fun incrementOdometer(deltaKm: Double) {
        if (deltaKm <= 0.0) return
        context.dataStore.edit { prefs ->
            val current = prefs[TOTAL_ODOMETER_KM] ?: 0.0
            prefs[TOTAL_ODOMETER_KM] = current + deltaKm
        }
    }
}
