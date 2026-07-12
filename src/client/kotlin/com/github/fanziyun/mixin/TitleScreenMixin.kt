package com.github.fanziyun.mixin

import com.github.fanziyun.client.ChangelogClient
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(TitleScreen::class)
abstract class TitleScreenMixin : Screen(Component.literal("")) {

    @Unique
    private var changelogButton: Button? = null

    @Unique
    private var hasUpdate = false

    @Inject(method = ["init"], at = [At("TAIL")])
    fun onInit(callback: CallbackInfo) {
        val config = ChangelogClient.config ?: return
        if (!config.showOnTitle) return

        val buttonY = height / 4 + 48 + 72 + 12 + 24

        if (config.changelogUrl.isNotBlank()) {
            ChangelogLoader.load(config.changelogUrl)
            if (config.enableVersionCheck && config.modpackVersion.isNotBlank()) {
                VersionChecker.checkAsync(config.modpackVersion)
            }
        }

        val button = Button.builder(Component.translatable("menu.changelog363.button")) {
            Minecraft.getInstance().setScreen(ChangelogOverviewScreen(Minecraft.getInstance().screen))
        }.bounds(width / 2 - 100, buttonY, 200, 20).build()

        changelogButton = button
        addRenderableWidget(button)
    }

    @Inject(method = ["tick"], at = [At("TAIL")])
    fun onTick(callback: CallbackInfo) {
        if (VersionChecker.isDone) hasUpdate = VersionChecker.hasUpdate
    }

    @Inject(method = ["render"], at = [At("TAIL")])
    fun onRender(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float, callback: CallbackInfo) {
        val config = ChangelogClient.config ?: return
        val packName = config.packName.ifBlank { null }
        val versionPart = "v${config.modpackVersion}"
        val white = 0xFF_FF_FF_FF.toInt()
        val lineY = height - 20

        val prefix = buildString {
            if (packName != null) append(packName).append(' ')
            append(versionPart)
        }
        val prefixWidth = font.width(prefix)
        graphics.drawString(font, prefix, 2, lineY, white)

        if (hasUpdate && VersionChecker.latestVersion.isNotBlank()) {
            val status = " (新版本 v${VersionChecker.latestVersion})"
            graphics.drawString(font, status, 2 + prefixWidth, lineY, 0xFF_FF_FF_55.toInt())
        } else {
            val status = " (已是最新版本)"
            graphics.drawString(font, status, 2 + prefixWidth, lineY, 0xFF_55_FF_55.toInt())
        }
    }
}
