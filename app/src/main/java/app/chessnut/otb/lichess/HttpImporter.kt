package app.chessnut.otb.lichess

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends PGNs to lichess.org/api/import. The login, when there is one, comes from [http]. */
class HttpImporter(private val http: LichessHttp) : LichessImporter {
    override suspend fun import(pgn: String, token: String?): ImportOutcome = withContext(Dispatchers.IO) {
        try {
            ImportOutcome.Success(LichessImport.parse(http.importPgn(pgn)))
        } catch (e: LichessHttpException) {
            when (e.status) {
                429 -> ImportOutcome.RateLimited(null)
                401 -> ImportOutcome.Unavailable("The Lichess login expired. Log in again to upload.")
                in 500..599 -> ImportOutcome.Unavailable("Lichess is having trouble. The game will be sent later.")
                else -> ImportOutcome.Rejected(e.status, e.body)
            }
        } catch (e: IOException) {
            ImportOutcome.Unavailable("No connection. The game will be sent later.")
        } catch (e: IllegalArgumentException) {
            ImportOutcome.Rejected(0, e.message.orEmpty())
        }
    }
}
