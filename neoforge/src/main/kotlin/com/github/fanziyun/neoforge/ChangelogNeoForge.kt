package com.github.fanziyun.neoforge

import com.github.fanziyun.Changelog
import com.github.fanziyun.ChangelogService
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

/** NeoForge 入口点。全部实际逻辑都在 common 的 [ChangelogService] 里。 */
@Mod(value = Changelog.MOD_ID, dist = [Dist.CLIENT])
class ChangelogNeoForge(container: ModContainer) {

    init {
        ChangelogService.init()

        // 模组列表里的"配置"按钮，对应 Fabric 侧的 ModMenu 集成。
        // registerExtensionPoint 有 (Class<T>, T) 和 (Class<T>, Supplier<T>) 两个重载，
        // 直接传裸 lambda 会有歧义，所以显式写出 SAM 构造。
        container.registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory { _, parent -> ChangelogService.configScreen(parent) },
        )
    }
}
