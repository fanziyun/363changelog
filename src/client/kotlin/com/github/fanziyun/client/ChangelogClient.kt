package com.github.fanziyun.client

import com.github.fanziyun.Changelog
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.util.concurrent.CompletableFuture

@Environment(EnvType.CLIENT)
class ChangelogClient : ClientModInitializer {

    companion object {
        var config: ModConfig? = null
            private set

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

    override fun onInitializeClient() {
        AutoConfig.register(ModConfig::class.java) { definition, clazz -> GsonConfigSerializer(definition, clazz) }
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config

        Changelog.LOGGER.info("363Changelog client initialized")
        ensureChangelogLoaded()
    }
}
