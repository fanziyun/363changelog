package com.github.fanziyun.fabric

//? if fabric {
import com.github.fanziyun.platform.Platform
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

class FabricPlatform : Platform {
    override val gameDir: Path get() = FabricLoader.getInstance().gameDir
}
//?}
