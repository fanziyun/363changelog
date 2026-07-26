package com.github.fanziyun.neoforge

import com.github.fanziyun.platform.Platform
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

/** 通过 `META-INF/services` 注册给 common。 */
class NeoForgePlatform : Platform {
    override val gameDir: Path get() = FMLPaths.GAMEDIR.get()
}
