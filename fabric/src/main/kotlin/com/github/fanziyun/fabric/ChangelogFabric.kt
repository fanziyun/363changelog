package com.github.fanziyun.fabric

import com.github.fanziyun.host.HostBridge
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/** Fabric-owned entrypoint; the changelog implementation lives in a reloadable JAR. */
@Environment(EnvType.CLIENT)
class ChangelogFabric : ClientModInitializer {
    override fun onInitializeClient() {
        HostBridge.initialize()
    }
}
