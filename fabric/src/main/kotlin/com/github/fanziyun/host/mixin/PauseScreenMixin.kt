package com.github.fanziyun.host.mixin

import com.github.fanziyun.host.HostBridge
import com.github.fanziyun.host.HostButtonPlacement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

private const val BUTTON_WIDTH = 204

@Mixin(PauseScreen::class)
abstract class PauseScreenMixin : Screen(Component.literal("")) {

    @Shadow
    abstract fun showsPauseMenu(): Boolean

    @Inject(method = ["init"], at = [At("TAIL")])
    fun changelog363_addChangelogButton(callback: CallbackInfo) {
        if (!HostBridge.showOnTitle() || !showsPauseMenu()) return
        HostBridge.ensureLoaded()

        val left = width / 2 - BUTTON_WIDTH / 2
        val buttonY = HostButtonPlacement.belowExistingColumn(children(), height, left, left + BUTTON_WIDTH)
            ?: return
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                val client = Minecraft.getInstance()
                HostBridge.openOverview(client.screen)?.let { screen -> client.setScreen(screen) }
            }.bounds(left, buttonY, BUTTON_WIDTH, 20).build(),
        )
    }
}
