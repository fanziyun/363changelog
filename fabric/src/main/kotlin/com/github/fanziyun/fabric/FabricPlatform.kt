package com.github.fanziyun.fabric

import com.github.fanziyun.platform.Platform
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

/** 通过 `META-INF/services` 注册给 common。 */
class FabricPlatform : Platform {
    override val gameDir: Path get() = FabricLoader.getInstance().gameDir
}
