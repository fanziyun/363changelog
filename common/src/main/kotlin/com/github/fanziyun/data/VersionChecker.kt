package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer

/**
 * 版本检测。
 *
 * 直接复用 [ChangelogLoader] 已加载的数据做比较，不会额外发起 HTTP 请求，
 * 因此必须在加载完成之后调用（见 `ChangelogService.ensureChangelogLoaded`）。
 */
object VersionChecker {

    @Volatile
    var isDone: Boolean = false
        private set

    @Volatile
    var hasUpdate: Boolean = false
        private set

    @Volatile
    var latestVersion: String = ""
        private set

    @Volatile
    var currentVersion: String = ""
        private set

    /** 比较整合包版本与更新日志中的最高版本。 */
    @Synchronized
    fun check(modpackVersion: String) {
        currentVersion = modpackVersion
        if (modpackVersion.isBlank()) {
            hasUpdate = false
            isDone = true
            return
        }
        val latest = ChangelogLoader.latestVersion
        latestVersion = latest
        hasUpdate = latest.isNotBlank() && SemVer.compare(latest, modpackVersion) > 0
        isDone = true
        Changelog.LOGGER.info(
            "Version check: current={}, latest={}, hasUpdate={}",
            modpackVersion, latest, hasUpdate
        )
    }

    /** 重新拉取数据前调用，让界面回到"检测中"状态。 */
    @Synchronized
    fun reset() {
        isDone = false
        hasUpdate = false
        latestVersion = ""
    }
}
