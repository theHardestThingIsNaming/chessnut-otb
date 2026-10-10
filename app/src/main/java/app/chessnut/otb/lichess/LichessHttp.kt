package app.chessnut.otb.lichess

import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class LichessCall(val status: Int, val body: String)

/** HTTP calls for the Lichess board API. [Pipe.close] ends a stream that is still open. */
class LichessHttp(private val token: () -> String?) {
    inner class Pipe : Closeable {
        @Volatile private var connection: HttpURLConnection? = null

        fun lines(method: String, path: String, form: String?, onLine: (String) -> Unit) {
            val connection = open(method, path, form, auth = true, onReady = { this.connection = it })
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                val err = connection.errorStream?.let { runCatching { it.bufferedReader().readText() }.getOrNull() }.orEmpty()
                Log.w("Lichess", "$method $path", e)
                if (err.isNotBlank()) throw LichessHttpException(0, err)
                throw e
            }
            Log.i("Lichess", "$method $path -> $code")
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw LichessHttpException(code, err)
            }
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            }
        }

        override fun close() {
            connection?.disconnect()
        }
    }

    fun exchange(form: String): String = call("POST", "/api/token", form, auth = false).body

    fun account(): String = call("GET", "/api/account", null, auth = true).body

    fun revoke() {
        runCatching { call("DELETE", "/api/token", null, auth = true) }
    }

    fun move(gameId: String, uci: String): LichessCall {
        require(GAME_ID.matches(gameId)) { "Bad game" }
        require(UCI.matches(uci)) { "Bad move" }
        return call("POST", "/api/board/game/$gameId/move/$uci", "", auth = true)
    }

    /** Real-time games in progress, as JSON. Used to resume a game and to check a quiet stream. */
    fun playing(): String = call("GET", "/api/account/playing?nb=10", null, auth = true, json = true).body

    /** Imports one PGN. With a login, the game is credited to the account. */
    fun importPgn(pgn: String): String =
        call("POST", "/api/import", LichessImport.formBody(pgn), auth = token() != null, json = true).body

    /** Opening explorer statistics after [play], UCI moves separated by commas. Needs a login. */
    fun explorer(play: String, query: String): String {
        require(PLAY.matches(play)) { "Bad line" }
        return call("GET", "https://explorer.lichess.org/lichess?$query&play=$play", null, auth = true, json = true).body
    }

    /** A study's chapters as PGN. Public and unlisted studies need no login; private ones need study:read. */
    fun study(id: String): String {
        require(STUDY_ID.matches(id)) { "Bad study" }
        return call("GET", "/api/study/$id.pgn", null, auth = token() != null, accept = "application/x-chess-pgn").body
    }

    fun abort(gameId: String) {
        require(GAME_ID.matches(gameId)) { "Bad game" }
        call("POST", "/api/board/game/$gameId/abort", "", auth = true)
    }

    fun resign(gameId: String) {
        require(GAME_ID.matches(gameId)) { "Bad game" }
        call("POST", "/api/board/game/$gameId/resign", "", auth = true)
    }

    fun draw(gameId: String, accept: Boolean) {
        require(GAME_ID.matches(gameId)) { "Bad game" }
        val answer = if (accept) "yes" else "no"
        call("POST", "/api/board/game/$gameId/draw/$answer", "", auth = true)
    }

    private fun call(
        method: String,
        path: String,
        form: String?,
        auth: Boolean,
        json: Boolean = false,
        accept: String? = null,
    ): LichessCall {
        val connection = open(method, path, form, auth, json, accept, onReady = {})
        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.readText().orEmpty()
            if (code >= 400) throw LichessHttpException(code, body)
            LichessCall(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        method: String,
        path: String,
        form: String?,
        auth: Boolean,
        json: Boolean = false,
        accept: String? = null,
        onReady: (HttpURLConnection) -> Unit,
    ): HttpURLConnection {
        val url = if (path.startsWith("https://")) path else "https://lichess.org$path"
        val connection = URL(url).openConnection() as HttpURLConnection
        onReady(connection)
        connection.requestMethod = method
        connection.connectTimeout = 20_000
        connection.readTimeout = if (method == "GET" && path.contains("stream") || path.endsWith("/seek")) 0 else 20_000
        connection.setRequestProperty("Accept", accept ?: if (json) "application/json" else "application/x-ndjson")
        connection.setRequestProperty("User-Agent", "chessnut-otb")
        if (auth) {
            val token = token() ?: throw LichessHttpException(401, "Not logged in")
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        if (form != null) {
            val bytes = form.toByteArray(StandardCharsets.UTF_8)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(bytes) }
        }
        return connection
    }

    private companion object {
        val GAME_ID = Regex("^[A-Za-z0-9]{8}$")
        val UCI = Regex("^[a-h][1-8][a-h][1-8][nbrq]?$")
        val STUDY_ID = Regex("^[A-Za-z0-9]{8}$")
        val PLAY = Regex("^([a-h][1-8][a-h][1-8][nbrq]?)(,[a-h][1-8][a-h][1-8][nbrq]?)*$")
    }
}

class LichessHttpException(val status: Int, val body: String) : Exception(LichessOAuth.errorText(body))
