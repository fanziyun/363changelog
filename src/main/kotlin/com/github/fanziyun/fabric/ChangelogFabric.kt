package com.github.fanziyun.fabric

//? if fabric {
import com.github.fanziyun.ChangelogService
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/** Fabric 入口点。全部实际逻辑都在 common 的 [ChangelogService] 里。 */
@Environment(EnvType.CLIENT)
class ChangelogFabric : ClientModInitializer {
    override fun onInitializeClient() {
        ChangelogService.init()
    }
}
//?}
