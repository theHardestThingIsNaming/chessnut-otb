package app.chessnut.otb.board

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import app.chessnut.otb.protocol.ByteLog
import app.chessnut.otb.protocol.ChessnutUuids
import app.chessnut.otb.protocol.FoundBoard
import app.chessnut.otb.protocol.GattException
import app.chessnut.otb.protocol.GattLink
import app.chessnut.otb.protocol.GattNotification
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [GattLink] over Android's `BluetoothGatt`. Android allows one outstanding
 * GATT operation per connection, so descriptor and characteristic writes go
 * through [operations] one at a time and wait for their callback.
 * Permissions are checked by the screen before any call reaches here.
 */
@SuppressLint("MissingPermission")
class AndroidGattLink(context: Context, private val log: ByteLog) : GattLink {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val incoming = MutableSharedFlow<GattNotification>(extraBufferCapacity = 1024)
    override val notifications: SharedFlow<GattNotification> = incoming.asSharedFlow()

    private val operations = Mutex()
    private var gatt: BluetoothGatt? = null

    @Volatile private var setup: CompletableDeferred<Unit>? = null
    @Volatile private var pending: CompletableDeferred<Int>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log.line("connection state $newState status $status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!g.requestMtu(REQUESTED_MTU)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val error = GattException("The board disconnected (status $status).")
                setup?.completeExceptionally(error)
                pending?.completeExceptionally(error)
                g.close()
                if (gatt === g) gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            log.line("MTU $mtu (status $status)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            g.services.forEach { service ->
                log.line("service ${service.uuid}: " + service.characteristics.joinToString { "${it.uuid} p=${it.properties}" })
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setup?.complete(Unit)
            } else {
                setup?.completeExceptionally(GattException("Service discovery failed (status $status)."))
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pending?.takeIf { it.isActive }?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.tryEmit(GattNotification(characteristic.uuid, value.copyOf()))
        }

        @Deprecated("Called below API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            incoming.tryEmit(GattNotification(characteristic.uuid, value.copyOf()))
        }
    }

    override fun scan(): Flow<FoundBoard> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw GattException("Bluetooth is off.")
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name
                val services = result.scanRecord?.serviceUuids?.map { it.toString() }.orEmpty()
                if (ChessnutUuids.isClassicBoard(name, services)) {
                    trySend(FoundBoard(result.device.address, name ?: "Chessnut board", result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(GattException("Bluetooth scan failed (code $errorCode)."))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        awaitClose { runCatching { scanner.stopScan(scanCallback) } }
    }

    override suspend fun connect(address: String) {
        val bluetooth = adapter?.takeIf { it.isEnabled } ?: throw GattException("Bluetooth is off.")
        val device = bluetooth.getRemoteDevice(address)
        val ready = CompletableDeferred<Unit>()
        setup = ready
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(20.seconds) { ready.await() }
        } catch (e: Exception) {
            disconnect()
            throw e as? GattException ?: GattException("Could not connect to $address: ${e.message}")
        }
    }

    override suspend fun enableNotify(service: UUID, characteristic: UUID) {
        val g = gatt ?: throw GattException("Not connected.")
        val target = g.getService(service)?.getCharacteristic(characteristic)
            ?: throw GattException("No characteristic $characteristic")
        if (!g.setCharacteristicNotification(target, true)) throw GattException("Notify refused for $characteristic")
        val cccd = target.getDescriptor(CCCD) ?: return
        val value = if (target.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        runOperation("enable notify $characteristic") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
    }

    override suspend fun write(service: UUID, characteristic: UUID, bytes: ByteArray) {
        val g = gatt ?: throw GattException("Not connected.")
        val target = g.getService(service)?.getCharacteristic(characteristic)
            ?: throw GattException("No characteristic $characteristic")
        val noResponse = target.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val type = if (noResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        runOperation("write $characteristic", waitForCallback = !noResponse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(target, bytes, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                target.writeType = type
                @Suppress("DEPRECATION")
                target.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(target)
            }
        }
    }

    override suspend fun disconnect() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    private suspend fun runOperation(
        name: String,
        waitForCallback: Boolean = true,
        start: () -> Boolean,
    ) = operations.withLock {
        if (!waitForCallback) {
            if (!start()) throw GattException("Android refused to $name.")
            delay(40.milliseconds)
            return@withLock
        }
        val done = CompletableDeferred<Int>()
        pending = done
        try {
            if (!start()) throw GattException("Android refused to $name.")
            val status = withTimeoutOrNull(3.seconds) { done.await() }
                ?: throw GattException("No callback for $name within 3 s.")
            if (status != BluetoothGatt.GATT_SUCCESS) throw GattException("$name failed with status $status.")
        } finally {
            if (pending === done) pending = null
        }
    }

    private companion object {
        const val REQUESTED_MTU = 500
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
