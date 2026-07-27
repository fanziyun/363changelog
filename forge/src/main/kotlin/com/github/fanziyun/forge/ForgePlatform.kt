package com.github.fanziyun.forge

import com.github.fanziyun.platform.Platform
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

class ForgePlatform : Platform {
    override val gameDir: Path get() = FMLPaths.GAMEDIR.get()
}
