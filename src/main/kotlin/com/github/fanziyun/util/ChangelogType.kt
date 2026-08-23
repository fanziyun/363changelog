package com.github.fanziyun.util

/**
 * 更新类型：图标、颜色与翻译键的唯一来源。
 *
 * JSON 的 `type` 字段可以写英文 id（`major`/`minor`/…），也可以写中文别名；
 * 无法识别的值会被当作自定义标签，使用 [UNKNOWN_COLOR] 与 [UNKNOWN_ICON]。
 */
enum class ChangelogType(
    val id: String,
    val color: Int,
    val icon: String,
    private val alias: String,
) {
    MAJOR("major", 0xFF5555FF.toInt(), "★", "重大更新"),  // ★
    MINOR("minor", 0xFF55FF55.toInt(), "●", "功能更新"),  // ●
    PATCH("patch", 0xFFFFFF55.toInt(), "○", "修复补丁"),  // ○
    HOTFIX("hotfix", 0xFFFF5555.toInt(), "◆", "热修复"),  // ◆
    DANGER("danger", 0xFFFF5555.toInt(), "⚠", "危险更新"); // ⚠

    /** 对应 lang 文件中的翻译键 */
    val translationKey: String get() = "changelog363.type.$id"

    companion object {
        val DEFAULT = PATCH

        const val UNKNOWN_COLOR = 0xFF888888.toInt()
        const val UNKNOWN_ICON = "•" // •

        private val BY_KEY: Map<String, ChangelogType> = entries
            .flatMap { type -> listOf(type.id, type.alias).map { it.lowercase() to type } }
            .toMap()

        /** 解析类型 id 或中文别名，无法识别时返回 null。 */
        fun of(key: String?): ChangelogType? = key?.let { BY_KEY[it.trim().lowercase()] }
    }
}
