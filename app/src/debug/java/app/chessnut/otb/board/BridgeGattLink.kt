package app.chessnut.otb.board

import app.chessnut.otb.protocol.ByteLog
import app.chessnut.otb.protocol.ChessnutUuids
import app.chessnut.otb.protocol.FoundBoard
import app.chessnut.otb.protocol.GattException
import app.chessnut.otb.protocol.GattLink
import app.chessnut.otb.protocol.GattNotification
import com.bleforemulator.BLEBridge
import com.bleforemulator.BLEService
import com.bleforemulator.BridgeEvent
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [GattLink] over BLEForEmulator: the emulator talks TCP to the Mac menu-bar
 * app at 10.0.2.2:7877, which drives the Mac's Bluetooth radio with
 * CoreBluetooth. The Mac picks the ATT MTU; there is no way to request one.
 */
class BridgeGattLink(private val log: ByteLog) : GattLink {
    private val bridge = BLEBridge()
    private val events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 1024)
    private val incoming = MutableSharedFlow<GattNotification>(extraBufferCapacity = 1024)
    override val notifications: SharedFlow<GattNotification> = incoming.asSharedFlow()

    private var ready = CompletableDeferred<Unit>()
    private var started = false
    private var connectionId: String? = null
    private var services: List<BLEService> = emptyList()

    init {
        bridge.onEvent = { event ->
            when (event) {
                is BridgeEvent.BridgeReady -> {
                    log.line("bridge ready, Mac Bluetooth ${event.bluetoothState}")
                    ready.complete(Unit)
                }
                is BridgeEvent.CharacteristicChanged -> uuidOrNull(event.characteristicUuid)?.let {
                    incoming.tryEmit(GattNotification(it, event.value))
                }
                is BridgeEvent.Disconnected -> log.line("bridge: board disconnected ${event.error.orEmpty()}")
                is BridgeEvent.ParseError -> log.line("bridge: unreadable line ${event.line}")
                else -> Unit
            }
            events.tryEmit(event)
        }
        bridge.onDisconnect = {
            log.line("bridge TCP connection closed")
            started = false
            ready = CompletableDeferred()
        }
    }

    override fun scan(): Flow<FoundBoard> = channelFlow {
        ensureBridge()
        launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                if (event is BridgeEvent.AdvertisementFound) {
                    log.line(
                        "adv ${event.localName ?: "(no name)"} rssi=${event.rssi}" +
                            event.serviceUuids.joinToString(prefix = " ") { it.take(8) },
                    )
                    val name = event.localName?.trim()?.takeIf { it.isNotEmpty() }
                    val classic = ChessnutUuids.isClassicBoard(name, event.serviceUuids)
                    if (classic) {
                        send(FoundBoard(event.address, name ?: "Chessnut board", event.rssi))
                    }
                }
            }
        }
        bridge.startScan()
        try {
            awaitCancellation()
        } finally {
            bridge.stopScan()
        }
    }

    override suspend fun connect(address: String) {
        ensureBridge()
        bridge.stopScan()
        val connected = awaitEvent(15.seconds, { bridge.connect(address) }) {
            (it is BridgeEvent.Connected && it.address == address) ||
                (it is BridgeEvent.ConnectionFailed && it.address == address)
        }
        when (connected) {
            is BridgeEvent.Connected -> connectionId = connected.connectionId
            is BridgeEvent.ConnectionFailed -> throw GattException("Connect failed: ${connected.error}")
            else -> throw GattException("The Mac bridge did not connect to $address within 15 s.")
        }
        val id = connectionId!!
        val discovered = awaitEvent(15.seconds, { bridge.discoverServices(id) }) {
            it is BridgeEvent.ServicesDiscovered && it.connectionId == id
        } as? BridgeEvent.ServicesDiscovered ?: throw GattException("Service discovery timed out.")
        services = discovered.services
        services.forEach { service ->
            log.line("service ${service.uuid}: " + service.characteristics.joinToString { "${it.uuid} ${it.properties}" })
        }
    }

    override suspend fun enableNotify(service: UUID, characteristic: UUID) {
        val id = connectionId ?: throw GattException("Not connected.")
        find(service, characteristic) ?: throw GattException("No characteristic $characteristic")
        bridge.setNotify(id, service.bridgeForm(), characteristic.bridgeForm(), true)
        delay(150.milliseconds)
    }

    override suspend fun write(service: UUID, characteristic: UUID, bytes: ByteArray) {
        val id = connectionId ?: throw GattException("Not connected.")
        val target = find(service, characteristic) ?: throw GattException("No characteristic $characteristic")
        val noResponse = target.properties.any { it.contains("without", ignoreCase = true) }
        if (noResponse) {
            bridge.writeCharacteristic(id, service.bridgeForm(), characteristic.bridgeForm(), bytes, withResponse = false)
            return
        }
        awaitEvent(2.seconds, {
            bridge.writeCharacteristic(id, service.bridgeForm(), characteristic.bridgeForm(), bytes, withResponse = true)
        }) { it is BridgeEvent.WriteAcknowledged && it.connectionId == id }
            ?: log.line("no write acknowledgement from the bridge")
    }

    override suspend fun disconnect() {
        connectionId?.let { bridge.disconnectPeripheral(it) }
        connectionId = null
        services = emptyList()
    }

    private suspend fun ensureBridge() {
        if (!started) {
            started = true
            bridge.connect()
        }
        withTimeoutOrNull(5.seconds) { ready.await() }
            ?: throw GattException("BLEForEmulator is not answering at 10.0.2.2:7877. Open the Mac app and allow Bluetooth.")
    }

    private suspend fun awaitEvent(
        timeout: Duration,
        send: () -> Unit,
        predicate: (BridgeEvent) -> Boolean,
    ): BridgeEvent? = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { events.first(predicate) }
        send()
        withTimeoutOrNull(timeout) { waiter.await() }.also { if (it == null) waiter.cancel() }
    }

    private fun find(service: UUID, characteristic: UUID) =
        services.firstOrNull { uuidOrNull(it.uuid) == service }
            ?.characteristics?.firstOrNull { uuidOrNull(it.uuid) == characteristic }

    private fun UUID.bridgeForm(): String = toString().uppercase()

    private fun uuidOrNull(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
