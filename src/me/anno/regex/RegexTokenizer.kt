package me.anno.regex

object RegexTokenizer {

    // -------- Tokenizer --------

    private val IS_DIGIT = CharCondition { it in '0'..'9' }
    private val ISNT_DIGIT = CharCondition { it !in '0'..'9' }
    private val IS_LETTER = CharCondition { it.isLetterOrDigit() || it == '_' }
    private val ISNT_LETTER = CharCondition { !(it.isLetterOrDigit() || it == '_') }
    private val IS_WHITESPACE = CharCondition { it.isWhitespace() }
    private val ISNT_WHITESPACE = CharCondition { !it.isWhitespace() }
    private val ASCII_CACHE = Array(127 - 32) { idx ->
        val char = ' ' + idx
        CharCondition { it == char }
    }

    fun tokenize(pattern: CharSequence): List<RegexToken> {
        val baseTokens = parseBaseTokens(pattern)
        return addExplicitConcatTokens(baseTokens)
    }

    private fun parseBaseTokens(pat: CharSequence): List<RegexToken> {
        val tokens = ArrayList<RegexToken>()
        var i = 0
        while (i < pat.length) {
            when (val c = pat[i]) {
                '(' -> tokens += RegexToken(RegexTokenType.OPEN)
                ')' -> tokens += RegexToken(RegexTokenType.CLOSE)
                '|' -> tokens += RegexToken(RegexTokenType.ALT)
                '*' -> tokens += RegexToken(RegexTokenType.STAR)
                '+' -> tokens += RegexToken(RegexTokenType.PLUS)
                '?' -> tokens += RegexToken(RegexTokenType.QUESTION)
                '.' -> tokens += RegexToken(RegexTokenType.LITERAL)
                '^' -> tokens += RegexToken(RegexTokenType.START_ANCHOR)
                '$' -> tokens += RegexToken(RegexTokenType.END_ANCHOR)
                '[' -> {
                    val end = pat.indexOf(']', i + 1)
                    require(end > i) { "Unterminated character class" }
                    val content = pat.substring(i + 1, end)
                    tokens += RegexToken(RegexTokenType.LITERAL, parseCharClass(content))
                    i = end
                }
                '\\' -> {
                    require(i + 1 < pat.length) { "Dangling escape" }
                    val next = pat[i + 1]
                    tokens += when (next) {
                        'd' -> RegexToken(RegexTokenType.LITERAL, IS_DIGIT)
                        'D' -> RegexToken(RegexTokenType.LITERAL, ISNT_DIGIT)
                        'w' -> RegexToken(RegexTokenType.LITERAL, IS_LETTER)
                        'W' -> RegexToken(RegexTokenType.LITERAL, ISNT_LETTER)
                        's' -> RegexToken(RegexTokenType.LITERAL, IS_WHITESPACE)
                        'S' -> RegexToken(RegexTokenType.LITERAL, ISNT_WHITESPACE)
                        else -> {
                            val condition = ASCII_CACHE.getOrNull(next.code - 32) ?: CharCondition { it == next }
                            RegexToken(RegexTokenType.LITERAL, condition)
                        }
                    }
                    i++
                }
                '{' -> {
                    // parse repetition {m}, {m,}, {m,n}
                    val end = pat.indexOf('}', i + 1)
                    require(end > i) { "Unterminated repetition" }
                    val body = pat.substring(i + 1, end)
                    val parts = body.split(',')
                    val min: Int
                    val max: Int?
                    when (parts.size) {
                        1 -> {
                            min = parts[0].toInt()
                            max = min
                        }
                        2 -> {
                            min = if (parts[0].isEmpty()) 0 else parts[0].toInt()
                            max = if (parts[1].isEmpty()) null else parts[1].toInt()
                        }
                        else -> throw IllegalArgumentException("Bad repetition syntax: {$body}")
                    }
                    require(min >= 0) { "min must be >= 0" }
                    if (max != null) require(max >= min) { "max must be >= min" }
                    tokens += RegexToken(RegexTokenType.REPEAT, min, max)
                    i = end
                }
                else -> tokens += RegexToken(RegexTokenType.LITERAL) { it == c }
            }
            i++
        }
        return tokens
    }

    private fun addExplicitConcatTokens(tokens: List<RegexToken>): List<RegexToken> {
        val result = ArrayList<RegexToken>()
        for (j in tokens.indices) {
            val t1 = tokens[j]
            result += t1
            if (j + 1 < tokens.size) {
                val t2 = tokens[j + 1]
                if (shouldConcat(t1, t2)) {
                    result += RegexToken(RegexTokenType.CONCAT)
                }
            }
        }
        return result
    }

    private fun shouldConcat(t1: RegexToken, t2: RegexToken): Boolean {
        if (t1.type in setOf(
                RegexTokenType.LITERAL,
                RegexTokenType.STAR,
                RegexTokenType.PLUS,
                RegexTokenType.QUESTION,
                RegexTokenType.CLOSE,
                RegexTokenType.START_ANCHOR
            )
        ) {
            if (t2.type in setOf(RegexTokenType.LITERAL, RegexTokenType.OPEN, RegexTokenType.END_ANCHOR)) return true
        }
        // Also, a REPEAT can follow something and we might need concat after it:
        if (t1.type == RegexTokenType.REPEAT) {
            if (t2.type in setOf(RegexTokenType.LITERAL, RegexTokenType.OPEN, RegexTokenType.END_ANCHOR)) return true
        }
        return false
    }

    private fun parseCharClass(content: CharSequence): CharCondition {
        val negate = content.startsWith('^')
        val chars = HashSet<Char>()
        val ranges = ArrayList<CharRange>()
        var i = if (negate) 1 else 0
        while (i < content.length) {
            if (i + 2 < content.length && content[i + 1] == '-') {
                ranges += content[i]..content[i + 2]
                i += 3
            } else {
                chars += content[i]
                i++
            }
        }
        return CharCondition { ch ->
            val inSet = chars.contains(ch) || ranges.any { range -> ch in range }
            if (negate) !inSet else inSet
        }
    }

}