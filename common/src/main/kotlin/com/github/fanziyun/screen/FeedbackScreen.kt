package com.github.fanziyun.screen

import com.github.fanziyun.ChangelogService
import com.github.fanziyun.feedback.FeedbackService
import com.github.fanziyun.util.ColorUtil
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 游戏内反馈表单。玩家输入反馈（多行）+ 可选联系方式，点「提交」后由
 * [FeedbackService] 在后台线程投递到 GitHub / Gitee，主线程只回显状态。
 */
class FeedbackScreen(private val parentScreen: Screen?) :
    Screen(Component.translatable("screen.changelog363.feedback.title")) {

    private companion object {
        const val FORM_WIDTH = 320
        const val FEEDBACK_HEIGHT = 88
        const val FEEDBACK_Y = 52
        const val CONTACT_Y = 164
        const val STATUS_Y = 196
        const val BUTTON_Y_MARGIN = 30
        const val BUTTON_WIDTH = 100
        const val BUTTON_GAP = 4
        const val MAX_FEEDBACK_CHARS = 2000
    }

    private enum class SubmitState { IDLE, SENDING }

    private var feedbackBox: MultiLineEditBox? = null
    private var contactBox: EditBox? = null
    private var submitButton: Button? = null
    private var submitState = SubmitState.IDLE
    private var statusMessage: Component? = null
    private var statusColor: Int = ColorUtil.GREY

    private val config get() = ChangelogService.config

    private val formTitle: String
        get() = config?.feedbackTitle?.trim()?.takeIf(String::isNotEmpty) ?: title.string

    private val placeholder: String
        get() = config?.feedbackPlaceholder?.trim()?.takeIf(String::isNotEmpty)
            ?: Component.translatable("screen.changelog363.feedback.placeholder").string

    override fun init() {
        super.init()
        val left = (width - FORM_WIDTH) / 2

        val box = MultiLineEditBox.builder()
            .setX(left)
            .setY(FEEDBACK_Y)
            .setPlaceholder(Component.literal(placeholder))
            .build(font, FORM_WIDTH, FEEDBACK_HEIGHT, Component.translatable("screen.changelog363.feedback.title"))
        box.setCharacterLimit(MAX_FEEDBACK_CHARS)
        box.setValueListener { updateSubmitState() }
        feedbackBox = addRenderableWidget(box)

        val contact = EditBox(
            font,
            left,
            CONTACT_Y,
            FORM_WIDTH,
            20,
            Component.translatable("screen.changelog363.feedback.contact"),
        )
        contact.setMaxLength(100)
        contactBox = addRenderableWidget(contact)

        val buttonLeft = width / 2 - (BUTTON_WIDTH * 2 + BUTTON_GAP) / 2
        val buttonY = height - BUTTON_Y_MARGIN
        submitButton = addRenderableWidget(
            Button.builder(Component.translatable("screen.changelog363.feedback.submit")) { submit() }
                .bounds(buttonLeft, buttonY, BUTTON_WIDTH, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.back")) { onClose() }
                .bounds(buttonLeft + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build()
        )

        setInitialFocus(box)
        updateSubmitState()
    }

    private fun submit() {
        val text = feedbackBox?.getValue()?.trim().orEmpty()
        if (text.isBlank()) {
            setStatus(
                Component.translatable("screen.changelog363.feedback.empty"),
                ColorUtil.YELLOW,
            )
            return
        }

        val cfg = config
        if (cfg == null || cfg.feedbackUrl.isBlank() || cfg.feedbackToken.isBlank()) {
            setStatus(
                Component.translatable("screen.changelog363.feedback.no_backend"),
                ColorUtil.YELLOW,
            )
            return
        }

        submitState = SubmitState.SENDING
        setStatus(Component.translatable("screen.changelog363.feedback.sending"), ColorUtil.GREY)
        updateSubmitState()

        val playerName = minecraft.player?.displayName?.string ?: "Player"
        FeedbackService.submit(
            packName = cfg.packName.orEmpty(),
            playerName = playerName,
            contact = contactBox?.getValue()?.trim().orEmpty(),
            version = cfg.modpackVersion.orEmpty(),
            text = text,
        ).whenComplete { result, _ ->
            minecraft.execute {
                if (minecraft.screen !== this) return@execute
                submitState = SubmitState.IDLE
                if (result.success) {
                    setStatus(
                        Component.translatable("screen.changelog363.feedback.success"),
                        ColorUtil.GREEN,
                    )
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

    private fun updateSubmitState() {
        val canSubmit = submitState != SubmitState.SENDING && !feedbackBox?.getValue().isNullOrBlank()
        submitButton?.active = canSubmit
    }

    private fun setStatus(message: Component, color: Int) {
        statusMessage = message
        statusColor = color
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val titleText = formTitle
        graphics.text(font, titleText, (width - font.width(titleText)) / 2, 20, ColorUtil.WHITE)

        val feedbackLabel = Component.translatable("screen.changelog363.feedback.label").string
        graphics.text(font, feedbackLabel, (width - FORM_WIDTH) / 2, FEEDBACK_Y - 12, ColorUtil.LIGHT_GREY)

        val contactLabel = Component.translatable("screen.changelog363.feedback.contact").string
        graphics.text(font, contactLabel, (width - FORM_WIDTH) / 2, CONTACT_Y - 12, ColorUtil.LIGHT_GREY)

        val message = statusMessage
        if (message != null) {
            val rendered = font.ellipsize(message.string, (FORM_WIDTH - 4).coerceAtLeast(0))
            graphics.text(
                font,
                rendered,
                (width - font.width(rendered)) / 2,
                STATUS_Y,
                statusColor,
            )
        }
    }

    override fun onClose() {
        minecraft.setScreen(parentScreen)
    }

    override fun isPauseScreen() = false
}
