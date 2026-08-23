package com.github.fanziyun.screen

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.feedback.FeedbackService
import com.github.fanziyun.feedback.GitHubOAuth
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.net.URI

/**
 * 游戏内反馈表单。玩家填标题 + 内容（+ 可选联系方式），首次提交前用 GitHub 设备流
 * 登录（不要求填 PAT），登录后可重复提交。提交由 [FeedbackService] 在后台线程完成，
 * 界面只回显状态；整体视觉与更新日志界面保持同一套配色/面板语言。
 */
class FeedbackScreen(private val parentScreen: Screen?) :
    Screen(Component.translatable("screen.changelog363.feedback.title")) {

    private companion object {
        const val PANEL_W = 340
        const val PANEL_TOP = 38
        const val PANEL_BOTTOM = 210
        const val TITLE_LABEL_Y = 46
        const val TITLE_BOX_Y = 58
        const val TITLE_BOX_H = 20
        const val CONTENT_LABEL_Y = 84
        const val CONTENT_BOX_Y = 95
        const val CONTENT_BOX_H = 70
        const val CONTACT_LABEL_Y = 171
        const val CONTACT_BOX_Y = 182
        const val CONTACT_BOX_H = 20
        const val STATUS_Y = 216
        const val LOGIN_INFO_Y = 232
        const val BUTTON_WIDTH = 100
        const val BUTTON_GAP = 4
        const val BUTTON_Y_MARGIN = 30
        const val MAX_TITLE_CHARS = 80
        const val MAX_CONTENT_CHARS = 4000
        const val MAX_CONTACT_CHARS = 100
        const val SMALL_GAP = 8
        const val ROW_BACKGROUND = 0x1AFFFFFF
        const val ROW_ACCENT = 0xFF55FF55.toInt() // ColorUtil.GREEN
    }

    private enum class LoginState { NOT_LOGGED, LOGGING_IN, LOGGED_IN, LOGIN_FAILED }
    private enum class SubmitState { IDLE, SENDING }

    private var titleBox: EditBox? = null
    private var contentBox: MultiLineEditBox? = null
    private var contactBox: EditBox? = null
    private var submitButton: Button? = null

    private var loginState = LoginState.NOT_LOGGED
    private var submitState = SubmitState.IDLE
    private var deviceCode: GitHubOAuth.DeviceCode? = null
    private var statusMessage: Component? = null
    private var statusColor: Int = ColorUtil.GREY

    private val config get() = ChangelogService.config

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

        val title = EditBox(
            font,
            left + SMALL_GAP,
            TITLE_BOX_Y,
            PANEL_W - SMALL_GAP * 2,
            TITLE_BOX_H,
            Component.translatable("screen.changelog363.feedback.title_label"),
        )
        title.setMaxLength(MAX_TITLE_CHARS)
        title.setBordered(false)
        title.setTextColor(ColorUtil.WHITE)
        title.setHint(Component.literal(titlePlaceholder))
        title.setResponder { updateSubmitState() }
        titleBox = addRenderableWidget(title)

        val content = MultiLineEditBox.builder()
            .setX(left + SMALL_GAP)
            .setY(CONTENT_BOX_Y)
            .setPlaceholder(Component.literal(contentPlaceholder))
            .setShowBackground(false)
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
        contact.setBordered(false)
        contact.setTextColor(ColorUtil.WHITE)
        contact.setHint(Component.translatable("screen.changelog363.feedback.contact_hint"))
        contactBox = addRenderableWidget(contact)

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
        updateLoginStatus()
        updateSubmitState()
    }

    // ---- 提交流程 ----

    private fun onSubmit() {
        val clientId = config?.githubClientId?.trim().orEmpty()
        val repo = config?.feedbackRepo?.trim().orEmpty()

        if (clientId.isBlank()) {
            setStatus(Component.translatable("screen.changelog363.feedback.no_client_id"), ColorUtil.YELLOW)
            return
        }
        if (repo.isBlank() || !repo.contains('/')) {
            setStatus(Component.translatable("screen.changelog363.feedback.no_repo"), ColorUtil.YELLOW)
            return
        }
        if (contentBox?.getValue().isNullOrBlank()) {
            setStatus(Component.translatable("screen.changelog363.feedback.empty"), ColorUtil.YELLOW)
            return
        }

        val existing = GitHubOAuth.load()
        when {
            existing == null -> startDeviceFlow(clientId, repo)
            GitHubOAuth.isUsable(existing) -> doSubmit(existing, repo)
            GitHubOAuth.needsRefresh(existing) -> {
                setStatus(Component.translatable("screen.changelog363.feedback.sending"), ColorUtil.GREY)
                updateSubmitState()
                GitHubOAuth.refreshAsync(clientId, existing.refreshToken!!).whenComplete { refreshed, error ->
                    minecraft.execute {
                        if (minecraft.screen !== this) return@execute
                        if (error != null || refreshed == null) {
                            startDeviceFlow(clientId, repo)
                        } else {
                            GitHubOAuth.save(refreshed)
                            doSubmit(refreshed, repo)
                        }
                    }
                }
            }
            else -> startDeviceFlow(clientId, repo)
        }
    }

    private fun doSubmit(token: GitHubOAuth.Token, repo: String) {
        submitState = SubmitState.SENDING
        setStatus(Component.translatable("screen.changelog363.feedback.sending"), ColorUtil.GREY)
        updateSubmitState()

        val cfg = config
        val playerName = minecraft.player?.displayName?.string ?: "Player"
        val titleText = titleBox?.getValue()?.trim()?.takeIf(String::isNotEmpty)
            ?: buildFallbackTitle(cfg?.packName.orEmpty(), playerName, contentBox?.getValue().orEmpty())
        val body = buildBody(
            content = contentBox?.getValue().orEmpty(),
            playerName = playerName,
            version = cfg?.modpackVersion.orEmpty(),
            contact = contactBox?.getValue()?.trim().orEmpty(),
        )

        FeedbackService.submit(repo, titleText, body, token.accessToken).whenComplete { result, _ ->
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

    private fun startDeviceFlow(clientId: String, repo: String) {
        loginState = LoginState.LOGGING_IN
        deviceCode = null
        setStatus(Component.translatable("screen.changelog363.feedback.login.opening"), ColorUtil.GREY)
        updateSubmitState()

        val client = minecraft
        GitHubOAuth.requestDeviceCodeAsync(clientId).whenComplete { code, error ->
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

                GitHubOAuth.pollForTokenAsync(clientId, code.deviceCode, code.interval).whenComplete { token, pollError ->
                    client.execute {
                        if (client.screen !== this) return@execute
                        if (pollError != null || token == null) {
                            loginState = LoginState.LOGIN_FAILED
                            setStatus(
                                Component.translatable("screen.changelog363.feedback.login.fail", errorText(pollError)),
                                ColorUtil.YELLOW,
                            )
                        } else {
                            GitHubOAuth.save(token)
                            loginState = LoginState.LOGGED_IN
                            deviceCode = null
                            doSubmit(token, repo)
                        }
                        updateSubmitState()
                    }
                }
            }
        }
    }

    private fun updateLoginStatus() {
        val clientId = config?.githubClientId?.trim().orEmpty()
        loginState = when {
            clientId.isBlank() -> LoginState.LOGIN_FAILED
            GitHubOAuth.hasSession() -> LoginState.LOGGED_IN
            else -> LoginState.NOT_LOGGED
        }
    }

    private fun updateSubmitState() {
        val canSubmit = submitState != SubmitState.SENDING &&
            loginState != LoginState.LOGGING_IN &&
            !contentBox?.getValue().isNullOrBlank()
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

    private fun buildBody(content: String, playerName: String, version: String, contact: String): String = buildString {
        append(content.trim())
        append("\n\n---")
        append("\nFrom: ").append(playerName)
        if (version.isNotBlank()) append("\nVersion: ").append(version)
        if (contact.isNotBlank()) append("\nContact: ").append(contact)
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
