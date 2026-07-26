package com.github.fanziyun.mixin

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

// 这几个常量声明成文件级 const，编译期会被直接内联到使用处，
// 不会给 mixin 类引入额外成员（放 companion object 反而会多出合成内部类）
private const val BUTTON_WIDTH = 200
private const val BUTTON_HEIGHT = 20
private const val BUTTON_GAP = 4
private const val EDGE_MARGIN = 2

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

        val left = width / 2 - BUTTON_WIDTH / 2
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                Minecraft.getInstance().setScreen(ChangelogOverviewScreen(Minecraft.getInstance().screen))
            }.bounds(left, changelog363_pickButtonY(left, left + BUTTON_WIDTH), BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )
    }

    /**
     * 挑一个不会和别人重叠的 Y。
     *
     * 原来这里是写死的 `height/4 + 48 + 72 + 12 + 24`，而原版的"选项/退出"那一行是
     * `height/4 + 48 + 48 + (24 若存在 Create Test World) + 36` —— 开发环境下两者恰好相等，
     * 于是像素级完全重合。硬编码还挡不住其他模组往标题界面加按钮，所以改成按实际布局来算。
     *
     * 优先排在菜单列下方（正式环境和常规窗口都走这条）；底部塞不下时（小窗口 / 大 GUI 缩放）
     * 退到菜单列上方、标题图与"单人游戏"之间的那段空白。
     */
    @Unique
    private fun changelog363_pickButtonY(left: Int, right: Int): Int {
        // 只看水平方向和按钮有交集的控件；版权信息在右下角，不该把按钮往下挤
        val inColumn = children()
            .filterIsInstance<AbstractWidget>()
            .filter { it.visible && it.x < right && it.x + it.width > left }

        val fallbackTop = height / 4 + 48
        val below = (inColumn.maxOfOrNull { it.y + it.height } ?: (fallbackTop + 72)) + BUTTON_GAP
        if (below + BUTTON_HEIGHT <= height - EDGE_MARGIN) return below

        val above = (inColumn.minOfOrNull { it.y } ?: fallbackTop) - BUTTON_HEIGHT - BUTTON_GAP
        return above.coerceAtLeast(EDGE_MARGIN)
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
