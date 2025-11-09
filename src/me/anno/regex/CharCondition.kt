package me.anno.regex

fun interface CharCondition {
    fun test(char: Char): Boolean

    companion object {
        val TRUE = CharCondition { true }
    }
}