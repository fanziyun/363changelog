package com.github.fanziyun.mixin

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.screen.ellipsize
import com.github.fanziyun.util.ButtonPlacement
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.Minecraft
import com.github.fanziyun.screen.UiGraphics
import com.github.fanziyun.screen.uiText
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

//? if <26 {
    @Inject(method = ["render"], at = [At("TAIL")])
//?} else {
    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
//?}
    fun changelog363_renderVersionLine(
        graphics: UiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
        callback: CallbackInfo,
    ) {
        val config = ChangelogService.config ?: return
        val label = listOfNotNull(
            config.packName.trim().takeIf(String::isNotEmpty),
            config.modpackVersion.trim().takeIf(String::isNotEmpty)?.let { "v$it" },
        ).joinToString(" ")
        if (label.isEmpty()) return

        val renderedLabel = font.ellipsize(label, (width - 4).coerceAtLeast(0))
        val lineY = (height - config.versionYOffset)
            .coerceIn(0, (height - font.lineHeight).coerceAtLeast(0))
        graphics.uiText(font, renderedLabel, 2, lineY, ColorUtil.WHITE)

        if (
            renderedLabel != label || !config.enableVersionCheck || !VersionChecker.isDone ||
            VersionChecker.currentVersion.isBlank()
        ) return

        val hasUpdate = VersionChecker.hasUpdate && VersionChecker.latestVersion.isNotBlank()
        val status = if (hasUpdate) {
            Component.translatable("screen.changelog363.update_available", VersionChecker.latestVersion)
        } else {
            Component.translatable("screen.changelog363.up_to_date")
        }
        val statusText = font.ellipsize(" ${status.string}", width - 2 - font.width(renderedLabel) - 2)
        if (statusText.isNotEmpty()) {
            graphics.uiText(
                font,
                statusText,
                2 + font.width(renderedLabel),
                lineY,
                if (hasUpdate) ColorUtil.YELLOW else ColorUtil.GREEN,
            )
        }
    }
}
