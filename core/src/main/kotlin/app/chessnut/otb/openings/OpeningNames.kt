package app.chessnut.otb.openings

/** The specific variation, so a long book name fits a title. "Miles Gambit" stays; "Falkbeer Countergambit Accepted" drops the trailing Accepted. */
fun shortOpening(name: String): String {
    val specific = name.substringAfterLast(": ").substringAfterLast(", ").trim().ifBlank { name }
    val base = specific.removeSuffix(" Accepted").removeSuffix(" Declined").trim()
    return if (base != specific && ("Countergambit" in base || base.length > 22)) base else specific
}
