package com.github.fanziyun.neoforge

import com.github.fanziyun.platform.Platform
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.internal.BrandingControl
import java.nio.file.Path

/** 通过 `META-INF/services` 注册给 common。 */
class NeoForgePlatform : Platform {
    override val gameDir: Path get() = FMLPaths.GAMEDIR.get()

    /**
     * NeoForge 用 branding 行替换掉原版那行版本号，行数由它自己决定
     * （装了会往 branding 里加行的模组时还会更多），所以这里实地数一遍，
     * 而不是写死 2。参数与 TitleScreen 里那次调用保持一致。
     */
    override val titleScreenBrandingLines: Int
        get() {
            var lines = 0
            BrandingControl.forEachLine(true, true) { _, _ -> lines++ }
            return lines
        }
}
