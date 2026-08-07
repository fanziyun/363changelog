package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
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
        if (x + badgeWidth > limitX) {
            if (fitted.isEmpty()) {
                val text = ellipsize(badge.text, limitX - x - BADGE_PADDING * 2)
                if (text.isNotEmpty()) fitted += badge.copy(text = text)
            }
            break
        }
        fitted += badge
        x += badgeWidth + BADGE_GAP
    }
    return fitted
}

fun GuiGraphicsExtractor.drawBadge(font: Font, badge: Badge, x: Int, y: Int): Int {
    val badgeWidth = font.badgeWidth(badge)
    fill(x, y, x + badgeWidth, y + BADGE_HEIGHT, badge.color)
    text(font, badge.text, x + BADGE_PADDING, y + 1, ColorUtil.WHITE)
    return x + badgeWidth + BADGE_GAP
}

fun Font.ellipsize(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (width(text) <= maxWidth) return text

    val ellipsisWidth = width(ELLIPSIS)
    if (ellipsisWidth > maxWidth) return ""
    val budget = maxWidth - ellipsisWidth
    if (budget == 0) return ELLIPSIS

    var end = 0
    while (end < text.length) {
        val next = text.offsetByCodePoints(end, 1)
        if (width(text.substring(0, next)) > budget) break
        end = next
    }
    return if (end == 0) ELLIPSIS else text.substring(0, end) + ELLIPSIS
}

fun Font.wrap(text: String, maxWidth: Int): List<String> {
    val normalized = text.trim()
    if (normalized.isEmpty()) return listOf("")
    if (maxWidth <= 0 || width(normalized) <= maxWidth) return listOf(normalized)

    val lines = mutableListOf<String>()
    var remaining = normalized
    while (remaining.isNotEmpty()) {
        if (width(remaining) <= maxWidth) {
            lines += remaining
            break
        }

        var index = 0
        var fittingEnd = 0
        var lastBreak = -1
        while (index < remaining.length) {
            val next = remaining.offsetByCodePoints(index, 1)
            if (width(remaining.substring(0, next)) > maxWidth) break
            fittingEnd = next
            if (remaining.substring(index, next).all(Char::isWhitespace)) lastBreak = next
            index = next
        }

        if (fittingEnd == 0) fittingEnd = remaining.offsetByCodePoints(0, 1)
        val breakAt = lastBreak.takeIf { it > 0 } ?: fittingEnd
        lines += remaining.substring(0, breakAt).trimEnd()
        remaining = remaining.substring(breakAt).trimStart()
    }
    return lines
}