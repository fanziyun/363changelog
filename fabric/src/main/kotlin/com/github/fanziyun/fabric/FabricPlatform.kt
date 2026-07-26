package com.github.fanziyun.fabric

import com.github.fanziyun.platform.Platform
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

/** 通过 `META-INF/services` 注册给 common。 */
class FabricPlatform : Platform {
    override val gameDir: Path get() = FabricLoader.getInstance().gameDir

    /** Fabric 不改标题界面，左下角就是原版那一行"Minecraft <版本>" */
    override val titleScreenBrandingLines: Int get() = 1
}
