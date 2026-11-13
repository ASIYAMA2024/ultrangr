package com.kugoo.dashboard.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import com.kugoo.dashboard.domain.BleConnectionState
import com.kugoo.dashboard.domain.FarDriverTelemetryState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

@SuppressLint("MissingPermission")
class FarDriverBleManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        val SERVICE_UUID_PRIMARY: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID_FFEC: UUID = UUID.fromString("0000ffec-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID_FFE1: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val _state = MutableStateFlow(FarDriverTelemetryState())
    val state: StateFlow<FarDriverTelemetryState> = _state.asStateFlow()

    var wheelCircumferenceMeters: Double = 1.59
    var motorPolePairs: Int = 5
    var gearReductionRatio: Double = 4.77

    private var bluetoothGatt: BluetoothGatt? = null
    private var reconnectJob: Job? = null
    private var targetAddress: String? = null
    private var userRequestedDisconnect = false
    private var reconnectAttempt = 0

    fun connect(address: String, deviceName: String? = null) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return
        }

        userRequestedDisconnect = false
        targetAddress = address
        reconnectJob?.cancel()

        _state.update {
            it.copy(
                connectionState = BleConnectionState.CONNECTING,
                deviceAddress = address,
                deviceName = deviceName ?: device.name ?: "FarDriver ND72450"
            )
        }

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _state.update { it.copy(connectionState = BleConnectionState.DISCONNECTED) }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempt = 0
                _state.update { it.copy(connectionState = BleConnectionState.CONNECTED) }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
                if (!userRequestedDisconnect && targetAddress != null) {
                    scheduleReconnect()
                } else {
                    _state.update { it.copy(connectionState = BleConnectionState.DISCONNECTED) }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service = gatt.getService(SERVICE_UUID_PRIMARY)
                ?: gatt.services.firstOrNull { svc ->
                    svc.getCharacteristic(CHAR_UUID_FFEC) != null ||
                            svc.getCharacteristic(CHAR_UUID_FFE1) != null
                }

            val notifyChar = service?.getCharacteristic(CHAR_UUID_FFEC)
                ?: service?.getCharacteristic(CHAR_UUID_FFE1)
                ?: gatt.services.flatMap { it.characteristics }.firstOrNull { char ->
                    (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                }

            if (notifyChar != null) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                _state.update { it.copy(connectionState = BleConnectionState.ERROR) }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { parseFarDriverPacket(it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseFarDriverPacket(value)
        }
    }

    private fun parseFarDriverPacket(packet: ByteArray) {
        if (packet.size < 16 || packet[0] != 0xAA.toByte()) return

        if (!verifyFarDriverCrc(packet)) {
            _state.update { it.copy(crcErrors = it.crcErrors + 1) }
            return
        }

        val frameId = packet[1].toInt() and 0xFF
        val buf = ByteBuffer.wrap(packet, 2, 12).order(ByteOrder.LITTLE_ENDIAN)
        val now = System.currentTimeMillis()

        when (frameId) {
            0x80, 0x00 -> {
                val flagsAndGear = buf.short.toInt() and 0xFFFF
                val rawVoltage = buf.short.toInt() and 0xFFFF
                val rawLineCurrent = buf.short.toInt()
                val faultFlags = buf.short.toInt() and 0xFFFF

                val dnrCode = flagsAndGear and 0x03
                val driveMode = when (dnrCode) {
                    1 -> "D"
                    2 -> "R"
                    else -> "N"
                }
                val gear = ((flagsAndGear shr 2) and 0x03).coerceIn(1, 3)
                val voltageV = rawVoltage / 10.0
                val lineCurrentA = rawLineCurrent / 4.0

                _state.update {
                    it.copy(
                        driveMode = driveMode,
                        gearLevel = gear,
                        voltageV = if (voltageV in 20.0..120.0) voltageV else it.voltageV,
                        lineCurrentA = lineCurrentA,
                        faultBitmask = faultFlags,
                        activeFaults = decodeControllerFaults(faultFlags),
                        packetsReceived = it.packetsReceived + 1,
                        lastUpdateMs = now
                    )
                }
            }
            0x81, 0x01 -> {
                val rawRpm = abs(buf.short.toInt())
                val rawPhaseCurrent = buf.short.toInt() / 4.0
                val wheelRpm = rawRpm.toDouble() / gearReductionRatio
                val speedKmh = (wheelRpm * wheelCircumferenceMeters * 60.0) / 1000.0

                _state.update {
                    it.copy(
                        rpm = rawRpm,
                        speedKmh = speedKmh.coerceIn(0.0, 199.0),
                        phaseCurrentA = rawPhaseCurrent,
                        packetsReceived = it.packetsReceived + 1,
                        lastUpdateMs = now
                    )
                }
            }
            0x82, 0x04 -> {
                val ctrlTemp = buf.short.toInt().toDouble()
                val motorTemp = buf.short.toInt().toDouble()

                _state.update {
                    it.copy(
                        controllerTempC = if (ctrlTemp in -40.0..180.0) ctrlTemp else it.controllerTempC,
                        motorTempC = if (motorTemp in -40.0..220.0) motorTemp else it.motorTempC,
                        packetsReceived = it.packetsReceived + 1,
                        lastUpdateMs = now
                    )
                }
            }
        }
    }

    private fun verifyFarDriverCrc(packet: ByteArray): Boolean {
        if (packet.size < 16) return false
        val crcLo = packet[14].toInt() and 0xFF
        val crcHi = packet[15].toInt() and 0xFF
        return !(crcLo == 0 && crcHi == 0)
    }

    private fun decodeControllerFaults(mask: Int): List<String> {
        if (mask == 0) return emptyList()
        val list = mutableListOf<String>()
        if ((mask and (1 shl 0)) != 0) list.add("CTRL: Hall Sensor Fault")
        if ((mask and (1 shl 1)) != 0) list.add("CTRL: Throttle Fault (High/Open)")
        if ((mask and (1 shl 2)) != 0) list.add("CTRL: Phase Overcurrent")
        if ((mask and (1 shl 3)) != 0) list.add("CTRL: Overvoltage Protection")
        if ((mask and (1 shl 4)) != 0) list.add("CTRL: Undervoltage Protection")
        if ((mask and (1 shl 5)) != 0) list.add("CTRL: Controller Over-Temp")
        if ((mask and (1 shl 6)) != 0) list.add("CTRL: Motor NTC Over-Temp")
        return list
    }

    private fun scheduleReconnect() {
        val address = targetAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            _state.update { it.copy(connectionState = BleConnectionState.RECONNECTING) }
            val delaySec = min(30.0, 2.0.pow(reconnectAttempt.toDouble())).toLong()
            reconnectAttempt++
            delay(delaySec * 1000L)
            if (!userRequestedDisconnect) {
                connect(address, _state.value.deviceName)
            }
        }
    }
}
