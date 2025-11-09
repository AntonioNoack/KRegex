package me.anno.regex

fun interface RegexCallback {
    /**
     * returns whether the operation is finished
     * */
    fun call(startIndex: Int, endIndex: Int): Boolean
}