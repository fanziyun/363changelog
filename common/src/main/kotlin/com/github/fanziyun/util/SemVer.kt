package com.github.fanziyun.util

object SemVer {

    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)

        for (index in 0 until maxOf(left.size, right.size)) {
            val result = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (result != 0) return result
        }

        return 0
    }

    val COMPARATOR: Comparator<String> = Comparator(::compare)

    private fun segments(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
