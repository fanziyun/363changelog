package com.github.fanziyun

import com.github.fanziyun.config.ModConfig
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import me.shedaniel.autoconfig.AutoConfig
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

        // 这里只是"点火"：真正加载跑在后台线程上，本方法立即返回。
        // 启动线程 / 渲染线程都不等待加载，游戏进标题界面、进世界都不会被 changelog 阻塞。
        Changelog.LOGGER.info(
            "363Changelog initialized — changelog load runs asynchronously and never blocks game entry; load timeout {}s",
            config?.loadTimeoutSeconds ?: ModConfig.DEFAULT_LOAD_TIMEOUT_SECONDS,
        )
        ensureChangelogLoaded()
    }

    /** ModMenu / NeoForge 模组列表里那个"配置"按钮打开的界面。 */
    // Cloth Config 15.x（1.21.1）还没拆出 AutoConfigClient，配置界面直接从 AutoConfig 拿
    fun configScreen(parent: Screen?): Screen =
        AutoConfig.getConfigScreen(ModConfig::class.java, parent).get()

    /**
     * 确保更新日志已加载，并在加载成功后执行版本检测。
     *
     * 版本检测依赖已加载的数据，所以必须挂在加载完成的回调上——否则会读到空数据，
     * 导致首次进入游戏永远检测不到新版本。
     *
     * 只有真正成功才检测：超时兜底会把 future 正常完成（值为 false），此时手上大概率
     * 还是空数据，照样去比较就会得出"已是最新版本"，在标题界面画出一行绿字，
     * 而实际上什么都没加载到。这种情况下要把检测重置回"未完成"，界面就不下结论。
     *
     * @param forceRefresh true 时忽略缓存重新拉取（"刷新"按钮）
     */
    fun ensureChangelogLoaded(forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val cfg = config ?: return CompletableFuture.completedFuture(false)
        if (forceRefresh) VersionChecker.reset()

        val loading = if (forceRefresh) {
            ChangelogLoader.load(cfg.changelogUrl, forceRefresh = true, timeoutMs = cfg.loadTimeoutMillis)
        } else {
            ChangelogLoader.ensureLoaded(cfg.changelogUrl, timeoutMs = cfg.loadTimeoutMillis)
        }

        return loading.whenComplete { success, exception ->
            val currentConfig = config
            if (
                exception == null && success == true && !ChangelogLoader.isError &&
                currentConfig?.enableVersionCheck == true
            ) {
                VersionChecker.check(currentConfig.modpackVersion)
            } else {
                VersionChecker.reset()
            }
        }
    }
}
