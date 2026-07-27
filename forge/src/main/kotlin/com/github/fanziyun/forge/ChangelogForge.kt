package com.github.fanziyun.forge

import com.github.fanziyun.Changelog
import com.github.fanziyun.ChangelogService
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment

@Mod(Changelog.MOD_ID)
class ChangelogForge {
    init {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ChangelogService.init()
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
                ConfigScreenHandler.ConfigScreenFactory { _, parent -> ChangelogService.configScreen(parent) }
            }
        }
    }
}
