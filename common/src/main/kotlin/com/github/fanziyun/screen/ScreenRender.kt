package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/** 一枚待绘制的标签：显示文本 + 背景色。 */
data class Badge(val text: String, val color: Int)

/** 11 而不是 10：文字占 8px、阴影再往下 1px，都要包在标签里 */
const val BADGE_HEIGHT = 11

private const val BADGE_PADDING = 3
private const val BADGE_GAP = 4
private const val ELLIPSIS = "..."

/** 更新类型标签，文本取自 lang 文件；未知类型原样显示。 */
fun typeBadge(type: String): Badge {
    val known = ChangelogType.of(type)
    return Badge(
        text = known?.let { Component.translatable(it.translationKey).string } ?: type,
        color = known?.color ?: ChangelogType.UNKNOWN_COLOR,
    )
}

/** 条目的全部标签：先是更新类型，再是自定义标签（颜色取自顶层 `tagColors`）。 */
fun badgesOf(entry: ChangelogEntry, tagColors: Map<String, String>): List<Badge> =
    entry.types.map(::typeBadge) + entry.tags.map { tag ->
        Badge(tag, ColorUtil.parseColor(tagColors[tag].orEmpty(), ChangelogType.UNKNOWN_COLOR))
    }

fun Font.badgeWidth(badge: Badge): Int = width(badge.text) + BADGE_PADDING * 2

/** 一整排标签的总宽度（含间隔），用于居中排布。 */
fun Font.badgeRowWidth(badges: List<Badge>): Int =
    if (badges.isEmpty()) 0 else badges.sumOf { badgeWidth(it) } + BADGE_GAP * (badges.size - 1)

/** 从 [startX] 开始依次排布，只保留右边缘不越过 [limitX] 的那些标签。 */
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

/**
 * 绘制一枚标签，返回下一枚标签的起始 x。
 *
 * 底色用标签自身的颜色，文字一律白色 —— 不再按底色亮度在黑白之间切换。
 * 白字自带的一像素深色阴影正好在浅底上勾出轮廓，亮绿、亮黄这类底色也能看清。
 */
fun GuiGraphics.drawBadge(font: Font, badge: Badge, x: Int, y: Int): Int {
    val badgeWidth = font.badgeWidth(badge)
    fill(x, y, x + badgeWidth, y + BADGE_HEIGHT, badge.color)
    drawString(font, badge.text, x + BADGE_PADDING, y + 1, ColorUtil.WHITE)
    return x + badgeWidth + BADGE_GAP
}

/**
 * 按像素宽度截断文本，超出部分用省略号代替。
 *
 * 按字符数截断（`String.take`）对中文和西文的实际宽度差异极大，所以这里按 [Font.width] 度量。
 */
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

/**
 * 按像素宽度折行。优先在空白处断开，中文等没有空格的文本按字符断开。
 *
 * 开销与文本长度成平方关系，调用方应在 `init()` 中预计算而不是每帧调用。
 */
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
                // lastSpace 是最后一个空白，其后的内容必然不含空白，可直接带到下一行
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
