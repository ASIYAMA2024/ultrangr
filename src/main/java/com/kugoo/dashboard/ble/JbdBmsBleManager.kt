package com.kugoo.dashboard.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.kugoo.dashboard.domain.BleConnectionState
import com.kugoo.dashboard.domain.JbdTelemetryState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

@SuppressLint("MissingPermission")
class JbdBmsBleManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "JbdBmsBleManager"
        val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val CHAR_NOTIFY_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val CHAR_WRITE_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val CMD_BASIC_INFO = byteArrayOf(
            0xDD.toByte(), 0xA5.toByte(), 0x03.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xFD.toByte(), 0x77.toByte()
        )
        val CMD_CELL_VOLTAGES = byteArrayOf(
            0xDD.toByte(), 0xA5.toByte(), 0x04.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xFC.toByte(), 0x77.toByte()
        )
    }

    private val _state = MutableStateFlow(JbdTelemetryState())
    val state: StateFlow<JbdTelemetryState> = _state.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var targetAddress: String? = null
    private var userRequestedDisconnect = false
    private var reconnectAttempt = 0

    private val frameBuffer = ByteArrayOutputStream()

    fun connect(address: String, deviceName: String? = null) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address: $address", e)
            return
        }

        userRequestedDisconnect = false
        targetAddress = address
        reconnectJob?.cancel()

        _state.update {
            it.copy(
                connectionState = BleConnectionState.CONNECTING,
                deviceAddress = address,
                deviceName = deviceName ?: device.name ?: "JBD BMS"
            )
        }

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        userRequestedDisconnect = true
        pollingJob?.cancel()
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
                gatt.requestMtu(185)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pollingJob?.cancel()
                gatt.close()
                if (!userRequestedDisconnect && targetAddress != null) {
                    scheduleReconnect()
                } else {
                    _state.update { it.copy(connectionState = BleConnectionState.DISCONNECTED) }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: run {
                _state.update { it.copy(connectionState = BleConnectionState.ERROR) }
                return
            }

            val notifyChar = service.getCharacteristic(CHAR_NOTIFY_UUID)
            writeCharacteristic = service.getCharacteristic(CHAR_WRITE_UUID)

            if (notifyChar != null && writeCharacteristic != null) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                } else {
                    startPollingLoop()
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                startPollingLoop()
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                characteristic.value?.let { onBytesReceived(it) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                onBytesReceived(value)
            }
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            var toggleCellPoll = 0
            while (isActive && _state.value.connectionState == BleConnectionState.CONNECTED) {
                val cmd = if (toggleCellPoll % 3 == 0) CMD_CELL_VOLTAGES else CMD_BASIC_INFO
                sendCommand(cmd)
                toggleCellPoll++
                delay(500L)
            }
        }
    }

    private fun sendCommand(command: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        char.value = command
        gatt.writeCharacteristic(char)
    }

    @Synchronized
    private fun onBytesReceived(chunk: ByteArray) {
        if (chunk.isNotEmpty() && chunk[0] == 0xDD.toByte()) {
            frameBuffer.reset()
        }
        frameBuffer.write(chunk)
        val accumulated = frameBuffer.toByteArray()

        if (accumulated.size >= 4 && accumulated[0] == 0xDD.toByte()) {
            val dataLen = accumulated[3].toInt() and 0xFF
            val expectedTotalLen = dataLen + 7
            if (accumulated.size >= expectedTotalLen) {
                val fullFrame = accumulated.copyOfRange(0, expectedTotalLen)
                frameBuffer.reset()
                parseJbdFrame(fullFrame)
            }
        }
    }

    private fun parseJbdFrame(frame: ByteArray) {
        if (frame.first() != 0xDD.toByte() || frame.last() != 0x77.toByte()) return
        val cmd = frame[1].toInt() and 0xFF
        val status = frame[2].toInt() and 0xFF
        val length = frame[3].toInt() and 0xFF

        if (status != 0x00 || frame.size < length + 7) return

        var sum = status + length
        for (i in 0 until length) {
            sum += (frame[4 + i].toInt() and 0xFF)
        }
        val calculatedChecksum = (0x10000 - (sum and 0xFFFF)) and 0xFFFF
        val receivedChecksum = ((frame[4 + length].toInt() and 0xFF) shl 8) or
                (frame[5 + length].toInt() and 0xFF)

        if (calculatedChecksum != receivedChecksum) {
            _state.update { it.copy(crcErrors = it.crcErrors + 1) }
            return
        }

        val payload = ByteBuffer.wrap(frame, 4, length).order(ByteOrder.BIG_ENDIAN)
        val now = System.currentTimeMillis()

        when (cmd) {
            0x03 -> {
                if (length < 23) return
                val totalVoltageV = (payload.short.toInt() and 0xFFFF) * 0.01
                val currentAmps = payload.short.toInt() * 0.01
                val remCapAh = (payload.short.toInt() and 0xFFFF) * 0.01
                val nomCapAh = (payload.short.toInt() and 0xFFFF) * 0.01
                val cycles = payload.short.toInt() and 0xFFFF
                payload.short
                payload.short
                payload.short
                val protectionMask = payload.short.toInt() and 0xFFFF
                payload.get()
                val rsoc = (payload.get().toInt() and 0xFF).coerceIn(0, 100)
                val fetStatus = payload.get().toInt() and 0xFF
                val cellCount = payload.get().toInt() and 0xFF
                val ntcCount = payload.get().toInt() and 0xFF

                val temps = mutableListOf<Double>()
                for (i in 0 until ntcCount) {
                    if (payload.remaining() >= 2) {
                        val rawKelvinTenths = payload.short.toInt() and 0xFFFF
                        val tempC = (rawKelvinTenths - 2731) / 10.0
                        temps.add(tempC)
                    }
                }

                _state.update {
                    it.copy(
                        totalVoltageV = totalVoltageV,
                        currentAmps = currentAmps,
                        remainingCapacityAh = remCapAh,
                        nominalCapacityAh = nomCapAh,
                        cycleCount = cycles,
                        rsocPercent = rsoc,
                        chargeMosEnabled = (fetStatus and 0x01) != 0,
                        dischargeMosEnabled = (fetStatus and 0x02) != 0,
                        cellCount = if (cellCount > 0) cellCount else it.cellCount,
                        temperaturesCelsius = temps,
                        protectionBitmask = protectionMask,
                        activeFaults = decodeProtectionFlags(protectionMask),
                        packetsReceived = it.packetsReceived + 1,
                        lastUpdateMs = now
                    )
                }
            }
            0x04 -> {
                val cellCount = length / 2
                val cells = mutableListOf<Int>()
                for (i in 0 until cellCount) {
                    cells.add(payload.short.toInt() and 0xFFFF)
                }
                _state.update {
                    it.copy(
                        cellVoltagesMv = cells,
                        packetsReceived = it.packetsReceived + 1,
                        lastUpdateMs = now
                    )
                }
            }
        }
    }

    private fun decodeProtectionFlags(mask: Int): List<String> {
        if (mask == 0) return emptyList()
        val faults = mutableListOf<String>()
        if ((mask and (1 shl 0)) != 0) faults.add("BMS: Cell Overvoltage")
        if ((mask and (1 shl 1)) != 0) faults.add("BMS: Cell Undervoltage")
        if ((mask and (1 shl 2)) != 0) faults.add("BMS: Pack Overvoltage")
        if ((mask and (1 shl 3)) != 0) faults.add("BMS: Pack Undervoltage")
        if ((mask and (1 shl 4)) != 0) faults.add("BMS: Charge Over-Temp")
        if ((mask and (1 shl 5)) != 0) faults.add("BMS: Charge Under-Temp")
        if ((mask and (1 shl 6)) != 0) faults.add("BMS: Discharge Over-Temp")
        if ((mask and (1 shl 7)) != 0) faults.add("BMS: Discharge Under-Temp")
        if ((mask and (1 shl 8)) != 0) faults.add("BMS: Charge Overcurrent")
        if ((mask and (1 shl 9)) != 0) faults.add("BMS: Discharge Overcurrent")
        if ((mask and (1 shl 10)) != 0) faults.add("BMS: Short Circuit Protection")
        if ((mask and (1 shl 11)) != 0) faults.add("BMS: Front-End IC Error")
        if ((mask and (1 shl 12)) != 0) faults.add("BMS: Software MOSFET Lock")
        return faults
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
