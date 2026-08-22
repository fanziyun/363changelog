package com.github.fanziyun.config

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment

const val DEFAULT_LOAD_TIMEOUT_SECONDS = 30

@Config(name = "changelog363")
class ModConfig : ConfigData {

    class FeedbackEndpoint {
        @Comment("Display name shown in the feedback form")
        var displayName: String = "363Changelog GitHub"

        @Comment("GitHub API base URL, for example https://api.github.com")
        var baseUrl: String = "https://api.github.com"

        @Comment("Target repository in owner/repo form")
        var repo: String = "fanziyun/363changelog"

        @Comment("Whether this endpoint supports OAuth. Disable for PAT-only services.")
        var oauthEnabled: Boolean = true

        @Comment("OAuth device-flow client ID; leave blank to disable OAuth for this endpoint")
        var oauthClientId: String = "Ov23liAM2iYE4alTOVOj"

        @Comment("Optional OAuth client secret, used by local-server callback flows when required by the provider")
        var oauthClientSecret: String = ""

        @Comment("OAuth device-code URL")
        var deviceCodeUrl: String = "https://github.com/login/device/code"

        @Comment("OAuth authorization URL used by the local-server callback flow")
        var authorizationUrl: String = "https://github.com/login/oauth/authorize"

        @Comment("OAuth token URL")
        var tokenUrl: String = "https://github.com/login/oauth/access_token"
    }

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

    @Comment("Feedback title field placeholder")
    var feedbackTitlePlaceholder: String = "一句话概括您的问题"

    @Comment("Feedback content field placeholder")
    var feedbackPlaceholder: String = "详细描述您遇到的问题或建议…"

    @Comment("Feedback services. Each entry can target a different GitHub/GitHub Enterprise API.")
    var feedbackEndpoints: MutableList<FeedbackEndpoint> = mutableListOf(
        FeedbackEndpoint(),
        FeedbackEndpoint().apply {
            displayName = "363Changelog GitHub CN Proxy"
            baseUrl = "https://github-issue-proxy.mangosmoke-a7306694.japaneast.azurecontainerapps.io"
            oauthEnabled = false
            oauthClientId = ""
        },
    )

}
