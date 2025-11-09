package me.anno.regex

import java.util.regex.Pattern

fun main() {
    val input = "1end2".map { it }.joinToString("")
    val pattern = "(start|end)"
    val regex = Regex(pattern)
    val runs = 100_000
    val t0 = System.nanoTime()
    repeat(runs) {
        regex.contains(input)
    }
    val t1 = System.nanoTime()
    repeat(runs) {
        Pattern.matches(pattern, input)
    }
    val t2 = System.nanoTime()
    repeat(runs) {
        input.contains("start") || input.contains("end")
    }
    val t3 = System.nanoTime()
    println("${(t3 - t2) / 1e6f} (native) vs ${(t2 - t1) / 1e6f} (JVM) vs ${(t1 - t0) / 1e6f} (regex)")
    // -> native is 40x faster (5ms vs 34ms vs 180ms), as roughly expected
    // -> JVM is 6x slower than native, and 6x faster than KRegex
}