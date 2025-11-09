package me.anno.regex

fun interface Condition {
    fun test(char: Char): Boolean

    companion object {
        val TRUE = Condition { true }
    }
}