package com.github.fanziyun

import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.platform.Platform
import com.github.fanziyun.runtime.RuntimeSettings
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

object ChangelogService {

    @Volatile
    var config: RuntimeSettings? = null
        private set

    fun init(gameDir: Path, settings: RuntimeSettings) {
        Platform.configure(gameDir)
        config = settings

        Changelog.LOGGER.info(
            "363Changelog runtime initialized — load runs asynchronously; timeout {}s",
            settings.loadTimeoutSeconds,
        )
        ensureChangelogLoaded()
    }

    fun shutdown() {
        ChangelogLoader.shutdown()
        VersionChecker.reset()
        config = null
        Changelog.LOGGER.info("363Changelog runtime shut down")
    }

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
