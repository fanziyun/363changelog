package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ChangelogDetailScreen(
    private val entry: ChangelogEntry,
    private val parentScreen: Screen?,
) : Screen(Component.literal(headline(entry))) {

    private companion object {
        const val CONTENT_LEFT = 30
        const val BADGES_TOP = 50
        const val CHANGES_TOP = 65
        const val LINE_HEIGHT = 12
        const val BOTTOM_MARGIN = 40
        const val BULLET = "• "

        fun headline(entry: ChangelogEntry): String =
            listOf(entry.version, entry.title).filter(String::isNotBlank).joinToString(" - ")
    }

    private class Line(val text: String, val indent: Int)

    private var lines: List<Line> = emptyList()
    private var badges: List<Badge> = emptyList()
    private var headlineText: String = ""

    override fun init() {
        super.init()
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build()
        )

        val contentWidth = width - CONTENT_LEFT * 2
        headlineText = font.ellipsize(title.string, contentWidth)
        badges = font.fitBadges(
            badgesOf(entry, ChangelogLoader.data.tagColors),
            startX = 0,
            limitX = contentWidth,
        )

        val bulletWidth = font.width(BULLET)
        val textWidth = contentWidth - bulletWidth
        lines = entry.changes.flatMap { change ->
            font.wrap(change, textWidth).mapIndexed { index, part ->
                if (index == 0) Line(BULLET + part, 0) else Line(part, bulletWidth)
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, headlineText, (width - font.width(headlineText)) / 2, 20, entry.color)

        if (entry.date.isNotBlank()) {
            val dateText = Component.translatable("screen.changelog363.date", entry.date).string
            graphics.drawString(font, dateText, (width - font.width(dateText)) / 2, 35, ColorUtil.GREY)
        }

        renderBadges(graphics, BADGES_TOP)

        val capacity = ((height - BOTTOM_MARGIN - CHANGES_TOP) / LINE_HEIGHT).coerceAtLeast(0)
        val truncated = lines.size > capacity
        val shown = if (truncated) (capacity - 1).coerceAtLeast(0) else lines.size

        var y = CHANGES_TOP
        for (index in 0 until shown) {
            val line = lines[index]
            graphics.drawString(font, line.text, CONTENT_LEFT + line.indent, y, ColorUtil.LIGHT_GREY)
            y += LINE_HEIGHT
        }

        if (truncated) {
            val more = Component.translatable("screen.changelog363.more", lines.size - shown).string
            graphics.drawString(font, font.ellipsize(more, width - CONTENT_LEFT * 2), CONTENT_LEFT, y, ColorUtil.GREY)
        }
    }

    private fun renderBadges(graphics: GuiGraphics, y: Int) {
        if (badges.isEmpty()) return
        var x = (width - font.badgeRowWidth(badges)) / 2
        for (badge in badges) x = graphics.drawBadge(font, badge, x, y)
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false
}
