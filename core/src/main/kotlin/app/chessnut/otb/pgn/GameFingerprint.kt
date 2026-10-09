package app.chessnut.otb.pgn

import java.security.MessageDigest

object GameFingerprint {
    private val comment = Regex("""\{[^\}]*\}""")
    private val restOfLine = Regex(""";[^\n]*""")
    private val nag = Regex("""\$\d+""")
    private val tag = Regex("""\[[^\]]*\]""")

    /** Stable id for "have we already uploaded this game", ignoring headers. */
    fun of(pgn: String): String {
        val movetext = pgn
            .replace(tag, " ")
            .replace(comment, " ")
            .replace(restOfLine, " ")
            .replace(nag, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(movetext.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
