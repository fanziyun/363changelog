package com.github.fanziyun.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment

@Config(name = "changelog363")
class ModConfig : ConfigData {

    @Comment("URL of the remote changelog JSON file (must return raw JSON, not an HTML page)")
    var changelogUrl: String =
        "https://raw.githubusercontent.com/fanziyun/363changelog/26.1.2/common/src/main/resources/changelog.json"

    @Comment("Modpack display name shown in the bottom-left of the title screen")
    var packName: String = "363Changelog"

    @Comment("Current modpack version number")
    var modpackVersion: String = "1.1.0"

    @Comment("Show changelog button on the title screen and pause screen")
    var showOnTitle: Boolean = true

    @Comment("Enable automatic update checking")
    var enableVersionCheck: Boolean = true

    @Comment("Seconds to wait for the remote changelog before falling back to cache/bundled (20-120)")
    @ConfigEntry.BoundedDiscrete(min = 20, max = 120)
    var loadTimeoutSeconds: Int = DEFAULT_LOAD_TIMEOUT_SECONDS

    @Comment("Distance in pixels between the version text and the bottom of the title screen")
    var versionYOffset: Int = 20

    @Comment("Display name of the external link")
    var externalLinkName: String = "项目主页"

    @Comment("URL of the external link")
    var externalLinkUrl: String = "https://github.com/fanziyun/363changelog"

    companion object {
        /** 用户可配置的加载超时默认值；ChangelogLoader 的 API 级兜底默认与其保持一致 */
        const val DEFAULT_LOAD_TIMEOUT_SECONDS = 30
    }
}
