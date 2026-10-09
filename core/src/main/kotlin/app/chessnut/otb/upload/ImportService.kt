package app.chessnut.otb.upload

import app.chessnut.otb.lichess.ImportedGame
import app.chessnut.otb.lichess.ImportOutcome
import app.chessnut.otb.lichess.LichessImporter
import app.chessnut.otb.pgn.GameFingerprint

sealed class SubmitResult {
    data class Uploaded(val game: ImportedGame) : SubmitResult()
    data object Duplicate : SubmitResult()
    data class Queued(val message: String) : SubmitResult()
    data class Failed(val message: String) : SubmitResult()
}

class ImportService(
    private val importer: LichessImporter,
    private val memory: GameMemory,
) {
    suspend fun submit(pgn: String, token: String?): SubmitResult {
        val prepared = pgn.trim()
        if (prepared.isEmpty()) return SubmitResult.Failed("Paste a PGN first.")
        if (prepared.length > MAX_PGN_CHARS) {
            return SubmitResult.Failed("That PGN is too large to import.")
        }
        val fingerprint = GameFingerprint.of(prepared)
        if (memory.has(fingerprint)) return SubmitResult.Duplicate
        return when (val outcome = importer.import(prepared, token?.takeIf { it.isNotBlank() })) {
            is ImportOutcome.Success -> {
                memory.remember(fingerprint)
                memory.removePending(prepared)
                SubmitResult.Uploaded(outcome.game)
            }
            is ImportOutcome.RateLimited -> {
                memory.enqueue(prepared)
                SubmitResult.Queued("Lichess is rate limiting imports. This game will be sent later.")
            }
            is ImportOutcome.Unavailable -> {
                memory.enqueue(prepared)
                SubmitResult.Queued(outcome.message)
            }
            is ImportOutcome.Rejected -> {
                SubmitResult.Failed("Lichess rejected the game (${outcome.status}).")
            }
        }
    }

    /** Sends queued games until one cannot be sent. Rejected games are dropped. */
    suspend fun flush(token: String?): List<SubmitResult> {
        val results = mutableListOf<SubmitResult>()
        for (pgn in memory.pending()) {
            val result = submit(pgn, token)
            when (result) {
                is SubmitResult.Uploaded, is SubmitResult.Duplicate, is SubmitResult.Failed ->
                    memory.removePending(pgn)
                is SubmitResult.Queued -> return results + result
            }
            results += result
        }
        return results
    }

    private companion object {
        const val MAX_PGN_CHARS = 256_000
    }
}
