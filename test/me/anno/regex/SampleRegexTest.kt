package me.anno.regex

fun main() {

    val regex = Regex("([A-Z][a-z]+|dog|cat)+[0-9]*")

    println(regex.matches("JohnCat9"))  // true
    println(regex.matches("dogcat"))    // true
    println(regex.matches("DogCat123")) // true
    println(regex.matches("dogCAT"))    // false (case sensitive)
    println(regex.matches("catdog1x"))  // false

    val input = "JohnCat9 DogCat123 dogcat"
    println("Overlapping matches:")
    regex.forEachMatch(input) { s, e ->
        println("Match: '${input.substring(s, e)}' at [$s,$e)")
        false
    }

    println("Non-overlapping matches:")
    regex.forEachNonOverlappingMatch(input) { s, e ->
        println("Match: '${input.substring(s, e)}' at [$s,$e)")
        false
    }

    println(setOf(' ', '\t', '\r', '\n', '\u000C').map { it.isWhitespace() })
}