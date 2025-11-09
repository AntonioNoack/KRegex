package me.anno.regex

data class RegexEdge(
    val next: RegexNode,
    val consumeChar: Boolean = false,
    val condition: CharCondition = CharCondition.TRUE
)