package com.github.fanziyun.runtime

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.VersionChecker
import com.github.fanziyun.screen.ChangelogOverviewScreen
import net.minecraft.client.gui.screens.Screen
import java.nio.file.Path
import java.util.Properties

/** Stable reflection boundary loaded by the Fabric host's child classloader. */
class RuntimeEntrypoint {
    fun start(
        gameDir: String,
        changelogUrl: String,
        packName: String,
        modpackVersion: String,
        showOnTitle: Boolean,
        enableVersionCheck: Boolean,
        loadTimeoutSeconds: Int,
        versionYOffset: Int,
        externalLinkName: String,
        externalLinkUrl: String,
    ) {
        ChangelogService.init(
            Path.of(gameDir),
            RuntimeSettings(
                changelogUrl,
                packName,
                modpackVersion,
                showOnTitle,
                enableVersionCheck,
                loadTimeoutSeconds,
                versionYOffset,
                externalLinkName,
                externalLinkUrl,
            ),
        )
    }

    fun stop() {
        ChangelogService.shutdown()
    }

    fun createOverviewScreen(parent: Screen?): Screen = ChangelogOverviewScreen(parent)

    fun versionLine(): String {
        val settings = ChangelogService.config ?: return ""
        val label = listOfNotNull(
            settings.packName.trim().takeIf(String::isNotEmpty),
            settings.modpackVersion.trim().takeIf(String::isNotEmpty)?.let { "v$it" },
        ).joinToString(" ")
        if (label.isEmpty() || !settings.enableVersionCheck || !VersionChecker.isDone) return label

        val status = if (VersionChecker.hasUpdate && VersionChecker.latestVersion.isNotBlank()) {
            "update ${VersionChecker.latestVersion} available"
        } else if (VersionChecker.currentVersion.isNotBlank()) {
            "up to date"
        } else {
            ""
        }
        return listOf(label, status).filter(String::isNotBlank).joinToString(" | ")
    }

    fun runtimeMarker(): String {
        val properties = Properties()
        RuntimeEntrypoint::class.java.getResourceAsStream("/changelog-runtime.properties")?.use {
            properties.load(it)
        }
        return properties.getProperty("runtime_version", "unknown")
    }
}
