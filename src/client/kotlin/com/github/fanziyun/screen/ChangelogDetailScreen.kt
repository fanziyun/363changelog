package com.github.fanziyun.screen

import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
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

        /** 标题缺失时不要留下多余的分隔符 */
        fun headline(entry: ChangelogEntry): String =
            listOf(entry.version, entry.title).filter(String::isNotBlank).joinToString(" - ")
    }

    /** 折行后的一行文本；[indent] 用于让续行对齐到项目符号之后 */
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

        // 标题同样是居中绘制的，过长时 x 会算成负数、两头都戳出屏幕
        headlineText = font.ellipsize(title.string, contentWidth)

        // 标签整排居中，总宽超出时同理，所以先按宽度截断
        badges = font.fitBadges(
            badgesOf(entry, ChangelogLoader.data.tagColors),
            startX = 0,
            limitX = contentWidth,
        )

        // 折行开销较大，只在 init（含窗口尺寸变化）时算一次
        val bulletWidth = font.width(BULLET)
        val textWidth = contentWidth - bulletWidth
        lines = entry.changes.flatMap { change ->
            font.wrap(change, textWidth).mapIndexed { index, part ->
                if (index == 0) Line(BULLET + part, 0) else Line(part, bulletWidth)
            }
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        graphics.text(font, headlineText, (width - font.width(headlineText)) / 2, 20, entry.color)

        if (entry.date.isNotBlank()) {
            val dateText = Component.translatable("screen.changelog363.date", entry.date).string
            graphics.text(font, dateText, (width - font.width(dateText)) / 2, 35, ColorUtil.GREY)
        }

        renderBadges(graphics, BADGES_TOP)

        val capacity = ((height - BOTTOM_MARGIN - CHANGES_TOP) / LINE_HEIGHT).coerceAtLeast(0)
        val truncated = lines.size > capacity
        // 放不下时留出最后一行显示"还有 N 行"
        val shown = if (truncated) (capacity - 1).coerceAtLeast(0) else lines.size

        var y = CHANGES_TOP
        for (index in 0 until shown) {
            val line = lines[index]
            graphics.text(font, line.text, CONTENT_LEFT + line.indent, y, ColorUtil.LIGHT_GREY)
            y += LINE_HEIGHT
        }
        if (truncated) {
            // 这行没有参与折行，长翻译会直接顶出右边界
            val more = Component.translatable("screen.changelog363.more", lines.size - shown).string
            graphics.text(font, font.ellipsize(more, width - CONTENT_LEFT * 2), CONTENT_LEFT, y, ColorUtil.GREY)
        }
    }

    private fun renderBadges(graphics: GuiGraphicsExtractor, y: Int) {
        if (badges.isEmpty()) return
        var x = (width - font.badgeRowWidth(badges)) / 2
        for (badge in badges) x = graphics.drawBadge(font, badge, x, y)
    }

    override fun onClose() {
        minecraft.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false
}
