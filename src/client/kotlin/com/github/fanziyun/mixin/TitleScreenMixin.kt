package com.github.fanziyun.mixin

import com.github.fanziyun.client.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ButtonPlacement
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
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
        val config = ChangelogService.config ?: return
        if (!config.showOnTitle) return

        ChangelogService.ensureChangelogLoaded()

        val left = width / 2 - BUTTON_WIDTH / 2
        val buttonY = ButtonPlacement.belowExistingColumn(children(), height, left, left + BUTTON_WIDTH)
            ?: (height / 4 + 48 + 72)
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                Minecraft.getInstance().setScreen(ChangelogOverviewScreen(Minecraft.getInstance().screen))
            }.bounds(left, buttonY, BUTTON_WIDTH, ButtonPlacement.BUTTON_HEIGHT).build()
        )
    }

    @Inject(method = ["render"], at = [At("TAIL")])
    fun changelog363_renderVersionLine(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo,
    ) {
        val config = ChangelogService.config ?: return

        val label = listOfNotNull(config.packName.takeIf(String::isNotBlank), "v${config.modpackVersion}")
            .joinToString(" ")
        val lineY = height - config.versionYOffset
        graphics.drawString(font, label, 2, lineY, ColorUtil.WHITE)

        if (!config.enableVersionCheck || !VersionChecker.isDone) return

        val hasUpdate = VersionChecker.hasUpdate && VersionChecker.latestVersion.isNotBlank()
        val status = if (hasUpdate) {
            Component.translatable("screen.changelog363.update_available", VersionChecker.latestVersion)
        } else {
            Component.translatable("screen.changelog363.up_to_date")
        }
        graphics.drawString(
            font,
            " ${status.string}",
            2 + font.width(label),
            lineY,
            if (hasUpdate) ColorUtil.YELLOW else ColorUtil.GREEN,
        )
    }
}
