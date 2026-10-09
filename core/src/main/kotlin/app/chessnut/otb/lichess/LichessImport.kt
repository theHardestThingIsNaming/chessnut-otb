package app.chessnut.otb.lichess

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ImportedGame(val id: String, val url: String)

object LichessImport {
    const val ENDPOINT = "https://lichess.org/api/import"

    fun formBody(pgn: String): String =
        "pgn=" + URLEncoder.encode(pgn, StandardCharsets.UTF_8)

    fun parse(json: String): ImportedGame {
        val id = field.find(json)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Lichess response has no game id")
        val url = urlField.find(json)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: "https://lichess.org/$id"
        return ImportedGame(id, url)
    }

    private val field = Regex(""""id"\s*:\s*"([^"]+)"""")
    private val urlField = Regex(""""url"\s*:\s*"([^"]+)"""")
}

sealed class ImportOutcome {
    data class Success(val game: ImportedGame) : ImportOutcome()
    data class Rejected(val status: Int, val body: String) : ImportOutcome()
    data class RateLimited(val retryAfterSeconds: Long?) : ImportOutcome()
    data class Unavailable(val message: String) : ImportOutcome()
}

interface LichessImporter {
    suspend fun import(pgn: String, token: String?): ImportOutcome
}
