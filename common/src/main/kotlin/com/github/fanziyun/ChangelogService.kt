package com.github.fanziyun

import com.github.fanziyun.config.ModConfig
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.AutoConfigClient
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.minecraft.client.gui.screens.Screen
import java.util.concurrent.CompletableFuture

/**
 * 与加载器无关的模组主体逻辑：注册配置、加载更新日志、触发版本检测。
 *
 * Cloth Config 的 `AutoConfig` 在 Fabric 和 NeoForge 上是同一套 API，
 * 所以这里可以直接放在 common，两个入口点各自调一次 [init] 就行。
 */
object ChangelogService {

    @Volatile
    var config: ModConfig? = null
        private set

    /** 由各加载器的入口点在客户端初始化时调用，只应调用一次。 */
    fun init() {
        AutoConfig.register(ModConfig::class.java) { definition, clazz ->
            GsonConfigSerializer(definition, clazz)
        }
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config

        Changelog.LOGGER.info("363Changelog initialized")
        ensureChangelogLoaded()
    }

    /** ModMenu / NeoForge 模组列表里那个"配置"按钮打开的界面。 */
    fun configScreen(parent: Screen?): Screen =
        AutoConfigClient.getConfigScreen(ModConfig::class.java, parent).get()

    /**
     * 确保更新日志已加载，并在加载完成后立即执行版本检测。
     *
     * 版本检测依赖已加载的数据，所以必须挂在加载完成的回调上——否则会读到空数据，
     * 导致首次进入游戏永远检测不到新版本。
     *
     * @param forceRefresh true 时忽略缓存重新拉取（"刷新"按钮）
     */
    fun ensureChangelogLoaded(forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val cfg = config ?: return CompletableFuture.completedFuture(false)
        if (forceRefresh) VersionChecker.reset()

        val loading = if (forceRefresh) ChangelogLoader.load(cfg.changelogUrl, forceRefresh = true)
        else ChangelogLoader.ensureLoaded(cfg.changelogUrl)

        return loading.whenComplete { _, _ ->
            if (cfg.enableVersionCheck) VersionChecker.check(cfg.modpackVersion)
        }
    }
}
