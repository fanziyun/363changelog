package com.github.fanziyun.mixin

import com.github.fanziyun.Changelog
import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.platform.Platform
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ButtonPlacement
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

// 声明成文件级 const，编译期会被直接内联到使用处，
// 不会给 mixin 类引入额外成员（放 companion object 反而会多出合成内部类）

/** 和原版那排小图标一样的方形边长 */
private const val ICON_SIZE = 20

/** 图标在按钮里的绘制尺寸，与原版 icon/language、icon/accessibility 保持一致 */
private const val SPRITE_SIZE = 15

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

        val label = Component.translatable("menu.changelog363.button")
        val button = SpriteIconButton.builder(label, {
            // 26.2 把 setScreen / screen 从 Minecraft 挪到了 Gui 上
            val gui = Minecraft.getInstance().gui
            gui.setScreen(ChangelogOverviewScreen(gui.screen()))
        }, true)
            .width(ICON_SIZE)
            .sprite(Identifier.fromNamespaceAndPath(Changelog.MOD_ID, "icon/changelog"), SPRITE_SIZE, SPRITE_SIZE)
            .tooltip(label)
            .build()

        val slot = ButtonPlacement.besideIconRow(children(), width, ICON_SIZE)
        if (slot != null) {
            button.setPosition(slot.x, slot.y)
        } else {
            // 那排小图标不在（其他模组重排过布局？）时退回"排在菜单列下方"，
            // 兜底值是原版"选项/退出"那行的理论底部
            val left = width / 2 - ICON_SIZE / 2
            val y = ButtonPlacement.belowExistingColumn(children(), height, left, left + ICON_SIZE)
                ?: (height / 4 + 48 + 72)
            button.setPosition(left, y)
        }
        addRenderableWidget(button)
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
        // 加载器自己的版本行是自下而上堆的，多于一行时（NeoForge）要再往上让，
        // 否则 versionYOffset 的默认值正好落在它的第二行上
        val brandingOffset = (Platform.INSTANCE.titleScreenBrandingLines - 1) * (font.lineHeight + 1)
        val lineY = height - config.versionYOffset - brandingOffset
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
