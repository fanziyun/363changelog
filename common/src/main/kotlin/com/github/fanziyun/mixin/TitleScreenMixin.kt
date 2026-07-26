package com.github.fanziyun.mixin

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ColorUtil
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

// 注入方法统一加 changelog363_ 前缀：TitleScreen 是热门 mixin 目标，
// 通用名（onInit / onRender）容易和其他模组撞同名同签名的方法
@Mixin(TitleScreen::class)
abstract class TitleScreenMixin : Screen(Component.literal("")) {

    @Inject(method = ["init"], at = [At("TAIL")])
    fun changelog363_addChangelogButton(callback: CallbackInfo) {
        val config = ChangelogService.config ?: return
        if (!config.showOnTitle) return

        // 只有第一次会真正发起网络请求；之后返回标题界面复用已有数据
        ChangelogService.ensureChangelogLoaded()

        val buttonY = height / 4 + 48 + 72 + 12 + 24
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                Minecraft.getInstance().setScreen(ChangelogOverviewScreen(Minecraft.getInstance().screen))
            }.bounds(width / 2 - 100, buttonY, 200, 20).build()
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
        val config = ChangelogService.config ?: return

        val label = listOfNotNull(config.packName.takeIf(String::isNotBlank), "v${config.modpackVersion}")
            .joinToString(" ")
        val lineY = height - config.versionYOffset
        graphics.text(font, label, 2, lineY, ColorUtil.WHITE)

        // 检测结束前不下结论，避免先显示"已是最新版本"再跳变成"有新版本"
        if (!config.enableVersionCheck || !VersionChecker.isDone) return

        val hasUpdate = VersionChecker.hasUpdate && VersionChecker.latestVersion.isNotBlank()
        val status = if (hasUpdate) {
            Component.translatable("screen.changelog363.update_available", VersionChecker.latestVersion)
        } else {
            Component.translatable("screen.changelog363.up_to_date")
        }
        graphics.text(
            font,
            " ${status.string}",
            2 + font.width(label),
            lineY,
            if (hasUpdate) ColorUtil.YELLOW else ColorUtil.GREEN,
        )
    }
}
