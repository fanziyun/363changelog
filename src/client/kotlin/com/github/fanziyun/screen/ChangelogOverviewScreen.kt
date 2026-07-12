package com.github.fanziyun.screen

import com.github.fanziyun.client.ChangelogClient
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

class ChangelogOverviewScreen(parent: Screen?) : Screen(Component.translatable("screen.changelog363.title")) {

    private val parentScreen = parent
    private var targetScroll = 0
    private var smoothScroll = 0f
    private var hoveredIndex = -1
    private val slotHeight = 50
    private val listLeft = 20
    private val listRight: Int get() = width - 30
    private val scrollBarWidth = 6
    private val scrollBarRight: Int get() = width - 10
    private val scrollBarLeft: Int get() = scrollBarRight - scrollBarWidth
    private val listTop = 55
    private val listBottom: Int get() = height - 60

    private val totalContentHeight: Int get() = ChangelogLoader.data.entriesOrEmpty.size * slotHeight
    private val visibleHeight: Int get() = listBottom - listTop
    private val maxScroll: Int get() = (totalContentHeight - visibleHeight).coerceAtLeast(0)

    private val scrollOffset: Int get() = smoothScroll.toInt()

    override fun init() {
        super.init()
        val totalBtnWidth = 214
        val btnLeft = width / 2 - totalBtnWidth / 2
        val gap = 4

        val cfg = ChangelogClient.config
        val hasLink = cfg != null && cfg.externalLinkUrl.isNotBlank() && cfg.externalLinkName.isNotBlank()
        val linkWidth = if (hasLink) (totalBtnWidth - gap) / 3 else 0
        val backWidth = (totalBtnWidth - gap) - linkWidth

        if (hasLink) {
            addRenderableWidget(
                Button.builder(Component.literal(cfg.externalLinkName)) {
                    ConfirmLinkScreen.confirmLink(this, URI.create(cfg.externalLinkUrl))
                }.bounds(btnLeft, height - 30, linkWidth, 20).build()
            )
        }

        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) {
                minecraft.setScreen(parentScreen)
            }.bounds(btnLeft + linkWidth + gap, height - 30, backWidth, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("screen.changelog363.refresh")) {
                val url = ChangelogClient.config?.changelogUrl ?: ""
                ChangelogLoader.load(url, forceRefresh = true).thenRun {
                    minecraft.execute {
                        minecraft.setScreen(ChangelogOverviewScreen(parentScreen))
                    }
                }
            }.bounds(width - 100, 10, 90, 20).tooltip(Tooltip.create(Component.translatable("screen.changelog363.refresh.tooltip"))).build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        smoothScroll += (targetScroll - smoothScroll) * 0.25f
        if (kotlin.math.abs(smoothScroll - targetScroll) < 0.5f) smoothScroll = targetScroll.toFloat()

        val titleText = title.string
        graphics.drawString(font, titleText, width / 2 - font.width(titleText) / 2, 20, 0xFFFFFF)

        val stats = Component.translatable("screen.changelog363.stats", ChangelogLoader.data.entriesOrEmpty.size).string
        graphics.drawString(font, stats, 20, 35, 0xFFAAAAAA.toInt())

        val config = ChangelogClient.config
        if (config?.enableVersionCheck == true && VersionChecker.isDone && VersionChecker.hasUpdate) {
            val updateText = "新版本: ${VersionChecker.latestVersion}"
            graphics.drawString(font, updateText, 20 + font.width(stats) + 4, 35, 0xFF_FF_FF_55.toInt())
        }

        graphics.enableScissor(listLeft, listTop, listRight, listBottom)

        graphics.fill(listLeft, listTop, listLeft + 1, listBottom, 0x44FFFFFF)

        var y = listTop - scrollOffset
        for ((i, entry) in ChangelogLoader.data.entriesOrEmpty.withIndex()) {
            if (y + slotHeight < listTop) { y += slotHeight; continue }
            if (y > listBottom) break
            if (i == hoveredIndex) {
                graphics.fill(listLeft, y, listRight, y + slotHeight, 0x33FFFFFF)
            }
            y = renderEntry(graphics, entry, y)
        }

        graphics.disableScissor()

        renderScrollbar(graphics)
    }

    private fun renderEntry(graphics: GuiGraphics, entry: ChangelogEntry, top: Int): Int {
        var y = top
        graphics.fill(20, y + 1, listRight, y + slotHeight - 1, 0x1AFFFFFF)
        graphics.fill(20, y, 24, y + slotHeight, entry.parsedColor)

        val icon = ColorUtil.getTypeIcon(entry.primaryType)
        val typeColor = ColorUtil.getTypeColor(entry.primaryType)
        val versionText = "$icon ${entry.versionOrEmpty}"
        graphics.drawString(font, versionText, 32, y + 4, typeColor)

        var tagX = 32 + font.width(versionText) + 6
        for (tag in entry.typeOrEmpty) {
            val tagName = ColorUtil.getTypeDisplayName(tag)
            val tagW = font.width(tagName) + 6
            graphics.fill(tagX, y + 3, tagX + tagW, y + 13, ColorUtil.getTypeColor(tag))
            graphics.drawString(font, tagName, tagX + 3, y + 4, if (ColorUtil.isBright(ColorUtil.getTypeColor(tag))) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            tagX += tagW + 4
        }

        if (entry.dateOrEmpty.isNotBlank()) {
            graphics.drawString(font, entry.dateOrEmpty, listRight - font.width(entry.dateOrEmpty), y + 4, 0xFFAAAAAA.toInt())
        }
        if (entry.titleOrEmpty.isNotBlank()) {
            graphics.drawString(font, entry.titleOrEmpty, 32, y + 18, 0xFFDDDDDD.toInt())
        }
        if (entry.changesOrEmpty.isNotEmpty()) {
            graphics.drawString(font, "• ${entry.changesOrEmpty.first().take(45)}", 32, y + 34, 0xFFAAAAAA.toInt())
        }

        y += slotHeight
        return y
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val idx = hitTestEntry(mouseY.toInt())
            if (idx >= 0) {
                minecraft.setScreen(ChangelogDetailScreen(ChangelogLoader.data.entriesOrEmpty[idx], this))
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        targetScroll = (targetScroll - (scrollY * 20).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        hoveredIndex = hitTestEntry(mouseY.toInt())
        super.mouseMoved(mouseX, mouseY)
    }

    private fun hitTestEntry(mouseY: Int): Int {
        if (mouseY !in listTop until listBottom) return -1
        val entryIndex = (mouseY - listTop + scrollOffset) / slotHeight
        if (entryIndex < 0 || entryIndex >= ChangelogLoader.data.entriesOrEmpty.size) return -1
        return entryIndex
    }

    private fun renderScrollbar(graphics: GuiGraphics) {
        if (maxScroll <= 0) return
        val barHeight = (visibleHeight.toFloat() / totalContentHeight * visibleHeight).toInt().coerceAtLeast(10)
        val barY = listTop + ((smoothScroll / maxScroll) * (visibleHeight - barHeight)).toInt()
        graphics.fill(scrollBarLeft - 1, listTop, scrollBarLeft, listBottom, 0x44FFFFFF)
        graphics.fill(scrollBarLeft, listTop, scrollBarRight, listBottom, 0x33FFFFFF)
        graphics.fill(scrollBarLeft, barY, scrollBarRight, barY + barHeight, 0xAAFFFFFF.toInt())
    }

    override fun isPauseScreen() = false
}
