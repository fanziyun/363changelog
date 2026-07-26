package com.github.fanziyun.screen

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.ChangelogEntry
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.net.URI
import kotlin.math.abs

class ChangelogOverviewScreen(private val parentScreen: Screen?) :
    Screen(Component.translatable("screen.changelog363.title")) {

    private companion object {
        const val SLOT_HEIGHT = 50
        const val LIST_LEFT = 20
        const val LIST_TOP = 55
        const val TEXT_LEFT = 32

        /** 磁贴内容与磁贴右边缘之间的留白，取值与左侧留白（TEXT_LEFT - LIST_LEFT）一致 */
        const val ROW_PADDING = TEXT_LEFT - LIST_LEFT

        /** 类型标签与右侧日期之间至少留出的间隔 */
        const val GAP_BEFORE_DATE = 4

        /** 版本号与第一个类型标签之间的间隔 */
        const val GAP_AFTER_VERSION = 6

        const val SCROLL_BAR_WIDTH = 6
        const val SCROLL_STEP = 20
        const val SCROLL_SMOOTHING = 0.25f

        const val ROW_BACKGROUND = 0x1AFFFFFF
        const val ROW_HOVERED = 0x33FFFFFF
        const val DIVIDER = 0x44FFFFFF
        const val SCROLL_TRACK = 0x33FFFFFF
        val SCROLL_THUMB = 0xAAFFFFFF.toInt()
    }

    /** 预先测量并排布好的一行内容，避免每帧重复计算文本宽度。 */
    private class Row(
        val entry: ChangelogEntry,
        val versionText: String,
        val versionColor: Int,
        val badges: List<Badge>,
        val badgeX: Int,
        val date: String,
        val dateX: Int,
        val title: String,
        val summary: String,
    )

    private var rows: List<Row> = emptyList()
    private var targetScroll = 0
    private var smoothScroll = 0f
    private var hoveredIndex = -1

    private val listRight: Int get() = width - 30
    private val listBottom: Int get() = height - 60
    private val scrollBarRight: Int get() = width - 10
    private val scrollBarLeft: Int get() = scrollBarRight - SCROLL_BAR_WIDTH

    private val visibleHeight: Int get() = listBottom - LIST_TOP
    private val totalContentHeight: Int get() = rows.size * SLOT_HEIGHT
    private val maxScroll: Int get() = (totalContentHeight - visibleHeight).coerceAtLeast(0)

    override fun init() {
        super.init()
        rebuildRows()

        val totalButtonWidth = 214
        val buttonLeft = width / 2 - totalButtonWidth / 2
        val gap = 4

        val config = ChangelogService.config
        val linkName = config?.externalLinkName?.takeIf(String::isNotBlank)
        // 配置里的 URL 可能写错，解析失败时直接不显示按钮，而不是等到点击时抛异常
        val linkUri = config?.externalLinkUrl?.let(::parseHttpUri)

        val buttonY = height - 30
        if (linkName != null && linkUri != null) {
            val linkWidth = (totalButtonWidth - gap) / 3
            addRenderableWidget(
                Button.builder(Component.literal(linkName)) { ConfirmLinkScreen.confirmLinkNow(this, linkUri) }
                    .bounds(buttonLeft, buttonY, linkWidth, 20)
                    .build()
            )
            addRenderableWidget(
                Button.builder(Component.translatable("gui.back")) { onClose() }
                    .bounds(buttonLeft + linkWidth + gap, buttonY, totalButtonWidth - gap - linkWidth, 20)
                    .build()
            )
        } else {
            // 没有外链按钮时让返回键独占整条，否则它会偏右 2px 且短 4px
            addRenderableWidget(
                Button.builder(Component.translatable("gui.back")) { onClose() }
                    .bounds(buttonLeft, buttonY, totalButtonWidth, 20)
                    .build()
            )
        }

        addRenderableWidget(
            Button.builder(Component.translatable("screen.changelog363.refresh")) {
                ChangelogService.ensureChangelogLoaded(forceRefresh = true)
                    .thenRun { minecraft?.execute(::rebuildRows) }
            }
                .bounds(width - 100, 10, 90, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.changelog363.refresh.tooltip")))
                .build()
        )
    }

    /** 只接受绝对的 http(s) 链接，其余（空串、相对路径、file:// 等）一律视为未配置 */
    private fun parseHttpUri(raw: String): URI? =
        runCatching { URI.create(raw.trim()) }.getOrNull()
            ?.takeIf { it.isAbsolute && (it.scheme.equals("http", true) || it.scheme.equals("https", true)) }

    private fun rebuildRows() {
        // 内容右边界要比磁贴右边缘再收一点，否则日期会紧贴着磁贴边框
        val contentRight = listRight - ROW_PADDING
        val textWidth = contentRight - TEXT_LEFT

        rows = ChangelogLoader.data.entries.map { entry ->
            val type = entry.types.first()

            val date = entry.date
            val dateWidth = if (date.isBlank()) 0 else font.width(date)
            val dateX = contentRight - dateWidth
            // 第一行（版本号 + 类型标签）共用的右边界：不能越过右对齐的日期
            val lineLimit = if (dateWidth == 0) contentRight else dateX - GAP_BEFORE_DATE

            // 版本号自身也要截断，否则超长版本号会直接画到日期上，
            // 而且会把 badgeX 顶过 lineLimit 导致类型标签全部消失
            val versionText = font.ellipsize(
                "${ColorUtil.typeIcon(type)} ${entry.version}",
                lineLimit - TEXT_LEFT,
            )
            val badgeX = TEXT_LEFT + font.width(versionText) + GAP_AFTER_VERSION

            Row(
                entry = entry,
                versionText = versionText,
                versionColor = ColorUtil.typeColor(type),
                // 标签只排到日期左侧为止，放不下的整枚不画
                badges = font.fitBadges(entry.types.map(::typeBadge), badgeX, lineLimit),
                badgeX = badgeX,
                date = date,
                dateX = dateX,
                title = font.ellipsize(entry.title, textWidth),
                summary = entry.changes.firstOrNull()
                    ?.let { font.ellipsize("• $it", textWidth) }
                    .orEmpty(),
            )
        }
        clampScroll()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        clampScroll()
        smoothScroll += (targetScroll - smoothScroll) * SCROLL_SMOOTHING
        if (abs(smoothScroll - targetScroll) < 0.5f) smoothScroll = targetScroll.toFloat()
        // 在渲染时判定悬停，滚轮滚动（鼠标不动）时高亮也能跟着更新
        hoveredIndex = entryIndexAt(mouseX, mouseY)

        val titleText = title.string
        graphics.drawString(font, titleText, (width - font.width(titleText)) / 2, 20, ColorUtil.WHITE)

        val stats = Component.translatable("screen.changelog363.stats", rows.size).string
        graphics.drawString(font, stats, LIST_LEFT, 35, ColorUtil.GREY)

        if (ChangelogService.config?.enableVersionCheck == true && VersionChecker.isDone && VersionChecker.hasUpdate) {
            val update = Component.translatable(
                "screen.changelog363.update_available", VersionChecker.latestVersion
            ).string
            graphics.drawString(font, update, LIST_LEFT + font.width(stats) + 6, 35, ColorUtil.YELLOW)
        }

        graphics.enableScissor(LIST_LEFT, LIST_TOP, listRight, listBottom)
        graphics.fill(LIST_LEFT, LIST_TOP, LIST_LEFT + 1, listBottom, DIVIDER)

        var y = LIST_TOP - smoothScroll.toInt()
        for ((index, row) in rows.withIndex()) {
            if (y > listBottom) break
            if (y + SLOT_HEIGHT >= LIST_TOP) renderRow(graphics, row, y, hovered = index == hoveredIndex)
            y += SLOT_HEIGHT
        }

        graphics.disableScissor()
        renderScrollbar(graphics)
    }

    private fun renderRow(graphics: GuiGraphics, row: Row, top: Int, hovered: Boolean) {
        graphics.fill(
            LIST_LEFT, top + 1, listRight, top + SLOT_HEIGHT - 1,
            if (hovered) ROW_HOVERED else ROW_BACKGROUND
        )
        // 与磁贴背景保持同一垂直范围，否则色条上下各多出 1px，行与行之间会露出色带
        graphics.fill(LIST_LEFT, top + 1, LIST_LEFT + 4, top + SLOT_HEIGHT - 1, row.entry.color)

        graphics.drawString(font, row.versionText, TEXT_LEFT, top + 4, row.versionColor)

        var badgeX = row.badgeX
        for (badge in row.badges) badgeX = graphics.drawBadge(font, badge, badgeX, top + 3)

        if (row.date.isNotBlank()) {
            graphics.drawString(font, row.date, row.dateX, top + 4, ColorUtil.GREY)
        }
        if (row.title.isNotBlank()) {
            graphics.drawString(font, row.title, TEXT_LEFT, top + 18, ColorUtil.LIGHT_GREY)
        }
        if (row.summary.isNotBlank()) {
            graphics.drawString(font, row.summary, TEXT_LEFT, top + 34, ColorUtil.GREY)
        }
    }

    private fun renderScrollbar(graphics: GuiGraphics) {
        if (maxScroll <= 0) return
        val thumbHeight = (visibleHeight.toFloat() / totalContentHeight * visibleHeight).toInt().coerceAtLeast(10)
        val thumbY = LIST_TOP + ((smoothScroll / maxScroll) * (visibleHeight - thumbHeight)).toInt()
        graphics.fill(scrollBarLeft - 1, LIST_TOP, scrollBarLeft, listBottom, DIVIDER)
        graphics.fill(scrollBarLeft, LIST_TOP, scrollBarRight, listBottom, SCROLL_TRACK)
        graphics.fill(scrollBarLeft, thumbY, scrollBarRight, thumbY + thumbHeight, SCROLL_THUMB)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val index = entryIndexAt(mouseX.toInt(), mouseY.toInt())
            if (index >= 0) {
                minecraft?.setScreen(ChangelogDetailScreen(rows[index].entry, this))
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        targetScroll = (targetScroll - (scrollY * SCROLL_STEP).toInt()).coerceIn(0, maxScroll)
        return true
    }

    /** 窗口尺寸或条目数量变化后，滚动位置可能越界 */
    private fun clampScroll() {
        targetScroll = targetScroll.coerceIn(0, maxScroll)
        smoothScroll = smoothScroll.coerceIn(0f, maxScroll.toFloat())
    }

    private fun entryIndexAt(mouseX: Int, mouseY: Int): Int {
        if (mouseX !in LIST_LEFT..listRight) return -1
        if (mouseY !in LIST_TOP..<listBottom) return -1
        val index = (mouseY - LIST_TOP + smoothScroll.toInt()) / SLOT_HEIGHT
        return if (index in rows.indices) index else -1
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false
}
