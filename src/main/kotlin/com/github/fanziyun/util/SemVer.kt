package com.github.fanziyun.util

import java.math.BigInteger

object SemVer {

    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)

        if (left.isValid != right.isValid) return left.isValid.compareTo(right.isValid)
        if (!left.isValid) {
            val caseInsensitive = left.original.compareTo(right.original, ignoreCase = true)
            return if (caseInsensitive != 0) caseInsensitive else left.original.compareTo(right.original)
        }

        for (index in 0 until maxOf(left.core.size, right.core.size)) {
            val result = left.core.getOrElse(index) { BigInteger.ZERO }
                .compareTo(right.core.getOrElse(index) { BigInteger.ZERO })
            if (result != 0) return result
        }

        if (left.preRelease.isEmpty() || right.preRelease.isEmpty()) {
            return left.preRelease.isEmpty().compareTo(right.preRelease.isEmpty())
        }

        for (index in 0 until minOf(left.preRelease.size, right.preRelease.size)) {
            val result = compareIdentifier(left.preRelease[index], right.preRelease[index])
            if (result != 0) return result
        }
        return left.preRelease.size.compareTo(right.preRelease.size)
    }

    val COMPARATOR: Comparator<String> = Comparator(::compare)

    private data class Version(
        val original: String,
        val core: List<BigInteger>,
        val preRelease: List<String>,
        val isValid: Boolean,
    )

    private fun parse(version: String): Version {
        val original = version.trim()
        val normalized = original.removeVersionPrefix().substringBefore('+')
        val coreText = normalized.substringBefore('-')
        val coreParts = coreText.split('.')
        val core = coreParts.map { part -> part.takeWhile(Char::isDigit).toBigIntegerOrNull() }
        val isValid = original.isNotEmpty() && core.isNotEmpty() && core.all { it != null }
        val preRelease = normalized.substringAfter('-', missingDelimiterValue = "")
            .split('.')
            .filter(String::isNotEmpty)
        return Version(original, core.filterNotNull(), preRelease, isValid)
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumber = left.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
        val rightNumber = right.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun String.removeVersionPrefix(): String =
        if (length > 1 && (first() == 'v' || first() == 'V') && this[1].isDigit()) substring(1) else this
}
