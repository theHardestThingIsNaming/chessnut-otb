package app.chessnut.otb.upload

import app.chessnut.otb.pgn.GameFingerprint
import java.io.File

interface GameMemory {
    fun has(fingerprint: String): Boolean
    fun remember(fingerprint: String)
    fun enqueue(pgn: String)
    fun pending(): List<String>
    fun removePending(pgn: String)
}

class MemoryGameMemory : GameMemory {
    private val seen = mutableSetOf<String>()
    private val queue = linkedMapOf<String, String>()

    override fun has(fingerprint: String): Boolean = fingerprint in seen

    override fun remember(fingerprint: String) {
        seen += fingerprint
    }

    override fun enqueue(pgn: String) {
        queue.putIfAbsent(GameFingerprint.of(pgn), pgn)
    }

    override fun pending(): List<String> = queue.values.toList()

    override fun removePending(pgn: String) {
        queue.remove(GameFingerprint.of(pgn))
    }
}

/** Fingerprints and the offline queue, as files under [directory]. */
class FileGameMemory(directory: File) : GameMemory {
    private val root = directory.apply { mkdirs() }
    private val fingerprints = File(root, "uploaded.txt")
    private val queueDir = File(root, "queue").apply { mkdirs() }
    private val lock = Any()

    override fun has(fingerprint: String): Boolean = synchronized(lock) {
        if (!fingerprints.exists()) return false
        fingerprints.readLines().any { it == fingerprint }
    }

    override fun remember(fingerprint: String) = synchronized(lock) {
        if (has(fingerprint)) return
        fingerprints.appendText(fingerprint + "\n")
    }

    override fun enqueue(pgn: String) = synchronized(lock) {
        val file = File(queueDir, GameFingerprint.of(pgn))
        if (!file.exists()) file.writeText(pgn)
    }

    override fun pending(): List<String> = synchronized(lock) {
        queueDir.listFiles()?.sortedBy { it.name }?.map { it.readText() }.orEmpty()
    }

    override fun removePending(pgn: String) = synchronized(lock) {
        File(queueDir, GameFingerprint.of(pgn)).delete()
        Unit
    }
}
