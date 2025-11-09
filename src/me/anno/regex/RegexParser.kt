package me.anno.regex

object RegexParser {

    private val TRUE = Condition { _: Char -> true }

    data class Edge(val from: Node, val to: Node)

    // -------- Tokenizer --------

    private enum class TokenType {
        LITERAL, CONCAT, ALT, STAR, PLUS, QUESTION,
        OPEN, CLOSE,
        START_ANCHOR, END_ANCHOR,
        REPEAT
    }

    private data class Token(
        val type: TokenType,
        val condition: Condition = TRUE,
        val min: Int, val max: Int?,
    ) {
        constructor(type: TokenType) : this(type, TRUE, 0, null)
        constructor(type: TokenType, condition: Condition) : this(type, condition, 0, null)
        constructor(type: TokenType, min: Int, max: Int?) : this(type, TRUE, min, max)
    }

    private fun tokenize(pat: CharSequence): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < pat.length) {
            when (val c = pat[i]) {
                '(' -> tokens += Token(TokenType.OPEN)
                ')' -> tokens += Token(TokenType.CLOSE)
                '|' -> tokens += Token(TokenType.ALT)
                '*' -> tokens += Token(TokenType.STAR)
                '+' -> tokens += Token(TokenType.PLUS)
                '?' -> tokens += Token(TokenType.QUESTION)
                '.' -> tokens += Token(TokenType.LITERAL)
                '^' -> tokens += Token(TokenType.START_ANCHOR)
                '$' -> tokens += Token(TokenType.END_ANCHOR)
                '[' -> {
                    val end = pat.indexOf(']', i + 1)
                    require(end > i) { "Unterminated character class" }
                    val content = pat.substring(i + 1, end)
                    tokens += Token(TokenType.LITERAL, parseCharClass(content))
                    i = end
                }
                '\\' -> {
                    require(i + 1 < pat.length) { "Dangling escape" }
                    val next = pat[i + 1]
                    tokens += when (next) {
                        'd' -> Token(TokenType.LITERAL) { it in '0'..'9' }
                        'D' -> Token(TokenType.LITERAL) { it !in '0'..'9' }
                        'w' -> Token(TokenType.LITERAL) { it.isLetterOrDigit() || it == '_' }
                        'W' -> Token(TokenType.LITERAL) { !(it.isLetterOrDigit() || it == '_') }
                        's' -> Token(TokenType.LITERAL) { it in setOf(' ', '\t', '\r', '\n', '\u000C') }
                        'S' -> Token(TokenType.LITERAL) { it !in setOf(' ', '\t', '\r', '\n', '\u000C') }
                        else -> Token(TokenType.LITERAL) { it == next }
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
                    tokens += Token(TokenType.REPEAT, min, max)
                    i = end
                }
                else -> tokens += Token(TokenType.LITERAL) { it == c }
            }
            i++
        }

        // Add explicit CONCAT tokens
        val result = ArrayList<Token>()
        for (j in tokens.indices) {
            val t1 = tokens[j]
            result += t1
            if (j + 1 < tokens.size) {
                val t2 = tokens[j + 1]
                if (shouldConcat(t1, t2)) {
                    result += Token(TokenType.CONCAT)
                }
            }
        }
        return result
    }

    private fun shouldConcat(t1: Token, t2: Token): Boolean {
        if (t1.type in setOf(TokenType.LITERAL, TokenType.STAR, TokenType.PLUS, TokenType.QUESTION, TokenType.CLOSE, TokenType.START_ANCHOR)) {
            if (t2.type in setOf(TokenType.LITERAL, TokenType.OPEN, TokenType.END_ANCHOR)) return true
        }
        // Also, a REPEAT can follow something and we might need concat after it:
        if (t1.type == TokenType.REPEAT) {
            if (t2.type in setOf(TokenType.LITERAL, TokenType.OPEN, TokenType.END_ANCHOR)) return true
        }
        return false
    }

    private fun parseCharClass(content: CharSequence): Condition {
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
        return Condition { ch ->
            val inSet = chars.contains(ch) || ranges.any { range -> ch in range }
            if (negate) !inSet else inSet
        }
    }

    // -------- Infix → Postfix (Shunting-Yard) --------

    private val precedence = mapOf(
        TokenType.ALT to 1,
        TokenType.CONCAT to 2,
        TokenType.STAR to 3,
        TokenType.PLUS to 3,
        TokenType.QUESTION to 3,
        TokenType.REPEAT to 3
    )

    private fun toPostfix(tokens: List<Token>): List<Token> {
        val output = ArrayList<Token>()
        val stack = ArrayList<Token>()

        for (t in tokens) {
            when (t.type) {
                TokenType.LITERAL, TokenType.START_ANCHOR, TokenType.END_ANCHOR -> output += t
                TokenType.OPEN -> stack += t
                TokenType.CLOSE -> {
                    while (stack.isNotEmpty() && stack.last().type != TokenType.OPEN)
                        output += stack.removeLast()
                    if (stack.isEmpty() || stack.last().type != TokenType.OPEN)
                        throw IllegalArgumentException("Mismatched parentheses")
                    stack.removeLast()
                }
                TokenType.ALT, TokenType.CONCAT, TokenType.STAR, TokenType.PLUS, TokenType.QUESTION, TokenType.REPEAT -> {
                    while (stack.isNotEmpty() && stack.last().type != TokenType.OPEN &&
                        (precedence[stack.last().type] ?: 0) >= (precedence[t.type] ?: 0)
                    ) output += stack.removeLast()
                    stack += t
                }
            }
        }

        while (stack.isNotEmpty()) {
            val op = stack.removeLast()
            if (op.type == TokenType.OPEN || op.type == TokenType.CLOSE)
                throw IllegalArgumentException("Mismatched parentheses")
            output += op
        }

        return output
    }


    // -------- Helpers for fragment manipulation (clone, concat, star, question) --------

    // Clone the subgraph of a fragment; returns a new fragment (fresh states)
    private fun cloneFragment(f: Edge): Edge {
        // Collect nodes reachable from f.start (should be self-contained)
        val visited = HashSet<Node>()
        val stack = ArrayList<Node>()
        stack += f.from
        visited += f.from
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            for (t in s.transitions) {
                if (t.next !in visited) {
                    visited += t.next
                    stack += t.next
                }
            }
        }
        // Map old -> new
        val map = HashMap<Node, Node>()
        for (old in visited) {
            map[old] = Node() // new state (not end)
        }
        // Copy transitions
        for (old in visited) {
            val new = map[old]!!
            for (t in old.transitions) {
                val newNext = map[t.next] ?: continue // if a transition leads outside visited (shouldn't) skip
                new.transitions += Transition(newNext, t.consumeChar, t.condition)
            }
        }
        val newStart = map[f.from]!!
        val newOuts = map[f.to]!!
        return Edge(newStart, newOuts)
    }

    private fun fragConcat(a: Edge, b: Edge): Edge {
        a.to.transitions += Transition(b.from)
        return Edge(a.from, b.to)
    }

    private fun fragStar(e: Edge): Edge {
        val start = Node()
        val out = Node()
        start.transitions += Transition(e.from)
        start.transitions += Transition(out)
        e.to.transitions += Transition(e.from)
        e.to.transitions += Transition(out)
        return Edge(start, out)
    }

    private fun fragQuestion(e: Edge): Edge {
        val start = Node()
        val out = Node()
        start.transitions += Transition(e.from)
        start.transitions += Transition(out)
        e.to.transitions += Transition(out)
        return Edge(start, out)
    }

    // Build optional repetition of up to 'k' copies (k can be 0)
    private fun fragOptionalUpTo(base: Edge, k: Int): Edge {
        // if k == 0 -> epsilon fragment
        if (k == 0) {
            val s = Node()
            return Edge(s, s)
        }
        var acc: Edge? = null
        for (i in 1..k) {
            val clone = cloneFragment(base)
            val opt = fragQuestion(clone)
            acc = if (acc == null) opt else fragConcat(acc, opt)
        }
        return acc!! // k>=1 => not null
    }

    // Concatenate 'times' exact copies of base. If times == 0, return epsilon fragment.
    private fun fragExactRepeat(base: Edge, times: Int): Edge {
        if (times == 0) {
            val s = Node()
            return Edge(s, s)
        }
        var acc: Edge? = null
        for (i in 1..times) {
            val clone = cloneFragment(base)
            acc = if (acc == null) clone else fragConcat(acc, clone)
        }
        return acc!!
    }

    // -------- Build NFA from Postfix --------

    fun parsePattern(pattern: CharSequence): Node {
        val tokens = tokenize(pattern)
        val postfix = toPostfix(tokens)

        val stack = ArrayList<Edge>()

        for (t in postfix) {
            when (t.type) {
                TokenType.LITERAL -> {
                    val start = Node()
                    val end = Node()
                    val cond = t.condition
                    start.transitions += Transition(end, true, cond)
                    stack += Edge(start, end)
                }
                TokenType.CONCAT -> {
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    stack += fragConcat(e1, e2)
                }
                TokenType.ALT -> {
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    val start = Node()
                    val out = Node()
                    start.transitions += Transition(e1.from)
                    start.transitions += Transition(e2.from)
                    e1.to.transitions += Transition(out)
                    e2.to.transitions += Transition(out)
                    stack += Edge(start, out)
                }
                TokenType.STAR -> {
                    val e = stack.removeLast()
                    stack += fragStar(e)
                }
                TokenType.PLUS -> {
                    val e = stack.removeLast()
                    // e+ == e concat e*
                    val cloneForStar = cloneFragment(e)
                    val star = fragStar(cloneForStar)
                    stack += fragConcat(e, star)
                }
                TokenType.QUESTION -> {
                    val e = stack.removeLast()
                    stack += fragQuestion(e)
                }
                TokenType.REPEAT -> {
                    val e = stack.removeLast()
                    val min = t.min
                    val max = t.max // null means unbounded

                    // exact required copies:
                    val required = fragExactRepeat(e, min)

                    val resultFrag: Edge = if (max == null) {
                        // {m,} => required concatenated with (e)*
                        val cloneForStar = cloneFragment(e)
                        val star = fragStar(cloneForStar)
                        fragConcat(required, star)
                    } else {
                        // finite upper bound: {m,n} where n >= m
                        val optionalCount = max - min
                        val optionalFrag = fragOptionalUpTo(e, optionalCount)
                        fragConcat(required, optionalFrag)
                    }
                    stack += resultFrag
                }
                TokenType.START_ANCHOR -> {
                    val s = Node()
                    s.mustBeStart = true
                    stack += Edge(s, s)
                }
                TokenType.END_ANCHOR -> {
                    val s = Node()
                    s.mustBeEnd = true
                    stack += Edge(s, s)
                }
                else -> {
                    // parentheses handled earlier by shunting-yard
                }
            }
        }

        require(stack.isNotEmpty()) { "Invalid pattern — empty postfix stack" }
        val e = stack.removeLast()
        e.to.isEnd = true
        return e.from
    }

}