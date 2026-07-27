package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer

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
            modpackVersion, latest, hasUpdate,
        )
    }

    @Synchronized
    fun reset() {
        isDone = false
        hasUpdate = false
        latestVersion = ""
    }
}
