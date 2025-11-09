package me.anno.regex

data class Transition(
    val next: Node,
    val consumeChar: Boolean = false,
    val condition: Condition = Condition.TRUE
)