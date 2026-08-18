package com.github.fanziyun.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment

@Config(name = "changelog363")
class ModConfig : ConfigData {

    @Comment("URL of the remote changelog JSON file (must return raw JSON, not an HTML page)")
    var changelogUrl: String =
        "https://raw.githubusercontent.com/fanziyun/363changelog/1.21.1/common/src/main/resources/changelog.json"

    @Comment("Modpack display name shown in the bottom-left of the title screen")
    var packName: String = "363Changelog"

    @Comment("Current modpack version number")
    var modpackVersion: String = "1.1.0"

    @Comment("Show changelog button on the title screen and pause screen")
    var showOnTitle: Boolean = true

    @Comment("Enable automatic update checking")
    var enableVersionCheck: Boolean = true

    @Comment("Seconds to wait for the remote changelog before falling back to cache/bundled (20-120)")
    @ConfigEntry.BoundedDiscrete(min = MIN_LOAD_TIMEOUT_SECONDS, max = MAX_LOAD_TIMEOUT_SECONDS)
    var loadTimeoutSeconds: Int = DEFAULT_LOAD_TIMEOUT_SECONDS

    @Comment("Distance in pixels between the version text and the bottom of the title screen")
    var versionYOffset: Int = 20

    @Comment("Display name of the external link")
    var externalLinkName: String = "项目主页"

    @Comment("URL of the external link")
    var externalLinkUrl: String = "https://github.com/fanziyun/363changelog"

    companion object {
        /** 加载超时默认值。[ChangelogLoader][com.github.fanziyun.data.ChangelogLoader] 的 API 级默认也取这里 */
        const val DEFAULT_LOAD_TIMEOUT_SECONDS = 30

        /**
         * 下限对齐"单次远程请求最坏耗时"（connect 5s + read 10s ≈ 15s），
         * 防止正常的慢速请求被误判成超时。
         *
         * 声明成 Long 是 @BoundedDiscrete 的要求（它的 min/max 是 long）；
         * Kotlin 只对字面量做隐式转换，具名的 Int 常量放进注解会类型不匹配。
         */
        const val MIN_LOAD_TIMEOUT_SECONDS = 20L

        /** 上限兜底防误填超大值——填得再大也不该让界面无限等下去 */
        const val MAX_LOAD_TIMEOUT_SECONDS = 120L
    }

    /**
     * 夹进合法区间后的端到端超时，毫秒。
     *
     * 由配置自己给出已经可用的值，调用方不必各自记得夹一遍：配置界面有
     * @BoundedDiscrete 拦着，但手改配置文件、或旧版本写下的值都可能越界。
     */
    val loadTimeoutMillis: Long
        get() = loadTimeoutSeconds.toLong()
            .coerceIn(MIN_LOAD_TIMEOUT_SECONDS, MAX_LOAD_TIMEOUT_SECONDS) * 1000L
}
