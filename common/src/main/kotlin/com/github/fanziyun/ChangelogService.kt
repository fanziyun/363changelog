package com.github.fanziyun

import com.github.fanziyun.config.ModConfig
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.data.VersionChecker
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.minecraft.client.gui.screens.Screen
import java.util.concurrent.CompletableFuture

object ChangelogService {

    @Volatile
    var config: ModConfig? = null
        private set

    fun init() {
        AutoConfig.register(ModConfig::class.java) { definition, clazz ->
            GsonConfigSerializer(definition, clazz)
        }
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config

        Changelog.LOGGER.info("363Changelog initialized")
        ensureChangelogLoaded()
    }

    fun configScreen(parent: Screen?): Screen =
        AutoConfig.getConfigScreen(ModConfig::class.java, parent).get()

    fun ensureChangelogLoaded(forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val cfg = config ?: return CompletableFuture.completedFuture(false)
        if (forceRefresh) VersionChecker.reset()

        val loading = if (forceRefresh) {
            ChangelogLoader.load(cfg.changelogUrl, forceRefresh = true)
        } else {
            ChangelogLoader.ensureLoaded(cfg.changelogUrl)
        }

        return loading.whenComplete { _, _ ->
            if (cfg.enableVersionCheck) VersionChecker.check(cfg.modpackVersion)
        }
    }
}
