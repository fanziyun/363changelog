package com.github.fanziyun.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
class ChangelogClient : ClientModInitializer {
    override fun onInitializeClient() {
        ChangelogService.init()
    }
}
