package com.github.fanziyun.mixin

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.util.ButtonPlacement
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

// 暂停菜单整行按钮宽 204（两个半宽按钮 98 + 中间 8 间距），和"回到游戏"那行对齐
private const val BUTTON_WIDTH = 204

@Mixin(PauseScreen::class)
abstract class PauseScreenMixin : Screen(Component.literal("")) {

    @Shadow
    abstract fun showsPauseMenu(): Boolean

    @Inject(method = ["init"], at = [At("TAIL")])
    fun changelog363_addChangelogButton(callback: CallbackInfo) {
        val config = ChangelogService.config ?: return
        if (!config.showOnTitle) return
        // F3+Esc 的精简暂停界面跟着原版保持空白。注意不能用"界面上没有控件"来判断——
        // 精简界面也有一个"游戏已暂停"的 StringWidget，控件列永远不为空
        if (!showsPauseMenu()) return

        // 快速游玩等入口可能没经过标题界面，这里兜底触发一次加载（已加载则直接复用）
        ChangelogService.ensureChangelogLoaded()

        val left = width / 2 - BUTTON_WIDTH / 2
        val buttonY = ButtonPlacement.belowExistingColumn(children(), height, left, left + BUTTON_WIDTH)
            ?: return
        addRenderableWidget(
            Button.builder(Component.translatable("menu.changelog363.button")) {
                Minecraft.getInstance().setScreen(ChangelogOverviewScreen(Minecraft.getInstance().screen))
            }.bounds(left, buttonY, BUTTON_WIDTH, ButtonPlacement.BUTTON_HEIGHT).build()
        )
    }
}
