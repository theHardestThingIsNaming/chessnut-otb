package app.chessnut.otb.protocol

/**
 * Bytes to and from a Chessnut board. BLE, USB, and the test double all
 * implement this. Nothing in this module opens a socket or a radio.
 */
interface BoardTransport {
    suspend fun write(bytes: ByteArray)
    suspend fun read(): ByteArray
}

class FakeTransport : BoardTransport {
    val written: MutableList<ByteArray> = mutableListOf()
    private val incoming: ArrayDeque<ByteArray> = ArrayDeque()

    fun enqueue(bytes: ByteArray) {
        incoming.addLast(bytes.copyOf())
    }

    override suspend fun write(bytes: ByteArray) {
        written.add(bytes.copyOf())
    }

    override suspend fun read(): ByteArray {
        check(incoming.isNotEmpty()) { "Fake transport has no queued reply" }
        return incoming.removeFirst()
    }
}
