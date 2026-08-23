package com.github.fanziyun.platform

import com.github.fanziyun.Changelog
//? if fabric {
import com.github.fanziyun.fabric.FabricPlatform
//?} else {
import com.github.fanziyun.neoforge.NeoForgePlatform
//?}
import java.nio.file.Path

interface Platform {

    /** 游戏根目录，更新日志的磁盘缓存放在它下面的 `.cache/` 里 */
    val gameDir: Path

    companion object {
        // Stonecutter 在编译期选择加载器实现，避免 ServiceLoader 在异步线程上丢失 classloader。
        //? if fabric {
        val INSTANCE: Platform = FabricPlatform()
        //?} else {
        val INSTANCE: Platform = NeoForgePlatform()
        //?}
    }
}
