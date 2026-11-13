package com.kugoo.dashboard.domain

enum class DataSourceBadge(val label: String) {
    FARDRIVER("CTRL"),
    JBD_BMS("BMS"),
    GPS("GPS"),
    CALCULATED("CALC (V×I)"),
    DEMO("DEMO DATA"),
    UNAVAILABLE("N/A")
}

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class SourcedMetric<T>(
    val value: T? = null,
    val source: DataSourceBadge = DataSourceBadge.UNAVAILABLE,
    val timestampMs: Long = 0L
) {
    fun isStale(nowMs: Long, maxAgeMs: Long = 4000L): Boolean {
        return value != null && source != DataSourceBadge.DEMO && (nowMs - timestampMs) > maxAgeMs
    }
}

data class JbdTelemetryState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val totalVoltageV: Double? = null,
    val currentAmps: Double? = null,
    val remainingCapacityAh: Double? = null,
    val nominalCapacityAh: Double? = null,
    val cycleCount: Int? = null,
    val rsocPercent: Int? = null,
    val chargeMosEnabled: Boolean? = null,
    val dischargeMosEnabled: Boolean? = null,
    val cellCount: Int = 20,
    val cellVoltagesMv: List<Int> = emptyList(),
    val temperaturesCelsius: List<Double> = emptyList(),
    val protectionBitmask: Int = 0,
    val activeFaults: List<String> = emptyList(),
    val packetsReceived: Long = 0L,
    val crcErrors: Long = 0L,
    val lastUpdateMs: Long = 0L
)

data class FarDriverTelemetryState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val speedKmh: Double? = null,
    val rpm: Int? = null,
    val gearLevel: Int? = null,
    val driveMode: String = "N",
    val voltageV: Double? = null,
    val lineCurrentA: Double? = null,
    val phaseCurrentA: Double? = null,
    val motorTempC: Double? = null,
    val controllerTempC: Double? = null,
    val faultBitmask: Int = 0,
    val activeFaults: List<String> = emptyList(),
    val packetsReceived: Long = 0L,
    val crcErrors: Long = 0L,
    val lastUpdateMs: Long = 0L
)

data class UnifiedCockpitState(
    val isDemoMode: Boolean = false,
    val speedKmh: SourcedMetric<Double> = SourcedMetric(),
    val batteryPercent: SourcedMetric<Int> = SourcedMetric(),
    val batteryVoltageV: SourcedMetric<Double> = SourcedMetric(),
    val batteryCurrentA: SourcedMetric<Double> = SourcedMetric(),
    val powerWatts: SourcedMetric<Double> = SourcedMetric(),
    val motorTempC: SourcedMetric<Double> = SourcedMetric(),
    val controllerTempC: SourcedMetric<Double> = SourcedMetric(),
    val batteryTempC: SourcedMetric<Double> = SourcedMetric(),
    val gearDisplay: String = "N",
    val tripDistanceKm: Double = 0.0,
    val totalOdometerKm: Double = 0.0,
    val currentRideDurationSec: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val wattHoursConsumed: Double = 0.0,
    val jbdState: JbdTelemetryState = JbdTelemetryState(),
    val farDriverState: FarDriverTelemetryState = FarDriverTelemetryState(),
    val activeWarnings: List<DashboardWarning> = emptyList()
)

data class DashboardWarning(
    val id: String,
    val title: String,
    val detail: String,
    val isCritical: Boolean
)

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val detectedType: DetectedDeviceType
)

enum class DetectedDeviceType {
    FARDRIVER_CONTROLLER,
    JBD_BMS,
    UNKNOWN
}
