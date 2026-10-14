package app.chessnut.otb.flags

import app.chessnut.otb.play.PlayPosition
import com.github.bhlangonijr.chesslib.Board
import java.io.File

/**
 * A move the user wants explained in more detail. [moves] are the UCI moves from the start, the last being the
 * move in question, so the position before it can always be rebuilt. [key] names the move by its position, so an
 * explanation written for it shows wherever the same position and move turn up.
 */
data class MoveFlag(
    val key: String,
    val moves: List<String>,
    /** Which screen it was flagged on: master, train, analysis, ... */
    val source: String,
    /** Where it was: the opening and line, the master game and ply, or the game. */
    val context: String,
    /** The note that was showing with the move, if any. */
    val note: String,
    /** What the user wants to know, in their words; may be empty. */
    val question: String,
    val at: Long,
)

object FlagKey {
    /** The key of the last move in [uciMoves]: the position before it (pieces, side, castling, en passant) and the move. */
    fun of(uciMoves: List<String>): String? {
        if (uciMoves.isEmpty()) return null
        var fen = Board().fen
        for (uci in uciMoves.dropLast(1)) fen = PlayPosition.after(fen, uci)?.second ?: return null
        return of(fen, uciMoves.last())
    }

    fun of(fenBefore: String, uci: String): String =
        fenBefore.split(' ').take(4).joinToString(" ") + "|" + uci.lowercase()
}

/** Flagged moves, kept in a small tab-separated file the phone hands over with adb. */
class FlagStore(private val file: File) {
    private val flags = linkedMapOf<String, MoveFlag>()

    init {
        runCatching {
            if (file.exists()) file.readLines().forEach { line ->
                val p = line.split('\t')
                if (p.size >= 7) flags[p[0]] = MoveFlag(p[0], p[1].split(' ').filter { it.isNotBlank() }, p[2], unescape(p[3]), unescape(p[4]), unescape(p[5]), p[6].toLongOrNull() ?: 0L)
            }
        }
    }

    @Synchronized
    fun get(key: String): MoveFlag? = flags[key]

    @Synchronized
    fun all(): List<MoveFlag> = flags.values.toList()

    @Synchronized
    fun put(flag: MoveFlag) {
        flags[flag.key] = flag
        save()
    }

    @Synchronized
    fun remove(key: String) {
        if (flags.remove(key) != null) save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(flags.values.joinToString("") { f ->
                listOf(f.key, f.moves.joinToString(" "), f.source, escape(f.context), escape(f.note), escape(f.question), f.at.toString()).joinToString("\t") + "\n"
            })
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}

/** Written explanations of moves, by [FlagKey]: `move-insights.tsv`, `key <TAB> text` with `\n` for a new line. */
object MoveInsights {
    private val byKey: Map<String, String> by lazy {
        MoveInsights::class.java.getResourceAsStream("/move-insights.tsv")?.bufferedReader()?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { it.split('\t', limit = 2).takeIf { p -> p.size == 2 } }
            .associate { (key, text) -> key to text.replace("\\n", "\n") }
    }

    fun of(key: String?): String? = key?.let { byKey[it] }

    val keys: Set<String> get() = byKey.keys
}
