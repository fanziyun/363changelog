package com.github.fanziyun.util

import java.math.BigInteger

/**
 * 宽松的语义化版本比较。
 *
 * 支持可选的 `v` 前缀、缺失的核心版本段与 SemVer 预发布标识符；构建元数据不参与比较。
 * 因此 `1.2` 与 `1.2.0` 相等，`1.2.0-beta` 小于 `1.2.0`，
 * `1.2.0-beta.10` 大于 `1.2.0-beta.2`。
 */
object SemVer {

    /** [leftVersion] 大于 [rightVersion] 返回正数，小于返回负数，相等返回 0。 */
    fun compare(leftVersion: String, rightVersion: String): Int {
        val left = parse(leftVersion)
        val right = parse(rightVersion)
        for (index in 0 until maxOf(left.core.size, right.core.size)) {
            val result = left.core.getOrElse(index) { BigInteger.ZERO }
                .compareTo(right.core.getOrElse(index) { BigInteger.ZERO })
            if (result != 0) return result
        }
        return comparePreRelease(left.preRelease, right.preRelease)
    }

    val COMPARATOR: Comparator<String> = Comparator(::compare)

    private fun comparePreRelease(left: List<String>?, right: List<String>?): Int {
        if (left == null) return if (right == null) 0 else 1
        if (right == null) return -1

        for (index in 0 until minOf(left.size, right.size)) {
            val result = compareIdentifier(left[index], right[index])
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumber = left.numericIdentifierOrNull()
        val rightNumber = right.numericIdentifierOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun String.numericIdentifierOrNull(): BigInteger? =
        takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let(::BigInteger)

    private fun parse(version: String): ParsedVersion {
        val normalized = version.trim().removePrefix("v").removePrefix("V")
        val withoutBuildMetadata = normalized.substringBefore('+')
        val preReleaseSeparator = withoutBuildMetadata.indexOf('-')
        val coreText = if (preReleaseSeparator >= 0) {
            withoutBuildMetadata.substring(0, preReleaseSeparator)
        } else {
            withoutBuildMetadata
        }
        val preRelease = if (preReleaseSeparator >= 0) {
            withoutBuildMetadata.substring(preReleaseSeparator + 1).split('.')
        } else {
            null
        }
        val core = coreText.split('.').map { segment ->
            segment.takeWhile(Char::isDigit)
                .takeIf(String::isNotEmpty)
                ?.let(::BigInteger)
                ?: BigInteger.ZERO
        }
        return ParsedVersion(core, preRelease)
    }

    private data class ParsedVersion(
        val core: List<BigInteger>,
        val preRelease: List<String>?,
    )
}
