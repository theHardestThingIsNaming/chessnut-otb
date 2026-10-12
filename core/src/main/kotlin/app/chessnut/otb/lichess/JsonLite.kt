package app.chessnut.otb.lichess

/** The small slice of JSON the Lichess board stream actually sends. */
internal sealed class Json {
    data class Obj(val fields: Map<String, Json>) : Json()
    data class Arr(val items: List<Json>) : Json()
    data class Str(val text: String) : Json()
    data class Num(val raw: String) : Json()
    data class Bool(val value: Boolean) : Json()
    data object Null : Json()

    fun obj(key: String): Obj? = (this as? Obj)?.fields[key] as? Obj
    fun str(key: String): String? = ((this as? Obj)?.fields[key] as? Str)?.text
    fun bool(key: String): Boolean = ((this as? Obj)?.fields[key] as? Bool)?.value == true
    fun long(key: String): Long? = ((this as? Obj)?.fields[key] as? Num)?.raw?.toLongOrNull()
}

internal object JsonLite {
    fun parse(text: String): Json? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val reader = Reader(trimmed)
        val value = reader.value()
        return value
    }

    private class Reader(val text: String) {
        var i = 0

        fun value(): Json {
            skip()
            if (i >= text.length) throw IllegalArgumentException("Empty JSON")
            return when (val c = text[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> Json.Str(string())
                't' -> literal("true", Json.Bool(true))
                'f' -> literal("false", Json.Bool(false))
                'n' -> literal("null", Json.Null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("Bad JSON at $i")
            }
        }

        private fun obj(): Json.Obj {
            i++
            val fields = linkedMapOf<String, Json>()
            skip()
            if (peek('}')) return Json.Obj(fields)
            while (i < text.length) {
                skip()
                val key = string()
                skip()
                require(text[i] == ':') { "Expected colon" }
                i++
                fields[key] = value()
                skip()
                when (text.getOrNull(i)) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return Json.Obj(fields)
                    }
                    else -> throw IllegalArgumentException("Bad object at $i")
                }
            }
            throw IllegalArgumentException("Unclosed object")
        }

        private fun arr(): Json.Arr {
            i++
            val items = mutableListOf<Json>()
            skip()
            if (peek(']')) return Json.Arr(items)
            while (i < text.length) {
                items += value()
                skip()
                when (text.getOrNull(i)) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return Json.Arr(items)
                    }
                    else -> throw IllegalArgumentException("Bad array at $i")
                }
            }
            throw IllegalArgumentException("Unclosed array")
        }

        private fun string(): String {
            require(text[i] == '"') { "Expected string" }
            i++
            val out = StringBuilder()
            while (i < text.length) {
                val c = text[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
            throw IllegalArgumentException("Unclosed string")
        }

        private fun escape(): Char {
            if (i >= text.length) throw IllegalArgumentException("Bad escape")
            return when (val c = text[i++]) {
                '"', '\\', '/' -> c
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val hex = text.substring(i, (i + 4).coerceAtMost(text.length))
                    i += 4
                    hex.toInt(16).toChar()
                }
                else -> c
            }
        }

        private fun number(): Json.Num {
            val start = i
            if (text[i] == '-') i++
            while (i < text.length && text[i].isDigit()) i++
            if (i < text.length && text[i] == '.') {
                i++
                while (i < text.length && text[i].isDigit()) i++
            }
            if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
                i++
                if (i < text.length && (text[i] == '+' || text[i] == '-')) i++
                while (i < text.length && text[i].isDigit()) i++
            }
            return Json.Num(text.substring(start, i))
        }

        private fun literal(word: String, value: Json): Json {
            require(text.regionMatches(i, word, 0, word.length)) { "Expected $word" }
            i += word.length
            return value
        }

        private fun peek(c: Char): Boolean {
            if (text.getOrNull(i) != c) return false
            i++
            return true
        }

        private fun skip() {
            while (i < text.length && text[i].isWhitespace()) i++
        }
    }
}
