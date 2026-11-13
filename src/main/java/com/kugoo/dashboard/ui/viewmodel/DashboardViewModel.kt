package com.kugoo.dashboard.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kugoo.dashboard.ble.FarDriverBleManager
import com.kugoo.dashboard.ble.JbdBmsBleManager
import com.kugoo.dashboard.data.export.RideExportManager
import com.kugoo.dashboard.data.local.*
import com.kugoo.dashboard.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.sin

@SuppressLint("MissingPermission")
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore(application)
    private val database = RideDatabase.getInstance(application)
    private val rideDao = database.rideDao()

    val jbdManager = JbdBmsBleManager(application, viewModelScope)
    val farDriverManager = FarDriverBleManager(application, viewModelScope)

    val settingsState: StateFlow<UserCockpitSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserCockpitSettings())

    val rideHistoryFlow: StateFlow<List<RideSessionEntity>> = rideDao.observeAllRides()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _cockpitState = MutableStateFlow(UnifiedCockpitState())
    val cockpitState: StateFlow<UnifiedCockpitState> = _cockpitState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBleDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastExportMessage = MutableStateFlow<String?>(null)
    val lastExportMessage: StateFlow<String?> = _lastExportMessage.asStateFlow()

    private var gpsSpeedKmh: Double? = null
    private var gpsSpeedTimestampMs: Long = 0L
    private var lastGpsLocation: Location? = null

    private var activeRideId: Long? = null
    private var rideStartEpochMs: Long = System.currentTimeMillis()
    private var tripDistanceKm: Double = 0.0
    private var maxSpeedRecorded: Double = 0.0
    private var maxPowerRecorded: Double = 0.0
    private var wattHoursAccumulated: Double = 0.0
    private var demoTick: Double = 0.0

    init {
        startTelemetryFusionLoop()
        startRideRecordingLoop()
        initGpsListener()
    }

    private fun startTelemetryFusionLoop() {
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val cfg = settingsState.value
                val jbd = jbdManager.state.value
                val fd = farDriverManager.state.value

                val anyRealConnected = jbd.connectionState == BleConnectionState.CONNECTED ||
                        fd.connectionState == BleConnectionState.CONNECTED

                if (cfg.demoModeEnabled && !anyRealConnected) {
                    demoTick += 0.15
                    val simSpeed = 42.0 + 28.0 * sin(demoTick * 0.5)
                    val simVolt = 79.4 - 2.1 * sin(demoTick * 0.5)
                    val simAmps = 45.0 + 65.0 * sin(demoTick * 0.5).coerceAtLeast(-15.0)
                    val simPower = simVolt * simAmps
                    val simSoc = 84
                    val simMotorTemp = 64.5 + 4.0 * sin(demoTick * 0.2)
                    val simCtrlTemp = 48.2 + 2.5 * sin(demoTick * 0.2)
                    val simBattTemp = 31.4

                    _cockpitState.update {
                        it.copy(
                            isDemoMode = true,
                            speedKmh = SourcedMetric(simSpeed, DataSourceBadge.DEMO, now),
                            batteryPercent = SourcedMetric(simSoc, DataSourceBadge.DEMO, now),
                            batteryVoltageV = SourcedMetric(simVolt, DataSourceBadge.DEMO, now),
                            batteryCurrentA = SourcedMetric(simAmps, DataSourceBadge.DEMO, now),
                            powerWatts = SourcedMetric(simPower, DataSourceBadge.DEMO, now),
                            motorTempC = SourcedMetric(simMotorTemp, DataSourceBadge.DEMO, now),
                            controllerTempC = SourcedMetric(simCtrlTemp, DataSourceBadge.DEMO, now),
                            batteryTempC = SourcedMetric(simBattTemp, DataSourceBadge.DEMO, now),
                            gearDisplay = "D3",
                            totalOdometerKm = cfg.totalOdometerKm,
                            jbdState = jbd,
                            farDriverState = fd,
                            activeWarnings = evaluateWarnings(
                                soc = simSoc,
                                motorTemp = simMotorTemp,
                                ctrlTemp = simCtrlTemp,
                                battTemp = simBattTemp,
                                jbd = jbd,
                                fd = fd,
                                cfg = cfg,
                                isDemo = true
                            )
                        )
                    }
                } else {
                    val speedMetric = when {
                        fd.speedKmh != null && (now - fd.lastUpdateMs) < 5000L ->
                            SourcedMetric(fd.speedKmh, DataSourceBadge.FARDRIVER, fd.lastUpdateMs)
                        gpsSpeedKmh != null && (now - gpsSpeedTimestampMs) < 5000L ->
                            SourcedMetric(gpsSpeedKmh, DataSourceBadge.GPS, gpsSpeedTimestampMs)
                        else -> SourcedMetric()
                    }

                    val voltageMetric = when {
                        jbd.totalVoltageV != null ->
                            SourcedMetric(jbd.totalVoltageV, DataSourceBadge.JBD_BMS, jbd.lastUpdateMs)
                        fd.voltageV != null ->
                            SourcedMetric(fd.voltageV, DataSourceBadge.FARDRIVER, fd.lastUpdateMs)
                        else -> SourcedMetric()
                    }

                    val currentMetric = when {
                        jbd.currentAmps != null ->
                            SourcedMetric(-jbd.currentAmps, DataSourceBadge.JBD_BMS, jbd.lastUpdateMs)
                        fd.lineCurrentA != null ->
                            SourcedMetric(fd.lineCurrentA, DataSourceBadge.FARDRIVER, fd.lastUpdateMs)
                        else -> SourcedMetric()
                    }

                    val powerMetric = if (voltageMetric.value != null && currentMetric.value != null) {
                        SourcedMetric(
                            voltageMetric.value * currentMetric.value,
                            DataSourceBadge.CALCULATED,
                            maxOf(voltageMetric.timestampMs, currentMetric.timestampMs)
                        )
                    } else {
                        SourcedMetric()
                    }

                    val socMetric = if (jbd.rsocPercent != null) {
                        SourcedMetric(jbd.rsocPercent, DataSourceBadge.JBD_BMS, jbd.lastUpdateMs)
                    } else {
                        SourcedMetric()
                    }

                    val motorTempMetric = if (fd.motorTempC != null) {
                        SourcedMetric(fd.motorTempC, DataSourceBadge.FARDRIVER, fd.lastUpdateMs)
                    } else {
                        SourcedMetric()
                    }

                    val ctrlTempMetric = if (fd.controllerTempC != null) {
                        SourcedMetric(fd.controllerTempC, DataSourceBadge.FARDRIVER, fd.lastUpdateMs)
                    } else {
                        SourcedMetric()
                    }

                    val maxBattTemp = jbd.temperaturesCelsius.maxOrNull()
                    val battTempMetric = if (maxBattTemp != null) {
                        SourcedMetric(maxBattTemp, DataSourceBadge.JBD_BMS, jbd.lastUpdateMs)
                    } else {
                        SourcedMetric()
                    }

                    val spd = speedMetric.value ?: 0.0
                    if (spd > 0.5) {
                        val deltaKm = spd * (0.2 / 3600.0)
                        tripDistanceKm += deltaKm
                        settingsStore.incrementOdometer(deltaKm)
                    }
                    if (spd > maxSpeedRecorded) maxSpeedRecorded = spd

                    val pwr = powerMetric.value ?: 0.0
                    if (pwr > maxPowerRecorded) maxPowerRecorded = pwr
                    wattHoursAccumulated += abs(pwr) * (0.2 / 3600.0)

                    val durationSec = ((now - rideStartEpochMs) / 1000L).coerceAtLeast(1L)
                    val avgSpeed = if (durationSec > 5) (tripDistanceKm / (durationSec / 3600.0)) else 0.0

                    _cockpitState.update {
                        it.copy(
                            isDemoMode = false,
                            speedKmh = speedMetric,
                            batteryPercent = socMetric,
                            batteryVoltageV = voltageMetric,
                            batteryCurrentA = currentMetric,
                            powerWatts = powerMetric,
                            motorTempC = motorTempMetric,
                            controllerTempC = ctrlTempMetric,
                            batteryTempC = battTempMetric,
                            gearDisplay = "${fd.driveMode}${fd.gearLevel ?: ""}",
                            tripDistanceKm = tripDistanceKm,
                            totalOdometerKm = cfg.totalOdometerKm,
                            currentRideDurationSec = durationSec,
                            maxSpeedKmh = maxSpeedRecorded,
                            avgSpeedKmh = avgSpeed,
                            wattHoursConsumed = wattHoursAccumulated,
                            jbdState = jbd,
                            farDriverState = fd,
                            activeWarnings = evaluateWarnings(
                                soc = socMetric.value,
                                motorTemp = motorTempMetric.value,
                                ctrlTemp = ctrlTempMetric.value,
                                battTemp = battTempMetric.value,
                                jbd = jbd,
                                fd = fd,
                                cfg = cfg,
                                isDemo = false
                            )
                        )
                    }
                }
                delay(200L)
            }
        }
    }

    private fun startRideRecordingLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                val state = _cockpitState.value
                if (state.isDemoMode) continue

                val hasLiveTelemetry = state.speedKmh.value != null || state.batteryVoltageV.value != null
                if (!hasLiveTelemetry) continue

                val now = System.currentTimeMillis()
                val currentRideId = activeRideId ?: rideDao.insertRide(
                    RideSessionEntity(
                        startTimeEpochMs = rideStartEpochMs,
                        endTimeEpochMs = now,
                        durationSec = state.currentRideDurationSec,
                        distanceKm = state.tripDistanceKm,
                        maxSpeedKmh = state.maxSpeedKmh,
                        avgSpeedKmh = state.avgSpeedKmh,
                        maxPowerWatts = maxPowerRecorded,
                        wattHoursUsed = state.wattHoursConsumed,
                        maxMotorTempC = state.motorTempC.value,
                        maxControllerTempC = state.controllerTempC.value,
                        minVoltageV = state.batteryVoltageV.value,
                        maxCurrentA = state.batteryCurrentA.value
                    )
                ).also { activeRideId = it }

                rideDao.updateRide(
                    RideSessionEntity(
                        id = currentRideId,
                        startTimeEpochMs = rideStartEpochMs,
                        endTimeEpochMs = now,
                        durationSec = state.currentRideDurationSec,
                        distanceKm = state.tripDistanceKm,
                        maxSpeedKmh = state.maxSpeedKmh,
                        avgSpeedKmh = state.avgSpeedKmh,
                        maxPowerWatts = maxPowerRecorded,
                        wattHoursUsed = state.wattHoursConsumed,
                        maxMotorTempC = state.motorTempC.value,
                        maxControllerTempC = state.controllerTempC.value,
                        minVoltageV = state.batteryVoltageV.value,
                        maxCurrentA = state.batteryCurrentA.value
                    )
                )

                rideDao.insertSample(
                    TelemetrySampleEntity(
                        rideId = currentRideId,
                        timestampMs = now,
                        speedKmh = state.speedKmh.value,
                        speedSource = state.speedKmh.source.label,
                        voltageV = state.batteryVoltageV.value,
                        currentA = state.batteryCurrentA.value,
                        powerW = state.powerWatts.value,
                        socPercent = state.batteryPercent.value,
                        motorTempC = state.motorTempC.value,
                        controllerTempC = state.controllerTempC.value,
                        batteryTempC = state.batteryTempC.value
                    )
                )
            }
        }
    }

    private fun evaluateWarnings(
        soc: Int?,
        motorTemp: Double?,
        ctrlTemp: Double?,
        battTemp: Double?,
        jbd: JbdTelemetryState,
        fd: FarDriverTelemetryState,
        cfg: UserCockpitSettings,
        isDemo: Boolean
    ): List<DashboardWarning> {
        val list = mutableListOf<DashboardWarning>()
        if (soc != null && soc <= cfg.lowBatteryThresholdPct) {
            list.add(
                DashboardWarning(
                    "low_soc",
                    "LOW BATTERY ($soc%)",
                    "User threshold: ${cfg.lowBatteryThresholdPct}%",
                    soc <= 10
                )
            )
        }
        if (motorTemp != null && motorTemp >= cfg.highMotorTempWarningC) {
            list.add(
                DashboardWarning(
                    "motor_temp",
                    "MOTOR TEMP HIGH (${motorTemp.toInt()}°C)",
                    "User limit: ${cfg.highMotorTempWarningC.toInt()}°C",
                    motorTemp >= cfg.highMotorTempWarningC + 15
                )
            )
        }
        if (ctrlTemp != null && ctrlTemp >= cfg.highControllerTempWarningC) {
            list.add(
                DashboardWarning(
                    "ctrl_temp",
                    "CONTROLLER TEMP HIGH (${ctrlTemp.toInt()}°C)",
                    "User limit: ${cfg.highControllerTempWarningC.toInt()}°C",
                    ctrlTemp >= cfg.highControllerTempWarningC + 10
                )
            )
        }
        if (battTemp != null && battTemp >= cfg.highBatteryTempWarningC) {
            list.add(
                DashboardWarning(
                    "batt_temp",
                    "BATTERY TEMP HIGH (${battTemp.toInt()}°C)",
                    "User limit: ${cfg.highBatteryTempWarningC.toInt()}°C",
                    true
                )
            )
        }
        if (!isDemo) {
            if (jbd.connectionState == BleConnectionState.RECONNECTING) {
                list.add(DashboardWarning("bms_drop", "BMS RECONNECTING", "BLE link interrupted", false))
            }
            if (fd.connectionState == BleConnectionState.RECONNECTING) {
                list.add(DashboardWarning("fd_drop", "CONTROLLER RECONNECTING", "BLE link interrupted", false))
            }
            jbd.activeFaults.forEachIndexed { idx, fault ->
                list.add(DashboardWarning("bms_flt_$idx", fault, "Reported by JBD BMS", true))
            }
            fd.activeFaults.forEachIndexed { idx, fault ->
                list.add(DashboardWarning("fd_flt_$idx", fault, "Reported by FarDriver ND72450", true))
            }
        }
        return list
    }

    fun initGpsListener() {
        try {
            val locManager = getApplication<Application>()
                .getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0.5f,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (location.hasSpeed()) {
                            gpsSpeedKmh = (location.speed * 3.6)
                            gpsSpeedTimestampMs = System.currentTimeMillis()
                        }
                        lastGpsLocation?.let { prev ->
                            val deltaMeters = prev.distanceTo(location)
                            if (deltaMeters in 0.5f..100f && farDriverManager.state.value.speedKmh == null) {
                                val deltaKm = deltaMeters / 1000.0
                                tripDistanceKm += deltaKm
                                viewModelScope.launch { settingsStore.incrementOdometer(deltaKm) }
                            }
                        }
                        lastGpsLocation = location
                    }
                }
            )
        } catch (_: SecurityException) {
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unnamed BLE"
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()

            val detectedType = when {
                uuids.contains(JbdBmsBleManager.SERVICE_UUID) ||
                        name.contains("JBD", true) ||
                        name.contains("Xiaoxiang", true) ||
                        name.contains("DB24", true) -> DetectedDeviceType.JBD_BMS

                uuids.contains(FarDriverBleManager.SERVICE_UUID_PRIMARY) ||
                        name.contains("Fardriver", true) ||
                        name.contains("ND72", true) ||
                        name.contains("Motor", true) -> DetectedDeviceType.FARDRIVER_CONTROLLER

                else -> DetectedDeviceType.UNKNOWN
            }

            val entry = ScannedBleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                detectedType = detectedType
            )

            _scannedDevices.update { current ->
                val mutable = current.toMutableList()
                val idx = mutable.indexOfFirst { it.address == entry.address }
                if (idx >= 0) mutable[idx] = entry else mutable.add(entry)
                mutable.sortedByDescending { it.detectedType != DetectedDeviceType.UNKNOWN }
            }
        }
    }

    fun startBleScan() {
        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner ?: return
        if (_isScanning.value) return
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, bleScanCallback)

        viewModelScope.launch {
            delay(10_000L)
            stopBleScan()
        }
    }

    fun stopBleScan() {
        if (!_isScanning.value) return
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(bleScanCallback)
        _isScanning.value = false
    }

    fun connectAsFarDriver(device: ScannedBleDevice) {
        stopBleScan()
        viewModelScope.launch {
            settingsStore.setDemoMode(false)
            settingsStore.saveDeviceAddress(isFarDriver = true, address = device.address)
        }
        farDriverManager.connect(device.address, device.name)
    }

    fun connectAsJbdBms(device: ScannedBleDevice) {
        stopBleScan()
        viewModelScope.launch {
            settingsStore.setDemoMode(false)
            settingsStore.saveDeviceAddress(isFarDriver = false, address = device.address)
        }
        jbdManager.connect(device.address, device.name)
    }

    fun deleteRide(rideId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            rideDao.deleteRideById(rideId)
        }
    }

    fun exportRide(ride: RideSessionEntity, asJson: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = rideDao.getSamplesForRide(ride.id)
            val file = if (asJson) {
                RideExportManager.exportRideToJson(getApplication(), ride, samples)
            } else {
                RideExportManager.exportRideToCsv(getApplication(), ride, samples)
            }
            _lastExportMessage.value = "Exported: ${file.absolutePath}"
        }
    }

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch { settingsStore.setDemoMode(enabled) }
    fun setAccentColor(hex: Long) = viewModelScope.launch { settingsStore.updateAccentColor(hex) }
    fun setUseMph(useMph: Boolean) = viewModelScope.launch { settingsStore.setUseMph(useMph) }
    fun setPureBlack(pureBlack: Boolean) = viewModelScope.launch { settingsStore.setPureBlack(pureBlack) }
    fun setWidgetScale(scale: Float) = viewModelScope.launch { settingsStore.setWidgetScale(scale) }
    fun setWidgetVisibility(left: Boolean, right: Boolean) =
        viewModelScope.launch { settingsStore.setWidgetVisibility(left, right) }
    fun updateWarningThresholds(lowBatt: Int, motorC: Double, ctrlC: Double, battC: Double) =
        viewModelScope.launch { settingsStore.updateThresholds(lowBatt, motorC, ctrlC, battC) }
}
