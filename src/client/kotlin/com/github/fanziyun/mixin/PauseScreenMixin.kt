package com.github.fanziyun.mixin

import com.github.fanziyun.client.ChangelogService
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ButtonPlacement
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
    @field:Shadow
    private var showPauseMenu: Boolean = false

    @Inject(method = ["init"], at = [At("TAIL")])
    private fun changelog363_addChangelogButton(callback: CallbackInfo) {
        val config = ChangelogService.config ?: return
        if (!config.showOnTitle) return
        if (!showPauseMenu) return

        ChangelogService.ensureChangelogLoaded()

        val left = width / 2 - BUTTON_WIDTH / 2
        val buttonY = ButtonPlacement.belowExistingColumn(children(), height, left, left + BUTTON_WIDTH)
            ?: return
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                minecraft?.setScreen(ChangelogOverviewScreen(this))
            }.bounds(left, buttonY, BUTTON_WIDTH, ButtonPlacement.BUTTON_HEIGHT).build()
        )
    }
}
