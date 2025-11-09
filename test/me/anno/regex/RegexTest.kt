package me.anno.regex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegexTest {
    @Test
    fun testLiteralMatch() {
        val regex = Regex("abc")
        assertTrue(regex.matches("abc"))
        assertFalse(regex.matches("abcd"))
        assertFalse(regex.matches("ab"))
    }

    @Test
    fun testStartEndAnchors() {
        val regex = Regex("^abc$")
        println(regex.matches("a"))
        println(regex.matches("b"))
        println(regex.matches("c"))
        assertTrue(regex.matches("abc"))
        assertFalse(regex.matches(" abc"))
        assertFalse(regex.matches("abc "))
    }

    @Test
    fun testAlternation() {
        val regex = Regex("cat|dog")
        assertTrue(regex.matches("cat"))
        assertTrue(regex.matches("dog"))
        assertFalse(regex.matches("cow"))
    }

    @Test
    fun testQuantifiersPlus() {
        val regex = Regex("a+")
        assertTrue(regex.matches("a"))
        assertTrue(regex.matches("aaaa"))
        assertFalse(regex.matches(""))
    }

    @Test
    fun testQuantifiersOptional() {
        val regex = Regex("a?")
        assertTrue(regex.matches(""))
        assertTrue(regex.matches("a"))
        assertFalse(regex.matches("aa"))
    }

    @Test
    fun testQuantifiersBracesExact() {
        val regex = Regex("a{3}")
        assertTrue(regex.matches("aaa"))
        assertFalse(regex.matches("aa"))
        assertFalse(regex.matches("aaaa"))
    }

    @Test
    fun testQuantifiersBracesRange() {
        val regex = Regex("a{2,4}")
        assertFalse(regex.matches("a"))
        assertTrue(regex.matches("aa"))
        assertTrue(regex.matches("aaa"))
        assertTrue(regex.matches("aaaa"))
        assertFalse(regex.matches("aaaaa"))
    }

    @Test
    fun testForEachMatchOverlapping() {
        val regex = Regex("a+")
        val input = "aaabaa"
        val results = mutableListOf<Pair<Int, Int>>()
        var count = 0
        regex.forEachMatch(input) { start, end ->
            println("found match ${input.substring(start, end)} at [$start,$end)")
            results.add(start to end)
            count++
            false
        }
        assertEquals(2, count)
        assertEquals(listOf(0 to 3, 4 to 6), results)
    }

    @Test
    fun testForEachNonOverlappingMatch() {
        val regex = Regex("a+")
        val input = "aaabaa"
        val results = mutableListOf<Pair<Int, Int>>()
        var count = 0
        regex.forEachNonOverlappingMatch(input) { start, end ->
            println("found match ${input.substring(start, end)} at [$start,$end)")
            results.add(start to end)
            count++
            false
        }
        assertEquals(2, count)
        assertEquals(listOf(0 to 3, 4 to 6), results)
    }

    @Test
    fun testCallbackEarlyStop() {
        val regex = Regex("a+")
        val input = "aaabaa"
        val results = mutableListOf<Pair<Int, Int>>()
        var count = 0
        regex.forEachMatch(input) { start, end ->
            println("found match ${input.substring(start, end)} at [$start,$end)")
            results.add(start to end)
            count++
            true // stop after first match
        }
        assertEquals(1, count)
        assertEquals(listOf(0 to 3), results)
    }

    @Test
    fun testComplexPattern() {
        val regex = Regex("^(cat|dog){2,3}a?$")
        assertTrue(regex.matches("catcat"))
        assertTrue(regex.matches("catdoga"))
        assertFalse(regex.matches("catdogdogdog"))
        assertFalse(regex.matches("cat"))
    }

    @Test
    fun testEdgeCasesEmptyString() {
        val regex = Regex("")
        assertTrue(regex.matches(""))
        assertFalse(regex.matches("a"))
    }

    @Test
    fun testEdgeCasesAnchorsOnly() {
        val regex = Regex("^$")
        assertTrue(regex.matches(""))
        assertFalse(regex.matches("a"))
    }

    @Test
    fun testDogCat() {
        val regex = Regex("([A-Z][a-z]+|dog|cat)+[0-9]*")
        assertTrue(regex.matches("JohnCat9"))
        assertTrue(regex.matches("dogcat"))
        assertTrue(regex.matches("DogCat123"))
        assertFalse(regex.matches("dogCAT"))
        assertFalse(regex.matches("catdog1x"))
    }
}