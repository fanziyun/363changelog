package com.github.fanziyun

import com.github.fanziyun.config.ModConfig
import com.github.fanziyun.config.DEFAULT_LOAD_TIMEOUT_SECONDS
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.AutoConfigClient
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.minecraft.client.gui.screens.Screen
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

object ChangelogService {

    private val initialized = AtomicBoolean()

    @Volatile
    var config: ModConfig? = null
        private set

    fun init() {
        if (!initialized.compareAndSet(false, true)) return

        AutoConfig.register(ModConfig::class.java) { definition, clazz ->
            GsonConfigSerializer(definition, clazz)
        }
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config

        // 这里只是"点火"：真正加载跑在后台线程 363Changelog-Loader 上，本方法立即返回。
        // 启动线程 / 渲染线程都不等待加载，游戏进标题界面、进世界都不会被 changelog 阻塞。
        Changelog.LOGGER.info(
            "363Changelog initialized — changelog load runs asynchronously on the '363Changelog-Loader' background thread and never blocks game entry; load timeout {}s",
            config?.loadTimeoutSeconds ?: DEFAULT_LOAD_TIMEOUT_SECONDS,
        )
        ensureChangelogLoaded()
    }

    fun configScreen(parent: Screen?): Screen =
        // 注意：Cloth 26.1.154 的 getConfigScreen 返回的是 Supplier<Screen>（不是 CompletableFuture），
        // .get() 是在渲染线程上内联构建配置界面（有界、无网络/无 future 等待），风险低。
        // 若将来依赖升级成返回 CompletableFuture 的重载，这个 .get() 会变成渲染线程上的真正阻塞，
        // 必须改用 getNow()/isDone 之类的非阻塞写法。
        AutoConfigClient.getConfigScreen(ModConfig::class.java, parent).get()

    fun ensureChangelogLoaded(forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val cfg = config ?: return CompletableFuture.completedFuture(false)
        // 下限对齐"单次远程请求最坏耗时"（connect 5s + read 10s ≈ 15s），防止正常慢速请求被误判超时；
        // 上限兜底防误填超大值。超出范围的存储值由配置界面的 @BoundedDiscrete 拦截。
        val timeoutMs = cfg.loadTimeoutSeconds.coerceIn(20, 120) * 1000L
        if (forceRefresh) VersionChecker.reset()

        val loading = if (forceRefresh) {
            ChangelogLoader.load(cfg.changelogUrl, forceRefresh = true, timeoutMs = timeoutMs)
        } else {
            ChangelogLoader.ensureLoaded(cfg.changelogUrl, timeoutMs = timeoutMs)
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
