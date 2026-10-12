package app.chessnut.otb.chesscom

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Reads Chess.com's published data. No login: everything there is public. */
object ChessComHttp {
    fun get(url: String): String {
        require(url.startsWith("https://api.chess.com/pub/")) { "Not a Chess.com data address" }
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "chessnut-otb")
            val code = connection.responseCode
            if (code == 404) throw IOException("Chess.com has no player by that name.")
            if (code >= 400) throw IOException("Chess.com answered $code.")
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
