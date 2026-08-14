package com.github.fanziyun.host

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Mod Menu entrypoint: configuration and an explicit runtime replacement action. */
class HostControlScreen(private val parent: Screen?) : Screen(Component.literal("363Changelog")) {
    private var status = "Runtime: ${HostBridge.runtimeMarker()}"

    override fun init() {
        super.init()
        val buttonWidth = 220
        val left = width / 2 - buttonWidth / 2
        var y = height / 2 - 34

        addRenderableWidget(
            Button.builder(Component.literal("Configure")) {
                minecraft.setScreen(HostBridge.configScreen(this))
            }.bounds(left, y, buttonWidth, 20).build(),
        )
        y += 24
        addRenderableWidget(
            Button.builder(Component.literal("Reload runtime JAR")) {
                status = runCatching {
                    HostBridge.reloadRuntime()
                    "Reloaded runtime: ${HostBridge.runtimeMarker()}"
                }.getOrElse { "Reload failed: ${it.message ?: it.javaClass.simpleName}" }
            }.bounds(left, y, buttonWidth, 20).build(),
        )
        y += 24
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(left, y, buttonWidth, 20)
                .build(),
        )
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, 24, 0xFFFFFFFF.toInt())
        graphics.textWithWordWrap(
            font,
            Component.literal(status),
            width / 2 - 160,
            height / 2 + 58,
            320,
            0xFFE0E0E0.toInt(),
        )
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen() = false
}
