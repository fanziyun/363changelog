package com.github.fanziyun.screen

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.feedback.FeedbackService
import com.github.fanziyun.feedback.FeedbackEndpoint
import com.github.fanziyun.feedback.GitHubOAuth
import com.github.fanziyun.feedback.IssueBody
import com.github.fanziyun.feedback.PersonalAccessTokens
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.net.URI

/**
 * 游戏内反馈表单。玩家填标题 + 内容（+ 可选联系方式），首次提交前用 GitHub 设备流
 * 支持 OAuth 设备流和 PAT 两种鉴权。提交由 [FeedbackService] 在后台线程完成，
 * 界面只回显状态；整体视觉与更新日志界面保持同一套配色/面板语言。
 */
class FeedbackScreen(private val parentScreen: Screen?) :
    Screen(Component.translatable("screen.changelog363.feedback.title")) {

    private companion object {
        const val PANEL_W = 380
        const val PANEL_TOP = 38
        const val PANEL_BOTTOM = 300
        const val ENDPOINT_Y = 42
        const val AUTH_Y = 68
        const val PAT_Y = 96
        const val TITLE_LABEL_Y = 128
        const val TITLE_BOX_Y = 140
        const val TITLE_BOX_H = 20
        const val CONTENT_LABEL_Y = 174
        const val CONTENT_BOX_Y = 186
        const val CONTENT_BOX_H = 62
        const val CONTACT_LABEL_Y = 258
        const val CONTACT_BOX_Y = 270
        const val CONTACT_BOX_H = 20
        const val STATUS_Y = 306
        const val LOGIN_INFO_Y = 322
        const val BUTTON_WIDTH = 100
        const val BUTTON_GAP = 4
        const val BUTTON_Y_MARGIN = 30
        const val MAX_TITLE_CHARS = 80
        const val MAX_CONTENT_CHARS = 4000
        const val MAX_CONTACT_CHARS = 100
        const val SMALL_GAP = 8
        const val ROW_BACKGROUND = 0x66000000
        const val SECTION_BACKGROUND = 0x33000000
        const val FIELD_BACKGROUND = 0x22000000
        const val ROW_ACCENT = 0xFF55FF55.toInt() // ColorUtil.GREEN
    }

    private enum class LoginState { NOT_LOGGED, LOGGING_IN, LOGGED_IN, LOGIN_FAILED }
    private enum class SubmitState { IDLE, SENDING }
    private enum class AuthMode { OAUTH, PAT }

    private var titleBox: EditBox? = null
    private var contentBox: MultiLineEditBox? = null
    private var contactBox: EditBox? = null
    private var submitButton: Button? = null
    private var endpointSelector: CycleButton<FeedbackEndpoint>? = null
    private var authSelector: CycleButton<AuthMode>? = null
    private var deviceFlowBox: Checkbox? = null
    private var patBox: EditBox? = null
    private var savePatBox: Checkbox? = null

    private var loginState = LoginState.NOT_LOGGED
    private var submitState = SubmitState.IDLE
    private var deviceCode: GitHubOAuth.DeviceCode? = null
    private var statusMessage: Component? = null
    private var statusColor: Int = ColorUtil.GREY

    private val config get() = ChangelogService.config

    private val endpoints: List<FeedbackEndpoint>
        get() = config?.feedbackEndpoints?.map(FeedbackEndpoint.Companion::from).orEmpty()

    private val formTitle: String
        get() = config?.feedbackTitle?.trim()?.takeIf(String::isNotEmpty) ?: title.string

    private val titlePlaceholder: String
        get() = config?.feedbackTitlePlaceholder?.trim()?.takeIf(String::isNotEmpty)
            ?: Component.translatable("screen.changelog363.feedback.title_placeholder").string

    private val contentPlaceholder: String
        get() = config?.feedbackPlaceholder?.trim()?.takeIf(String::isNotEmpty)
            ?: Component.translatable("screen.changelog363.feedback.placeholder").string

    override fun init() {
        super.init()
        val left = (width - PANEL_W) / 2

        val configuredEndpoints = endpoints
        if (configuredEndpoints.isNotEmpty()) {
            endpointSelector = addRenderableWidget(
                CycleButton.builder<FeedbackEndpoint>({ Component.literal(it.displayName.ifBlank { it.baseUrl }) }, configuredEndpoints[0])
                    .withValues(configuredEndpoints)
                    .create(left + SMALL_GAP, ENDPOINT_Y, PANEL_W - SMALL_GAP * 2, 20, Component.translatable("screen.changelog363.feedback.service")) { _, endpoint ->
                        applyPatPreset(endpoint)
                        updateAuthWidgets()
                    }
            )
        }
        authSelector = addRenderableWidget(
            CycleButton.builder<AuthMode>({
                if (it == AuthMode.OAUTH) Component.translatable("screen.changelog363.feedback.auth.oauth")
                else Component.translatable("screen.changelog363.feedback.auth.pat")
            }, AuthMode.OAUTH)
                .withValues(AuthMode.entries)
                .create(left + SMALL_GAP, AUTH_Y, 116, 20, Component.translatable("screen.changelog363.feedback.auth")) { _, _ -> updateAuthWidgets() }
        )
        deviceFlowBox = addRenderableWidget(
            Checkbox.builder(Component.translatable("screen.changelog363.feedback.use_device_flow"), font)
                .pos(left + 136, AUTH_Y)
                .selected(true)
                .onValueChange { _, _ -> updateSubmitState() }
                .build()
        )

        val title = EditBox(
            font,
            left + SMALL_GAP,
            TITLE_BOX_Y,
            PANEL_W - SMALL_GAP * 2,
            TITLE_BOX_H,
            Component.translatable("screen.changelog363.feedback.title_label"),
        )
        title.setMaxLength(MAX_TITLE_CHARS)
        title.setBordered(true)
        title.setTextColor(ColorUtil.WHITE)
        title.setHint(Component.literal(titlePlaceholder))
        title.setResponder { updateSubmitState() }
        titleBox = addRenderableWidget(title)

        val content = MultiLineEditBox.builder()
            .setX(left + SMALL_GAP)
            .setY(CONTENT_BOX_Y)
            .setPlaceholder(Component.literal(contentPlaceholder))
            .setShowBackground(true)
            .setShowDecorations(true)
            .setTextColor(ColorUtil.WHITE)
            .build(font, PANEL_W - SMALL_GAP * 2, CONTENT_BOX_H, Component.translatable("screen.changelog363.feedback.label"))
        content.setCharacterLimit(MAX_CONTENT_CHARS)
        content.setValueListener { updateSubmitState() }
        contentBox = addRenderableWidget(content)

        val contact = EditBox(
            font,
            left + SMALL_GAP,
            CONTACT_BOX_Y,
            PANEL_W - SMALL_GAP * 2,
            CONTACT_BOX_H,
            Component.translatable("screen.changelog363.feedback.contact"),
        )
        contact.setMaxLength(MAX_CONTACT_CHARS)
        contact.setBordered(true)
        contact.setTextColor(ColorUtil.WHITE)
        contact.setHint(Component.translatable("screen.changelog363.feedback.contact_hint"))
        contactBox = addRenderableWidget(contact)

        val pat = EditBox(font, left + SMALL_GAP, PAT_Y, 216, 20, Component.literal("PAT"))
        pat.setMaxLength(500)
        pat.setBordered(true)
        pat.setTextColor(ColorUtil.WHITE)
        pat.setHint(Component.translatable("screen.changelog363.feedback.pat_hint"))
        pat.addFormatter { value, _ -> FormattedCharSequence.forward("•".repeat(value.length), Style.EMPTY) }
        pat.setResponder { updateSubmitState() }
        patBox = addRenderableWidget(pat)
        savePatBox = addRenderableWidget(
            Checkbox.builder(Component.translatable("screen.changelog363.feedback.save_pat"), font)
                .pos(left + 232, PAT_Y)
                .selected(false)
                .build()
        )

        val buttonLeft = width / 2 - (BUTTON_WIDTH * 2 + BUTTON_GAP) / 2
        val buttonY = height - BUTTON_Y_MARGIN
        submitButton = addRenderableWidget(
            Button.builder(Component.translatable("screen.changelog363.feedback.submit")) { onSubmit() }
                .bounds(buttonLeft, buttonY, BUTTON_WIDTH, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(buttonLeft + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build()
        )

        setInitialFocus(content)
        applyPatPreset(endpointSelector?.getValue())
        updateLoginStatus()
        updateAuthWidgets()
        updateSubmitState()
    }

    // ---- 提交流程 ----

    private fun onSubmit() {
        val endpoint = endpointSelector?.getValue()
        if (endpoint == null) {
            setStatus(Component.translatable("screen.changelog363.feedback.no_service"), ColorUtil.YELLOW)
            return
        }
        if (contentBox?.getValue().isNullOrBlank()) {
            setStatus(Component.translatable("screen.changelog363.feedback.empty"), ColorUtil.YELLOW)
            return
        }

        if (authSelector?.getValue() == AuthMode.PAT) {
            val pat = patBox?.getValue()?.trim().orEmpty().ifBlank { PersonalAccessTokens.load(endpoint.storageKey()).orEmpty() }
            if (pat.isBlank()) {
                setStatus(Component.translatable("screen.changelog363.feedback.pat_empty"), ColorUtil.YELLOW)
                return
            }
            if (savePatBox?.selected() == true) PersonalAccessTokens.save(endpoint.storageKey(), pat)
            doSubmit(GitHubOAuth.Token(pat, null, 0L), endpoint)
            return
        }

        if (endpoint.oauthClientId.isBlank()) {
            setStatus(Component.translatable("screen.changelog363.feedback.oauth_no_client_id"), ColorUtil.YELLOW)
            return
        }
        if (!endpoint.oauthEnabled) {
            setStatus(Component.translatable("screen.changelog363.feedback.pat_only"), ColorUtil.YELLOW)
            return
        }
        val existing = GitHubOAuth.load(endpoint.storageKey())
        val (_, tokenUrl) = try { endpoint.oauthUrls() } catch (exception: IllegalArgumentException) {
            setStatus(Component.translatable("screen.changelog363.feedback.oauth_invalid_url", exception.message ?: ""), ColorUtil.YELLOW)
            return
        }
        when {
            existing == null -> startOAuthFlow(endpoint)
            GitHubOAuth.isUsable(existing) -> doSubmit(existing, endpoint)
            GitHubOAuth.needsRefresh(existing) -> {
                setStatus(Component.translatable("screen.changelog363.feedback.sending"), ColorUtil.GREY)
                updateSubmitState()
                GitHubOAuth.refreshAsync(endpoint.oauthClientId, tokenUrl, existing.refreshToken!!).whenComplete { refreshed, error ->
                    minecraft.execute {
                        if (minecraft.screen !== this) return@execute
                        if (error != null || refreshed == null) {
                            startOAuthFlow(endpoint)
                        } else {
                            GitHubOAuth.save(endpoint.storageKey(), refreshed)
                            doSubmit(refreshed, endpoint)
                        }
                    }
                }
            }
            else -> startOAuthFlow(endpoint)
        }
    }

    private fun applyPatPreset(endpoint: FeedbackEndpoint?) {
        val value = endpoint?.let {
            PersonalAccessTokens.load(it.storageKey())?.takeIf(String::isNotBlank)
                ?: it.defaultPat.trim().takeIf(String::isNotBlank)
        }.orEmpty()
        patBox?.setValue(value)
    }

    private fun doSubmit(token: GitHubOAuth.Token, endpoint: FeedbackEndpoint) {
        submitState = SubmitState.SENDING
        setStatus(Component.translatable("screen.changelog363.feedback.sending"), ColorUtil.GREY)
        updateSubmitState()

        val cfg = config
        val playerName = minecraft.player?.name?.string ?: "Player"
        val titleText = titleBox?.getValue()?.trim()?.takeIf(String::isNotEmpty)
            ?: buildFallbackTitle(cfg?.packName.orEmpty(), playerName, contentBox?.getValue().orEmpty())
        val body = IssueBody.build(
            content = contentBox?.getValue().orEmpty(),
            playerName = playerName,
            version = cfg?.modpackVersion.orEmpty(),
            contact = contactBox?.getValue()?.trim().orEmpty(),
        )

        FeedbackService.submit(endpoint, titleText, body, token.accessToken).whenComplete { result, _ ->
            minecraft.execute {
                if (minecraft.screen !== this) return@execute
                submitState = SubmitState.IDLE
                if (result.success) {
                    setStatus(Component.translatable("screen.changelog363.feedback.success"), ColorUtil.GREEN)
                } else {
                    setStatus(
                        Component.translatable("screen.changelog363.feedback.fail", result.message),
                        ColorUtil.YELLOW,
                    )
                }
                updateSubmitState()
            }
        }
    }

    private fun startOAuthFlow(endpoint: FeedbackEndpoint) {
        if (deviceFlowBox?.selected() != true) {
            startLocalCallbackFlow(endpoint)
        } else {
            startDeviceFlow(endpoint)
        }
    }

    private fun startDeviceFlow(endpoint: FeedbackEndpoint) {
        loginState = LoginState.LOGGING_IN
        deviceCode = null
        setStatus(Component.translatable("screen.changelog363.feedback.login.opening"), ColorUtil.GREY)
        updateSubmitState()

        val client = minecraft
        val (deviceUrl, tokenUrl) = try { endpoint.oauthUrls() } catch (exception: IllegalArgumentException) {
            setStatus(Component.translatable("screen.changelog363.feedback.oauth_invalid_url", exception.message ?: ""), ColorUtil.YELLOW)
            updateSubmitState()
            return
        }
        GitHubOAuth.requestDeviceCodeAsync(endpoint.oauthClientId, deviceUrl).whenComplete { code, error ->
            client.execute {
                if (client.screen !== this) return@execute
                if (error != null || code == null) {
                    loginState = LoginState.LOGIN_FAILED
                    setStatus(
                        Component.translatable("screen.changelog363.feedback.login.fail", errorText(error)),
                        ColorUtil.YELLOW,
                    )
                    updateSubmitState()
                    return@execute
                }

                deviceCode = code
                setStatus(Component.translatable("screen.changelog363.feedback.login.await"), ColorUtil.LIGHT_GREY)
                runCatching { ConfirmLinkScreen.confirmLinkNow(this, URI.create(code.verificationUri)) }

                GitHubOAuth.pollForTokenAsync(endpoint.oauthClientId, tokenUrl, code.deviceCode, code.interval).whenComplete { token, pollError ->
                    client.execute {
                        if (client.screen !== this) return@execute
                        if (pollError != null || token == null) {
                            loginState = LoginState.LOGIN_FAILED
                            setStatus(
                                Component.translatable("screen.changelog363.feedback.login.fail", errorText(pollError)),
                                ColorUtil.YELLOW,
                            )
                        } else {
                            GitHubOAuth.save(endpoint.storageKey(), token)
                            loginState = LoginState.LOGGED_IN
                            deviceCode = null
                            doSubmit(token, endpoint)
                        }
                        updateSubmitState()
                    }
                }
            }
        }
    }

    private fun startLocalCallbackFlow(endpoint: FeedbackEndpoint) {
        loginState = LoginState.LOGGING_IN
        deviceCode = null
        setStatus(Component.translatable("screen.changelog363.feedback.login.opening"), ColorUtil.GREY)
        updateSubmitState()

        val (_, tokenUrl) = try { endpoint.oauthUrls() } catch (exception: IllegalArgumentException) {
            setStatus(Component.literal(exception.message ?: "OAuth URL 无效"), ColorUtil.YELLOW)
            loginState = LoginState.LOGIN_FAILED
            updateSubmitState()
            return
        }
        val authorizationUrl = try { endpoint.oauthAuthorizationUrl() } catch (exception: IllegalArgumentException) {
            setStatus(Component.translatable("screen.changelog363.feedback.oauth_invalid_url", exception.message ?: ""), ColorUtil.YELLOW)
            loginState = LoginState.LOGIN_FAILED
            updateSubmitState()
            return
        }
        val flow = try {
            GitHubOAuth.startLocalServerFlow(endpoint.oauthClientId, endpoint.oauthClientSecret, authorizationUrl, tokenUrl)
        } catch (exception: Exception) {
            setStatus(Component.translatable("screen.changelog363.feedback.local_callback_failed", errorText(exception)), ColorUtil.YELLOW)
            loginState = LoginState.LOGIN_FAILED
            updateSubmitState()
            return
        }
        setStatus(Component.translatable("screen.changelog363.feedback.login.await"), ColorUtil.LIGHT_GREY)
        runCatching { ConfirmLinkScreen.confirmLinkNow(this, flow.authorizationUri) }
        flow.tokenFuture.whenComplete { token, error ->
            minecraft.execute {
                if (minecraft.screen !== this) return@execute
                if (error != null || token == null) {
                    loginState = LoginState.LOGIN_FAILED
                    setStatus(
                        Component.translatable("screen.changelog363.feedback.login.fail", errorText(error)),
                        ColorUtil.YELLOW,
                    )
                } else {
                    GitHubOAuth.save(endpoint.storageKey(), token)
                    loginState = LoginState.LOGGED_IN
                    doSubmit(token, endpoint)
                }
                updateSubmitState()
            }
        }
    }

    private fun updateLoginStatus() {
        val endpoint = endpointSelector?.getValue()
        loginState = when {
            endpoint == null || endpoint.oauthClientId.isBlank() -> LoginState.LOGIN_FAILED
            GitHubOAuth.hasSession(endpoint.storageKey()) -> LoginState.LOGGED_IN
            else -> LoginState.NOT_LOGGED
        }
    }

    private fun updateAuthWidgets() {
        var patMode = authSelector?.getValue() == AuthMode.PAT
        val oauthAvailable = endpointSelector?.getValue()?.oauthEnabled == true
        if (!oauthAvailable && !patMode) {
            authSelector?.setValue(AuthMode.PAT)
            patMode = true
        }
        val canChangeAuth = loginState != LoginState.LOGGING_IN
        authSelector?.active = oauthAvailable && canChangeAuth
        endpointSelector?.active = canChangeAuth
        patBox?.visible = patMode
        patBox?.active = patMode
        savePatBox?.visible = patMode
        savePatBox?.active = patMode
        deviceFlowBox?.visible = !patMode && oauthAvailable
        deviceFlowBox?.active = !patMode && oauthAvailable && canChangeAuth
        updateLoginStatus()
        updateSubmitState()
    }

    private fun updateSubmitState() {
        val canSubmit = submitState != SubmitState.SENDING &&
            loginState != LoginState.LOGGING_IN &&
            !contentBox?.getValue().isNullOrBlank() &&
            (authSelector?.getValue() == AuthMode.PAT || endpointSelector != null)
        submitButton?.active = canSubmit
    }

    // ---- 渲染 ----

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawPanel(graphics)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val currentTitle = formTitle
        graphics.text(font, currentTitle, (width - font.width(currentTitle)) / 2, 20, ColorUtil.WHITE)
        drawLabels(graphics)
        drawStatus(graphics)
        drawLoginInfo(graphics)
    }

    private fun drawPanel(graphics: GuiGraphicsExtractor) {
        val left = (width - PANEL_W) / 2
        graphics.fill(left, PANEL_TOP, left + PANEL_W, PANEL_BOTTOM, ROW_BACKGROUND)
        graphics.fill(left, PANEL_TOP, left + 4, PANEL_BOTTOM, ROW_ACCENT)
        graphics.fill(left + SMALL_GAP, ENDPOINT_Y - 4, left + PANEL_W - SMALL_GAP, ENDPOINT_Y + 24, SECTION_BACKGROUND)
        graphics.fill(left + SMALL_GAP, AUTH_Y - 4, left + PANEL_W - SMALL_GAP, PAT_Y + 24, SECTION_BACKGROUND)
        graphics.fill(left + SMALL_GAP, TITLE_LABEL_Y - 6, left + PANEL_W - SMALL_GAP, CONTENT_BOX_Y + CONTENT_BOX_H + 4, FIELD_BACKGROUND)
        graphics.fill(left + SMALL_GAP, CONTACT_LABEL_Y - 6, left + PANEL_W - SMALL_GAP, CONTACT_BOX_Y + CONTACT_BOX_H + 4, FIELD_BACKGROUND)
    }

    private fun drawLabels(graphics: GuiGraphicsExtractor) {
        val left = (width - PANEL_W) / 2
        val titleLabel = Component.translatable("screen.changelog363.feedback.title_label").string
        graphics.text(font, titleLabel, left + SMALL_GAP, TITLE_LABEL_Y, ColorUtil.LIGHT_GREY)
        val contentLabel = Component.translatable("screen.changelog363.feedback.label").string
        graphics.text(font, contentLabel, left + SMALL_GAP, CONTENT_LABEL_Y, ColorUtil.LIGHT_GREY)
        val contactLabel = Component.translatable("screen.changelog363.feedback.contact").string
        graphics.text(font, contactLabel, left + SMALL_GAP, CONTACT_LABEL_Y, ColorUtil.LIGHT_GREY)
    }

    private fun drawStatus(graphics: GuiGraphicsExtractor) {
        val message = statusMessage ?: return
        val rendered = font.ellipsize(message.string, (PANEL_W - 4).coerceAtLeast(0))
        graphics.text(font, rendered, (width - font.width(rendered)) / 2, STATUS_Y, statusColor)
    }

    private fun drawLoginInfo(graphics: GuiGraphicsExtractor) {
        val code = deviceCode ?: return
        val uriText = Component.translatable(
            "screen.changelog363.feedback.login.uri",
            code.verificationUri,
        ).string
        val codeText = Component.translatable(
            "screen.changelog363.feedback.login.code",
            code.userCode,
        ).string
        graphics.text(font, font.ellipsize(uriText, (PANEL_W - 4).coerceAtLeast(0)), (width - PANEL_W) / 2, LOGIN_INFO_Y, ColorUtil.LIGHT_GREY)
        graphics.text(font, font.ellipsize(codeText, (PANEL_W - 4).coerceAtLeast(0)), (width - PANEL_W) / 2, LOGIN_INFO_Y + 14, ColorUtil.GREEN)
    }

    private fun setStatus(message: Component, color: Int) {
        statusMessage = message
        statusColor = color
    }

    // ---- 工具 ----

    private fun buildFallbackTitle(packName: String, playerName: String, content: String): String {
        val preview = content.trim().replace(Regex("\\s+"), " ")
        val truncated = if (preview.length <= 30) preview else preview.take(30) + "…"
        return if (packName.isBlank()) "$playerName: $truncated" else "[$packName] $playerName: $truncated"
    }

    private fun errorText(throwable: Throwable?): String =
        (throwable as? Exception)?.message?.takeIf(String::isNotBlank)
            ?: throwable?.javaClass?.simpleName
            ?: "未知错误"

    override fun onClose() {
        minecraft.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false
}
