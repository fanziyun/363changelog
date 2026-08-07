package com.github.fanziyun

import com.github.fanziyun.config.ModConfig
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

        Changelog.LOGGER.info("363Changelog initialized")
        ensureChangelogLoaded()
    }

    fun configScreen(parent: Screen?): Screen =
        AutoConfigClient.getConfigScreen(ModConfig::class.java, parent).get()

    fun ensureChangelogLoaded(forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val cfg = config ?: return CompletableFuture.completedFuture(false)
        if (forceRefresh) VersionChecker.reset()

        val loading = if (forceRefresh) {
            ChangelogLoader.load(cfg.changelogUrl, forceRefresh = true)
        } else {
            ChangelogLoader.ensureLoaded(cfg.changelogUrl)
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