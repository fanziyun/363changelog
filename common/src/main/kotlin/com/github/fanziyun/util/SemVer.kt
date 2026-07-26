package com.github.fanziyun.util

/**
 * 宽松的语义化版本比较。
 *
 * 按 `.` 拆分，每段只取前导数字（`1.2.0-beta` → `[1, 2, 0]`），缺失的段补 0，
 * 因此 `1.2` 与 `1.2.0` 相等、`1.10.0` 大于 `1.9.0`。
 */
object SemVer {

    /** [a] 大于 [b] 返回正数，小于返回负数，相等返回 0。 */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            // 用 compareTo 而不是相减，避免大版本号相减溢出
            val result = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
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
