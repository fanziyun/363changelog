package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer
import java.util.concurrent.atomic.AtomicReference

object VersionChecker {

    private data class State(
        val isDone: Boolean = false,
        val hasUpdate: Boolean = false,
        val latestVersion: String = "",
        val currentVersion: String = "",
    )

    private val state = AtomicReference(State())

    val isDone: Boolean get() = state.get().isDone
    val hasUpdate: Boolean get() = state.get().hasUpdate
    val latestVersion: String get() = state.get().latestVersion
    val currentVersion: String get() = state.get().currentVersion

    fun check(modpackVersion: String) {
        val current = modpackVersion.trim()
        if (current.isEmpty()) {
            state.set(State(isDone = true))
            return
        }

        val latest = ChangelogLoader.latestVersion
        val hasUpdate = latest.isNotBlank() && SemVer.compare(latest, current) > 0
        state.set(State(isDone = true, hasUpdate = hasUpdate, latestVersion = latest, currentVersion = current))
        Changelog.LOGGER.info(
            "Version check: current={}, latest={}, hasUpdate={}",
            current, latest, hasUpdate,
        )
    }

    fun reset() {
        state.set(State())
    }
}
