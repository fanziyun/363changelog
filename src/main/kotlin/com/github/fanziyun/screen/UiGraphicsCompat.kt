package com.github.fanziyun.screen

import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.Font

//? if <26 {
import net.minecraft.client.gui.GuiGraphics

typealias UiGraphics = GuiGraphics

fun UiGraphics.uiText(font: Font, text: String, x: Int, y: Int, color: Int) {
    drawString(font, text, x, y, color)
}

fun UiGraphics.enableUiScissor(x1: Int, y1: Int, x2: Int, y2: Int) {
    enableScissor(x1, y1, x2, y2)
}

fun UiGraphics.disableUiScissor() {
    disableScissor()
}
//?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor

typealias UiGraphics = GuiGraphicsExtractor

fun UiGraphics.uiText(font: Font, text: String, x: Int, y: Int, color: Int) {
    text(font, text, x, y, color)
}

fun UiGraphics.enableUiScissor(x1: Int, y1: Int, x2: Int, y2: Int) {
    enableScissor(x1, y1, x2, y2)
}

fun UiGraphics.disableUiScissor() {
    disableScissor()
}
//?}
