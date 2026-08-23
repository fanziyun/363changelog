package com.github.fanziyun.feedback

import com.github.fanziyun.config.ModConfig
import java.net.URI

data class FeedbackEndpoint(
    val displayName: String,
    val baseUrl: String,
    val repo: String,
    val defaultPat: String,
    val oauthEnabled: Boolean,
    val oauthForceDeviceFlow: Boolean,
    val oauthClientId: String,
    val oauthClientSecret: String,
    val deviceCodeUrl: String,
    val authorizationUrl: String,
    val tokenUrl: String,
) {
    fun normalizedBaseUrl(): String {
        val value = baseUrl.trim().trimEnd('/')
        require(value.isNotBlank()) { "反馈服务 Base URL 未配置" }
        val uri = URI.create(value)
        require(uri.scheme == "http" || uri.scheme == "https") { "反馈服务 Base URL 必须使用 HTTP(S)" }
        require(uri.host != null) { "反馈服务 Base URL 无效" }
        return value
    }

    fun issueUrl(): String {
        val repository = repo.trim().trim('/')
        require(repository.matches(Regex("[^/\\s]+/[^/\\s]+"))) { "反馈仓库格式应为 owner/repo" }
        return "${normalizedBaseUrl()}/repos/$repository/issues"
    }

    fun oauthUrls(): Pair<String, String> {
        val device = validateOAuthUrl(deviceCodeUrl, "OAuth device-code URL")
        val token = validateOAuthUrl(tokenUrl, "OAuth token URL")
        return device to token
    }

    fun oauthAuthorizationUrl(): String = validateOAuthUrl(authorizationUrl, "OAuth authorization URL")

    /**
     * 是否走设备流。[playerPrefersDeviceFlow] 是玩家在反馈界面里的勾选，
     * 端点配置了 [oauthForceDeviceFlow] 时直接忽略它——本地回调流需要 client secret，
     * 而 secret 一旦随配置分发给玩家就等于公开泄露。
     */
    fun usesDeviceFlow(playerPrefersDeviceFlow: Boolean): Boolean =
        oauthForceDeviceFlow || playerPrefersDeviceFlow

    /** 玩家能否自己挑登录方式：只有启用 OAuth 且没被强制设备流的端点才给选择。 */
    fun allowsFlowChoice(): Boolean = oauthEnabled && !oauthForceDeviceFlow

    fun storageKey(): String = listOf(baseUrl.trim().trimEnd('/'), repo.trim().trim('/')).joinToString("|")

    companion object {
        fun from(config: ModConfig.FeedbackEndpoint): FeedbackEndpoint = FeedbackEndpoint(
            displayName = config.displayName,
            baseUrl = config.baseUrl,
            repo = config.repo,
            defaultPat = config.defaultPat,
            oauthEnabled = config.oauthEnabled,
            oauthForceDeviceFlow = config.oauthForceDeviceFlow,
            oauthClientId = config.oauthClientId,
            oauthClientSecret = config.oauthClientSecret,
            deviceCodeUrl = config.deviceCodeUrl,
            authorizationUrl = config.authorizationUrl,
            tokenUrl = config.tokenUrl,
        )

        private fun validateOAuthUrl(value: String, label: String): String {
            val trimmed = value.trim()
            val uri = URI.create(trimmed)
            require((uri.scheme == "http" || uri.scheme == "https") && uri.host != null) { "$label 无效" }
            return trimmed
        }
    }
}
