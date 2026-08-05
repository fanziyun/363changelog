package com.github.fanziyun.util

object ColorUtil {

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val GREY = 0xFFAAAAAA.toInt()
    const val LIGHT_GREY = 0xFFDDDDDD.toInt()
    const val YELLOW = 0xFFFFFF55.toInt()
    const val GREEN = 0xFF55FF55.toInt()

    fun parseColor(colorStr: String, defaultColor: Int = WHITE): Int {
        val text = colorStr.trim()
        val hex = when {
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            text.startsWith("#") -> text.substring(1)
            else -> return parseDecimal(text, defaultColor)
        }

        return try {
            when (hex.length) {
                6 -> BLACK or Integer.parseInt(hex, 16)
                8 -> Integer.parseUnsignedInt(hex, 16)
                else -> defaultColor
            }
        } catch (_: NumberFormatException) {
            defaultColor
        }
    }

    fun typeColor(type: String): Int = ChangelogType.of(type)?.color ?: ChangelogType.UNKNOWN_COLOR

    fun typeIcon(type: String): String = ChangelogType.of(type)?.icon ?: ChangelogType.UNKNOWN_ICON

    private fun parseDecimal(text: String, defaultColor: Int): Int {
        val value = text.toLongOrNull() ?: return defaultColor
        return when (value) {
            in 0x000000..0xFFFFFF -> BLACK or value.toInt()
            in Int.MIN_VALUE.toLong()..0xFFFFFFFFL -> value.toInt()
            else -> defaultColor
        }
    }
}
