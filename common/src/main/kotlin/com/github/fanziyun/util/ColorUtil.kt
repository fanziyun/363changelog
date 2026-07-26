package com.github.fanziyun.util

object ColorUtil {

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val GREY = 0xFFAAAAAA.toInt()
    const val LIGHT_GREY = 0xFFDDDDDD.toInt()
    const val YELLOW = 0xFFFFFF55.toInt()
    const val GREEN = 0xFF55FF55.toInt()

    /**
     * 解析颜色字符串为 ARGB 整数。
     *
     * 支持 `0xAARRGGBB`、`0xRRGGBB`、`#RRGGBB`、`#AARRGGBB` 与十进制整数；
     * 只有 6 位（无 alpha）时自动补为不透明。无法解析时返回 [defaultColor]。
     */
    fun parseColor(colorStr: String, defaultColor: Int = WHITE): Int {
        val text = colorStr.trim()
        val hex = when {
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            text.startsWith("#") -> text.substring(1)
            // 非十六进制写法：按十进制整数处理
            else -> return text.toIntOrNull()?.let(::opaque) ?: defaultColor
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

    /** 更新类型对应的颜色，未知类型返回 [ChangelogType.UNKNOWN_COLOR]。 */
    fun typeColor(type: String): Int = ChangelogType.of(type)?.color ?: ChangelogType.UNKNOWN_COLOR

    /** 更新类型对应的图标，未知类型返回 [ChangelogType.UNKNOWN_ICON]。 */
    fun typeIcon(type: String): String = ChangelogType.of(type)?.icon ?: ChangelogType.UNKNOWN_ICON

    private fun opaque(value: Int): Int = if (value in 0x000000..0xFFFFFF) BLACK or value else value
}
