package me.anno.regex

enum class RegexTokenType {
    LITERAL, CONCAT, // any letter, and a helper
    ALT, STAR, PLUS, QUESTION, // |*+?
    OPEN, CLOSE, // ()
    START_ANCHOR, END_ANCHOR, // ^$
    REPEAT // {1,3}
}