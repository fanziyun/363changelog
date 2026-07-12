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
    private val parentScreen: Screen?
) : Screen(Component.literal("${entry.versionOrEmpty} - ${entry.titleOrEmpty}")) {

    override fun init() {
        super.init()
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(width / 2 - 50, height - 30, 100, 20).build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        val titleText = "${entry.versionOrEmpty} - ${entry.titleOrEmpty}"
        graphics.drawString(font, titleText, width / 2 - font.width(titleText) / 2, 20, entry.parsedColor)

        if (entry.dateOrEmpty.isNotBlank()) {
            val dateText = "日期: ${entry.dateOrEmpty}"
            graphics.drawString(font, dateText, width / 2 - font.width(dateText) / 2, 35, 0xFFAAAAAA.toInt())
        }

        renderTags(graphics, 50)

        var y = 65
        for (change in entry.changesOrEmpty) {
            if (y > height - 40) break
            graphics.drawString(font, "• $change", 30, y, 0xFFDDDDDD.toInt())
            y += 12
        }
    }

    private fun renderTags(graphics: GuiGraphics, y: Int) {
        val tags = mutableListOf<Pair<String, Int>>()
        for (t in entry.typeOrEmpty) tags.add(ColorUtil.getTypeDisplayName(t, true) to ColorUtil.getTypeColor(t))
        for (t in entry.tagsOrEmpty) {
            val c = ColorUtil.parseColor(ChangelogLoader.data.tagColorsOrEmpty[t] ?: "#888888", 0xFF888888.toInt())
            tags.add(t to c)
        }
        if (tags.isEmpty()) return
        val totalW = tags.sumOf { font.width(it.first) + 10 }
        var x = (width - totalW) / 2
        for ((text, color) in tags) {
            val w = font.width(text) + 6
            graphics.fill(x, y - 1, x + w, y + 9, color)
            val textColor = if (ColorUtil.isBright(color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            graphics.drawString(font, text, x + 3, y, textColor)
            x += w + 4
        }
    }

    override fun onClose() { minecraft.setScreen(parentScreen) }
    override fun isPauseScreen() = false
}
