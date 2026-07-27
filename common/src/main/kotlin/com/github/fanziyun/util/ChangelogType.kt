package com.github.fanziyun.util

enum class ChangelogType(
    val id: String,
    val color: Int,
    val icon: String,
    private val alias: String,
) {
    MAJOR("major", 0xFF5555FF.toInt(), "★", "重大更新"),
    MINOR("minor", 0xFF55FF55.toInt(), "●", "功能更新"),
    PATCH("patch", 0xFFFFFF55.toInt(), "○", "修复补丁"),
    HOTFIX("hotfix", 0xFFFF5555.toInt(), "◆", "热修复"),
    DANGER("danger", 0xFFFF5555.toInt(), "⚠", "危险更新");

    val translationKey: String get() = "changelog363.type.$id"

    companion object {
        val DEFAULT = PATCH

        const val UNKNOWN_COLOR = 0xFF888888.toInt()
        const val UNKNOWN_ICON = "•"

        private val byKey: Map<String, ChangelogType> = entries
            .flatMap { type -> listOf(type.id, type.alias).map { it.lowercase() to type } }
            .toMap()

        fun of(key: String?): ChangelogType? = key?.let { byKey[it.trim().lowercase()] }
    }
}
