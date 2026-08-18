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

        /** 标题缺失时不要留下多余的分隔符 */
        fun headline(entry: ChangelogEntry): String =
            listOf(entry.version, entry.title).filter(String::isNotBlank).joinToString(" - ")
    }

    /** 折行后的一行文本；[indent] 用于让续行对齐到项目符号之后 */
    private class Line(val text: String, val indent: Int)

    private var lines: List<Line> = emptyList()
    private var badges: List<Badge> = emptyList()
    private var headlineText: String = ""

    /**
     * 上次排版时见到的加载器数据版本。
     *
     * 标签颜色取自顶层 `tagColors`，而本页可能在加载还没结束时就被打开（此时是空数据）。
     * 迟到的加载完成后 dataVersion 会变，渲染帧据此重排一次，颜色才不会一直停在兜底值。
     */
    private var seenDataVersion = -1L

    override fun init() {
        super.init()
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build()
        )

        layOutContent()
    }

    /** 先取生成号再排版：反过来的话，加载恰好在两句之间完成就会记下新号却留着旧排版 */
    private fun layOutContent() {
        seenDataVersion = ChangelogLoader.dataVersion

        val contentWidth = width - CONTENT_LEFT * 2

        // 标题同样是居中绘制的，过长时 x 会算成负数、两头都戳出屏幕
        headlineText = font.ellipsize(title.string, contentWidth)

        // 标签整排居中，总宽超出时同理，所以先按宽度截断
        badges = font.fitBadges(
            badgesOf(entry, ChangelogLoader.data.tagColors),
            startX = 0,
            limitX = contentWidth,
        )

        // 折行开销较大，只在排版时算一次，不每帧重算
        val bulletWidth = font.width(BULLET)
        val textWidth = contentWidth - bulletWidth
        lines = entry.changes.flatMap { change ->
            font.wrap(change, textWidth).mapIndexed { index, part ->
                if (index == 0) Line(BULLET + part, 0) else Line(part, bulletWidth)
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (ChangelogLoader.dataVersion != seenDataVersion) layOutContent()

        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, headlineText, (width - font.width(headlineText)) / 2, 20, entry.color)

        if (entry.date.isNotBlank()) {
            val dateText = Component.translatable("screen.changelog363.date", entry.date).string
            graphics.drawString(font, dateText, (width - font.width(dateText)) / 2, 35, ColorUtil.GREY)
        }

        renderBadges(graphics, BADGES_TOP)

        val capacity = ((height - BOTTOM_MARGIN - CHANGES_TOP) / LINE_HEIGHT).coerceAtLeast(0)
        val truncated = lines.size > capacity
        // 放不下时留出最后一行显示"还有 N 行"
        val shown = if (truncated) (capacity - 1).coerceAtLeast(0) else lines.size

        var y = CHANGES_TOP
        for (index in 0 until shown) {
            val line = lines[index]
            graphics.drawString(font, line.text, CONTENT_LEFT + line.indent, y, ColorUtil.LIGHT_GREY)
            y += LINE_HEIGHT
        }
        if (truncated) {
            // 这行没有参与折行，长翻译会直接顶出右边界
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
