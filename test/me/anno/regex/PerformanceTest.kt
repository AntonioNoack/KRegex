package me.anno.regex

fun main() {
    val input = "1end2".map { it }.joinToString("")
    val regex = Regex("(start|end)")
    val runs = 100_000
    val t0 = System.nanoTime()
    repeat(runs) {
        regex.contains(input)
    }
    val t1 = System.nanoTime()
    repeat(runs) {
        input.contains("start") || input.contains("end")
    }
    val t2 = System.nanoTime()
    println("${(t2 - t1) / 1e6f} (native) vs ${(t1 - t0) / 1e6f} (regex)")
    // -> native is 50x faster, as roughly expected
}