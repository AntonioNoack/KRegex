package me.anno.regex

data class RegexToken(
    val type: RegexTokenType,
    val condition: CharCondition = CharCondition.TRUE,
    val min: Int, val max: Int?,
) {
    constructor(type: RegexTokenType) : this(type, CharCondition.TRUE, 0, null)
    constructor(type: RegexTokenType, condition: CharCondition) : this(type, condition, 0, null)
    constructor(type: RegexTokenType, min: Int, max: Int?) : this(type, CharCondition.TRUE, min, max)
}