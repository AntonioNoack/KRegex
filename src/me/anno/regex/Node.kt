package me.anno.regex

class Node {

    var mustBeStart: Boolean = false
    var mustBeEnd: Boolean = false
    var isEnd: Boolean = false

    val transitions = ArrayList<Transition>()

    /**
     * Careful when printing this!, can easily stack-overflow for recursive regexes.
     * */
    override fun toString(): String {
        return "{" + when {
            mustBeStart -> "^:"
            mustBeEnd && isEnd -> "\$E:"
            mustBeEnd -> "$:"
            isEnd -> "E:"
            else -> ""
        } + "${
            transitions.map {
                "${if (it.consumeChar) "c" else ""}${
                    if (it.condition.test(0.toChar())) ""
                    else {
                        (32 until 128).mapNotNull { idx ->
                            if (it.condition.test(idx.toChar())) idx.toChar()
                            else null
                        }.joinToString("", "\"", "\"/")
                    }
                }${it.next}"
            }
        }}"
    }
}