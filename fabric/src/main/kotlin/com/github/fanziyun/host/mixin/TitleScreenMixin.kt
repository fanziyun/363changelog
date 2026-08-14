package com.github.fanziyun.host.mixin

import com.github.fanziyun.host.HostBridge
import com.github.fanziyun.host.HostButtonPlacement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val BUTTON_WIDTH = 200

@Mixin(TitleScreen::class)
abstract class TitleScreenMixin : Screen(Component.literal("")) {

    @Inject(method = ["init"], at = [At("TAIL")])
    fun changelog363_addChangelogButton(callback: CallbackInfo) {
        if (!HostBridge.showOnTitle()) return
        HostBridge.ensureLoaded()

        val left = width / 2 - BUTTON_WIDTH / 2
        val buttonY = HostButtonPlacement.belowExistingColumn(children(), height, left, left + BUTTON_WIDTH)
            ?: HostButtonPlacement.afterTitleColumn(height)
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                val client = Minecraft.getInstance()
                HostBridge.openOverview(client.screen)?.let { screen -> client.setScreen(screen) }
            }.bounds(left, buttonY, BUTTON_WIDTH, 20).build(),
        )
    }

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    fun changelog363_renderVersionLine(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo,
    ) {
        val label = HostBridge.versionLine()
        if (label.isBlank()) return

        val renderedLabel = font.plainSubstrByWidth(label, (width - 4).coerceAtLeast(0))
        val lineY = (height - HostBridge.versionYOffset())
            .coerceIn(0, (height - font.lineHeight).coerceAtLeast(0))
        graphics.text(font, renderedLabel, 2, lineY, 0xFFFFFFFF.toInt())
    }
}
