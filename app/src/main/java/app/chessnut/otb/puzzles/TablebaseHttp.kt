package app.chessnut.otb.puzzles

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The Lichess tablebase: perfect play for positions with seven pieces or fewer. Free, no login. */
object TablebaseHttp {
    private val cache = mutableMapOf<String, TablebaseView>()

    @Synchronized
    fun lookup(fen: String): TablebaseView {
        cache[fen]?.let { return it }
        val connection = URL("https://tablebase.lichess.ovh/standard?fen=" + fen.trim().replace(' ', '_')).openConnection() as HttpURLConnection
        val view = try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "chessnut-otb")
            if (connection.responseCode != 200) throw IOException("The tablebase answered ${connection.responseCode}.")
            TablebaseView.parse(connection.inputStream.bufferedReader().readText()) ?: throw IOException("The tablebase answer couldn't be read.")
        } finally {
            connection.disconnect()
        }
        cache[fen] = view
        return view
    }
}
