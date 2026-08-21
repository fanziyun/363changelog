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

    @Comment("Show the in-game feedback button on the changelog overview screen")
    var feedbackEnabled: Boolean = true

    @Comment("Feedback form title")
    var feedbackTitle: String = "意见反馈"

    @Comment("Feedback text field placeholder")
    var feedbackPlaceholder: String = "请输入您遇到的问题或建议…"

    @Comment("Feedback API URL. Use https://api.github.com/repos/<owner>/<repo>/issues (GitHub) or https://gitee.com/api/v5/repos/<owner>/<repo>/issues (Gitee, China-accessible)")
    var feedbackUrl: String = "https://api.github.com/repos/fanziyun/363changelog/issues"

    @Comment("API token. GitHub: personal access token (repo scope). Gitee: personal access token. Sent as Authorization: Bearer (GitHub) / access_token (Gitee)")
    var feedbackToken: String = ""

    companion object {
        /** 用户可配置的加载超时默认值；ChangelogLoader 的 API 级兜底默认与其保持一致 */
        const val DEFAULT_LOAD_TIMEOUT_SECONDS = 30
    }
}
