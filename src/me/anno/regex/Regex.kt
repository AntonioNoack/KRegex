package me.anno.regex

data class Regex(val pattern: CharSequence) {

    val startState = run {
        val tokens = RegexTokenizer.tokenize(pattern.ifEmpty { "^$" })
        RegexGraphBuilder.tokensToGraph(tokens)
    }

    /**
     * Exact matches only.
     * $ and ^ match input[0] and input\[input.length], not input\[start]/input\[end]
     * */
    fun matches(input: CharSequence, start: Int = 0, end: Int = input.length): Boolean {
        var current = findSiblingStates(startState, start == 0, start == input.length)
        for (pos in start until end) {
            val nextStates = HashSet<RegexNode>()
            for (s in current) {
                for (t in s.edges) {
                    if (t.consumeChar && t.condition.test(input[pos])) {
                        nextStates += findSiblingStates(t.next, pos == 0, pos + 1 == input.length)
                    }
                }
            }
            current = nextStates
        }
        return current.any { it.isEnd }
    }

    /**
     * Checks whether any match exists between start and end.
     * $ and ^ match input[0] and input\[input.length], not input\[start]/input\[end]
     * */
    fun contains(input: CharSequence, start: Int = 0, end: Int = input.length): Boolean {
        return forEachNonOverlappingMatch(input, start, end) { _, _ -> true }
    }

    private fun findSiblingStates(state: RegexNode, isStart: Boolean, isEnd: Boolean): Set<RegexNode> {
        if (!isStart && state.mustBeStart) return emptySet()
        if (!isEnd && state.mustBeEnd) return emptySet()

        val found = HashSet<RegexNode>().apply { add(state) }
        val todo = ArrayList<RegexNode>().apply { add(state) }
        while (todo.isNotEmpty()) {
            val s = todo.removeLast()
            for (t in s.edges) {
                if (!t.consumeChar &&
                    (isStart || !t.next.mustBeStart) &&
                    (isEnd || !t.next.mustBeEnd) &&
                    found.add(t.next)
                ) {
                    todo.add(t.next)
                }
            }
        }

        return found
    }

    private fun addStartsAt(input: CharSequence, p: Int, target: HashMap<RegexNode, HashSet<Int>>) {
        val startClosure = findSiblingStates(startState, p == 0, p + 1 == input.length)
        for (s in startClosure) {
            target.getOrPut(s) { HashSet() }.add(p)
        }
    }

    /**
     * when callback returns true, we exit early
     * returns number of matches
     * */
    fun forEachMatch(
        input: CharSequence,
        callback: RegexCallback
    ) = forEachMatch(input, 0, input.length, callback)

    /**
     * when callback returns true, we exit early
     * returns number of matches
     *
     * todo bug: doesn't find best starts only
     * */
    fun forEachMatch(
        input: CharSequence, start: Int, end: Int,
        callback: RegexCallback
    ): Boolean {
        var matchCount = 0

        // Map from NFA state to set of starting positions
        var current: HashMap<RegexNode, HashSet<Int>> = HashMap()

        // Initially allow matches to start at position 0
        addStartsAt(input, 0, current)

        // Map from start position to its current longest match end
        val longestMatchEnds: HashMap<Int, Int> = HashMap()

        for (pos in start until end) {
            val ch = input[pos]
            val next = HashMap<RegexNode, HashSet<Int>>()

            // Advance all active NFA states
            for ((state, starts) in current) {
                for (t in state.edges) {
                    if (t.consumeChar && t.condition.test(ch)) {
                        val closure = findSiblingStates(t.next, pos == 0, pos + 1 == input.length)
                        for (s in closure) {
                            val targetStarts = next.getOrPut(s) { HashSet() }
                            targetStarts.addAll(starts)
                        }
                    }
                }
            }

            // Update the longest end for any start positions that reach an accepting state
            for ((state, starts) in next) {
                if (state.isEnd) {
                    for (start in starts) {
                        longestMatchEnds[start] = pos + 1
                    }
                }
            }

            // Prepare for next position: allow new matches to start
            addStartsAt(input, pos + 1, next)

            current = next
        }

        // After scanning the entire input, report all longest matches
        for ((start, end) in longestMatchEnds) {
            matchCount++
            if (callback.call(start, end)) return true
        }

        return false
    }

    /**
     * when callback returns true, we exit early
     * returns number of matches
     * */
    fun forEachNonOverlappingMatch(
        input: CharSequence,
        callback: RegexCallback
    ) = forEachNonOverlappingMatch(input, 0, input.length, callback)

    /**
     * when callback returns true, we exit early
     * returns number of matches
     * */
    fun forEachNonOverlappingMatch(
        input: CharSequence, start: Int, end: Int,
        callback: RegexCallback
    ): Boolean {
        var matchCount = 0
        var pos = start

        while (pos < end) {
            var current = HashMap<RegexNode, HashSet<Int>>()

            // Initialize NFA from this start position
            addStartsAt(input, pos, current)

            var longestEnd: Int = -1
            var i = pos

            // Advance NFA until no states remain
            while (i < end && current.isNotEmpty()) {
                val ch = input[i]
                val next = HashMap<RegexNode, HashSet<Int>>()

                // Propagate transitions
                for ((state, startSet) in current) {
                    for (t in state.edges) {
                        if (t.consumeChar && t.condition.test(ch)) {
                            val closure = findSiblingStates(t.next, i == 0, i + 1 == input.length)
                            for (s in closure) {
                                next.getOrPut(s) { HashSet() }
                                    .addAll(startSet)
                            }
                        }
                    }
                }

                // Check for end states and record the longest match seen so far
                for ((state, starts) in next) {
                    if (state.isEnd && starts.isNotEmpty()) {
                        val earliestStart = starts.min()
                        if (earliestStart == pos) {
                            longestEnd = i + 1  // extend the match
                        }
                    }
                }

                current = next
                i++
            }

            if (longestEnd != -1) {
                matchCount++
                if (callback.call(pos, longestEnd)) return true
                pos = longestEnd  // skip past this match for non-overlapping
            } else {
                pos++
            }
        }

        return false
    }
}
