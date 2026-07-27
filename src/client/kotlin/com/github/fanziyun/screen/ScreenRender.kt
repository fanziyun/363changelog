package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

data class Badge(val text: String, val color: Int)

const val BADGE_HEIGHT = 11

private const val BADGE_PADDING = 3
private const val BADGE_GAP = 4
private const val ELLIPSIS = "..."

fun typeBadge(type: String): Badge {
    val known = ChangelogType.of(type)
    return Badge(
        text = known?.let { Component.translatable(it.translationKey).string } ?: type,
        color = known?.color ?: ChangelogType.UNKNOWN_COLOR,
    )
}

fun badgesOf(entry: ChangelogEntry, tagColors: Map<String, String>): List<Badge> =
    entry.types.map(::typeBadge) + entry.tags.map { tag ->
        Badge(tag, ColorUtil.parseColor(tagColors[tag].orEmpty(), ChangelogType.UNKNOWN_COLOR))
    }

fun Font.badgeWidth(badge: Badge): Int = width(badge.text) + BADGE_PADDING * 2

fun Font.badgeRowWidth(badges: List<Badge>): Int =
    if (badges.isEmpty()) 0 else badges.sumOf { badgeWidth(it) } + BADGE_GAP * (badges.size - 1)

fun Font.fitBadges(badges: List<Badge>, startX: Int, limitX: Int): List<Badge> {
    val fitted = mutableListOf<Badge>()
    var x = startX
    for (badge in badges) {
        val badgeWidth = badgeWidth(badge)
        if (x + badgeWidth > limitX) break
        fitted += badge
        x += badgeWidth + BADGE_GAP
    }
    return fitted
}

fun GuiGraphics.drawBadge(font: Font, badge: Badge, x: Int, y: Int): Int {
    val badgeWidth = font.badgeWidth(badge)
    fill(x, y, x + badgeWidth, y + BADGE_HEIGHT, badge.color)
    drawString(font, badge.text, x + BADGE_PADDING, y + 1, ColorUtil.WHITE)
    return x + badgeWidth + BADGE_GAP
}

fun Font.ellipsize(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (width(text) <= maxWidth) return text

    val budget = maxWidth - width(ELLIPSIS)
    if (budget <= 0) return ""

    var end = 0
    while (end < text.length) {
        val next = text.offsetByCodePoints(end, 1)
        if (width(text.substring(0, next)) > budget) break
        end = next
    }
    return if (end == 0) "" else text.substring(0, end) + ELLIPSIS
}

fun Font.wrap(text: String, maxWidth: Int): List<String> {
    if (text.isEmpty()) return listOf("")
    if (maxWidth <= 0 || width(text) <= maxWidth) return listOf(text)

    val lines = mutableListOf<String>()
    val line = StringBuilder()
    var lastSpace = -1

    var index = 0
    while (index < text.length) {
        val next = text.offsetByCodePoints(index, 1)
        val chunk = text.substring(index, next)

        if (line.isNotEmpty() && width(line.toString() + chunk) > maxWidth) {
            if (lastSpace > 0) {
                lines += line.substring(0, lastSpace)
                val carry = line.substring(lastSpace + 1)
                line.setLength(0)
                line.append(carry)
            } else {
                lines += line.toString()
                line.setLength(0)
            }
            lastSpace = -1
        }

        if (chunk.length == 1 && chunk[0].isWhitespace()) lastSpace = line.length
        line.append(chunk)
        index = next
    }

    if (line.isNotEmpty()) lines += line.toString()
    return lines
}
