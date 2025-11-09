package me.anno.regex

object RegexGraphBuilder {

    private data class Edge(val from: RegexNode, val to: RegexNode)

    private val precedence = mapOf(
        RegexTokenType.ALT to 1,
        RegexTokenType.CONCAT to 2,
        RegexTokenType.STAR to 3,
        RegexTokenType.PLUS to 3,
        RegexTokenType.QUESTION to 3,
        RegexTokenType.REPEAT to 3
    )

    private fun toPostfix(tokens: List<RegexToken>): List<RegexToken> {
        val output = ArrayList<RegexToken>()
        val stack = ArrayList<RegexToken>()

        for (t in tokens) {
            when (t.type) {
                RegexTokenType.LITERAL, RegexTokenType.START_ANCHOR, RegexTokenType.END_ANCHOR -> output += t
                RegexTokenType.OPEN -> stack += t
                RegexTokenType.CLOSE -> {
                    while (stack.isNotEmpty() && stack.last().type != RegexTokenType.OPEN)
                        output += stack.removeLast()
                    if (stack.isEmpty() || stack.last().type != RegexTokenType.OPEN)
                        throw IllegalArgumentException("Mismatched parentheses")
                    stack.removeLast()
                }
                RegexTokenType.ALT, RegexTokenType.CONCAT, RegexTokenType.STAR, RegexTokenType.PLUS, RegexTokenType.QUESTION, RegexTokenType.REPEAT -> {
                    while (stack.isNotEmpty() && stack.last().type != RegexTokenType.OPEN &&
                        (precedence[stack.last().type] ?: 0) >= (precedence[t.type] ?: 0)
                    ) output += stack.removeLast()
                    stack += t
                }
            }
        }

        while (stack.isNotEmpty()) {
            val op = stack.removeLast()
            if (op.type == RegexTokenType.OPEN || op.type == RegexTokenType.CLOSE)
                throw IllegalArgumentException("Mismatched parentheses")
            output += op
        }

        return output
    }


    // -------- Helpers for fragment manipulation (clone, concat, star, question) --------

    /**
     * Clone the subgraph of a fragment; returns a new fragment (fresh states)
     * */
    private fun cloneFragment(f: Edge): Edge {
        // Collect nodes reachable from f.start (should be self-contained)
        val visited = HashSet<RegexNode>()
        val stack = ArrayList<RegexNode>()
        stack += f.from
        visited += f.from
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            for (t in s.edges) {
                if (t.next !in visited) {
                    visited += t.next
                    stack += t.next
                }
            }
        }
        // Map old -> new
        val map = HashMap<RegexNode, RegexNode>()
        for (old in visited) {
            map[old] = RegexNode() // new state (not end)
        }
        // Copy transitions
        for (old in visited) {
            val new = map[old]!!
            for (t in old.edges) {
                val newNext = map[t.next] ?: continue // if a transition leads outside visited (shouldn't) skip
                new.edges += RegexEdge(newNext, t.consumeChar, t.condition)
            }
        }
        val newStart = map[f.from]!!
        val newOuts = map[f.to]!!
        return Edge(newStart, newOuts)
    }

    private fun fragConcat(a: Edge, b: Edge): Edge {
        a.to.edges += RegexEdge(b.from)
        return Edge(a.from, b.to)
    }

    private fun fragStar(e: Edge): Edge {
        val from = RegexNode()
        val to = RegexNode()
        from.edges += RegexEdge(e.from)
        from.edges += RegexEdge(to)
        e.to.edges += RegexEdge(e.from)
        e.to.edges += RegexEdge(to)
        return Edge(from, to)
    }

    private fun fragQuestion(e: Edge): Edge {
        val from = RegexNode()
        val to = RegexNode()
        from.edges += RegexEdge(e.from)
        from.edges += RegexEdge(to)
        e.to.edges += RegexEdge(to)
        return Edge(from, to)
    }

    /**
     * Build optional repetition of up to 'k' copies (k can be 0)
     * */
    private fun fragOptionalUpTo(base: Edge, times: Int): Edge {
        // if k == 0 -> epsilon fragment
        if (times == 0) {
            val s = RegexNode()
            return Edge(s, s)
        }
        var acc: Edge? = null
        repeat(times) {
            val clone = cloneFragment(base)
            val opt = fragQuestion(clone)
            acc = if (acc == null) opt else fragConcat(acc, opt)
        }
        return acc!! // k>=1 => not null
    }

    /**
     * Concatenate 'times' exact copies of base. If times == 0, return epsilon fragment.
     * */
    private fun fragExactRepeat(base: Edge, times: Int): Edge {
        if (times == 0) {
            val s = RegexNode()
            return Edge(s, s)
        }
        var acc: Edge? = null
        repeat(times) {
            val clone = cloneFragment(base)
            acc = if (acc == null) clone else fragConcat(acc, clone)
        }
        return acc!!
    }

    // -------- Build NFA from Postfix --------

    fun tokensToGraph(tokens: List<RegexToken>): RegexNode {
        val postfix = toPostfix(tokens)

        val stack = ArrayList<Edge>()

        for (token in postfix) {
            when (token.type) {
                RegexTokenType.LITERAL -> {
                    val start = RegexNode()
                    val end = RegexNode()
                    val cond = token.condition
                    start.edges += RegexEdge(end, true, cond)
                    stack += Edge(start, end)
                }
                RegexTokenType.CONCAT -> {
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    stack += fragConcat(e1, e2)
                }
                RegexTokenType.ALT -> {
                    val e2 = stack.removeLast()
                    val e1 = stack.removeLast()
                    val from = RegexNode()
                    val to = RegexNode()
                    from.edges += RegexEdge(e1.from)
                    from.edges += RegexEdge(e2.from)
                    e1.to.edges += RegexEdge(to)
                    e2.to.edges += RegexEdge(to)
                    stack += Edge(from, to)
                }
                RegexTokenType.STAR -> {
                    val e = stack.removeLast()
                    stack += fragStar(e)
                }
                RegexTokenType.PLUS -> {
                    val e = stack.removeLast()
                    // e+ == e concat e*
                    val cloneForStar = cloneFragment(e)
                    val star = fragStar(cloneForStar)
                    stack += fragConcat(e, star)
                }
                RegexTokenType.QUESTION -> {
                    val e = stack.removeLast()
                    stack += fragQuestion(e)
                }
                RegexTokenType.REPEAT -> {
                    val e = stack.removeLast()
                    val min = token.min
                    val max = token.max // null means unbounded

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
                RegexTokenType.START_ANCHOR -> {
                    val s = RegexNode()
                    s.mustBeStart = true
                    stack += Edge(s, s)
                }
                RegexTokenType.END_ANCHOR -> {
                    val s = RegexNode()
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