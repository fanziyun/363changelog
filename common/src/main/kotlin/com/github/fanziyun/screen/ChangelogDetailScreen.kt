package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.abs

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
        const val SCROLL_STEP = 20
        const val SCROLL_SMOOTHING = 0.25f
        const val SCROLL_BAR_WIDTH = 6
        const val SCROLL_TRACK = 0x33FFFFFF
        val SCROLL_THUMB = 0xAAFFFFFF.toInt()

        fun headline(entry: ChangelogEntry): String =
            listOf(entry.version, entry.title).filter(String::isNotBlank).joinToString(" - ")
    }

    private class Line(val text: String, val indent: Int)

    private var lines: List<Line> = emptyList()
    private var badges: List<Badge> = emptyList()
    private var headlineText = ""
    private var dateText = ""
    private var targetScroll = 0
    private var smoothScroll = 0f

    private val contentRight: Int get() = width - CONTENT_LEFT
    private val contentBottom: Int get() = height - BOTTOM_MARGIN
    private val visibleHeight: Int get() = (contentBottom - CHANGES_TOP).coerceAtLeast(0)
    private val totalContentHeight: Int get() = lines.size * LINE_HEIGHT
    private val maxScroll: Int get() = (totalContentHeight - visibleHeight).coerceAtLeast(0)
    private val scrollBarRight: Int get() = width - 18
    private val scrollBarLeft: Int get() = scrollBarRight - SCROLL_BAR_WIDTH

    override fun init() {
        super.init()
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build()
        )

        val contentWidth = (width - CONTENT_LEFT * 2).coerceAtLeast(0)
        headlineText = font.ellipsize(title.string, contentWidth)
        dateText = font.ellipsize(
            entry.date.takeIf(String::isNotBlank)
                ?.let { Component.translatable("screen.changelog363.date", it).string }
                .orEmpty(),
            contentWidth,
        )
        badges = font.fitBadges(
            badgesOf(entry, ChangelogLoader.data.tagColors),
            startX = 0,
            limitX = contentWidth,
        )

        val bulletWidth = font.width(BULLET)
        val textWidth = (contentWidth - bulletWidth - SCROLL_BAR_WIDTH - 4).coerceAtLeast(0)
        lines = entry.changes.flatMap { change ->
            font.wrap(change, textWidth).mapIndexed { index, part ->
                if (index == 0) Line(BULLET + part, 0) else Line(part, bulletWidth)
            }
        }
        clampScroll()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        updateScroll()

        graphics.drawString(font, headlineText, (width - font.width(headlineText)) / 2, 20, entry.color)
        if (dateText.isNotEmpty()) {
            graphics.drawString(font, dateText, (width - font.width(dateText)) / 2, 35, ColorUtil.GREY)
        }
        renderBadges(graphics, BADGES_TOP)
        renderChanges(graphics)
        renderScrollbar(graphics)
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun updateScroll() {
        clampScroll()
        smoothScroll += (targetScroll - smoothScroll) * SCROLL_SMOOTHING
        if (abs(smoothScroll - targetScroll) < 0.5f) smoothScroll = targetScroll.toFloat()
    }

    private fun renderBadges(graphics: GuiGraphics, y: Int) {
        if (badges.isEmpty()) return
        var x = (width - font.badgeRowWidth(badges)) / 2
        for (badge in badges) x = graphics.drawBadge(font, badge, x, y)
    }

    private fun renderChanges(graphics: GuiGraphics) {
        if (visibleHeight <= 0) return
        if (lines.isEmpty()) {
            val message = Component.translatable("screen.changelog363.no_changes").string
            val rendered = font.ellipsize(message, (contentRight - CONTENT_LEFT).coerceAtLeast(0))
            graphics.drawString(
                font,
                rendered,
                (width - font.width(rendered)) / 2,
                CHANGES_TOP + (visibleHeight - font.lineHeight) / 2,
                ColorUtil.GREY,
            )
            return
        }

        graphics.enableScissor(CONTENT_LEFT, CHANGES_TOP, contentRight, contentBottom)
        var y = CHANGES_TOP - smoothScroll.toInt()
        for (line in lines) {
            if (y > contentBottom) break
            if (y + LINE_HEIGHT >= CHANGES_TOP) {
                graphics.drawString(font, line.text, CONTENT_LEFT + line.indent, y, ColorUtil.LIGHT_GREY)
            }
            y += LINE_HEIGHT
        }
        graphics.disableScissor()
    }

    private fun renderScrollbar(graphics: GuiGraphics) {
        if (maxScroll <= 0 || visibleHeight <= 0) return
        val thumbHeight = (visibleHeight.toFloat() / totalContentHeight * visibleHeight)
            .toInt()
            .coerceIn(10.coerceAtMost(visibleHeight), visibleHeight)
        val thumbTravel = (visibleHeight - thumbHeight).coerceAtLeast(0)
        val thumbY = CHANGES_TOP + ((smoothScroll / maxScroll) * thumbTravel).toInt()
        graphics.fill(scrollBarLeft, CHANGES_TOP, scrollBarRight, contentBottom, SCROLL_TRACK)
        graphics.fill(scrollBarLeft, thumbY, scrollBarRight, thumbY + thumbHeight, SCROLL_THUMB)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (
            mouseX.toInt() in CONTENT_LEFT..contentRight &&
            mouseY.toInt() in CHANGES_TOP until contentBottom && maxScroll > 0
        ) {
            targetScroll = (targetScroll - (scrollY * SCROLL_STEP).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY)
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false

    private fun clampScroll() {
        targetScroll = targetScroll.coerceIn(0, maxScroll)
        smoothScroll = smoothScroll.coerceIn(0f, maxScroll.toFloat())
    }
}
