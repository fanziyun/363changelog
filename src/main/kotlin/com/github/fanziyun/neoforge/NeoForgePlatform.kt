package com.github.fanziyun.neoforge

//? if neoforge {
import com.github.fanziyun.platform.Platform
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

class NeoForgePlatform : Platform {
    override val gameDir: Path get() = FMLPaths.GAMEDIR.get()
}
//?}
