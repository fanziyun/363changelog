package com.github.fanziyun.smoke

import com.github.fanziyun.Changelog
import com.github.fanziyun.ChangelogService
import com.github.fanziyun.data.ChangelogLoader
import com.github.fanziyun.screen.ChangelogDetailScreen
import com.github.fanziyun.screen.ChangelogOverviewScreen
import com.github.fanziyun.screen.FeedbackScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot

object ClientSmokeTest {
    private const val PROPERTY = "changelog363.smokeTest"
    private const val SUCCESS_MARKER = "CHANGELOG363_SMOKE_OK"

    private var tick = 0

    val enabled: Boolean get() = System.getProperty(PROPERTY) == "true"

    fun onClientTick(minecraft: Minecraft) {
        if (!enabled) return
        if (minecraft.overlay != null) return

        when (++tick) {
            5 -> check(ChangelogLoader.loadBundledForSmokeTest()) { "Bundled changelog failed to load" }
            10 -> minecraft.setScreen(ChangelogOverviewScreen(null))
            20 -> {
                check(minecraft.screen is ChangelogOverviewScreen) { "Overview screen did not initialize" }
                val entry = ChangelogLoader.data.entries.firstOrNull()
                    ?: error("Bundled changelog has no entries")
                minecraft.setScreen(ChangelogDetailScreen(entry, null))
            }
            30 -> {
                check(minecraft.screen is ChangelogDetailScreen) { "Detail screen did not initialize" }
                check(ChangelogService.config?.feedbackEnabled == true) { "Feedback is disabled" }
                minecraft.setScreen(FeedbackScreen(null))
            }
            40 -> {
                check(minecraft.screen is FeedbackScreen) { "Feedback screen did not initialize" }
                runCatching {
//? if <1.21.6 {
                    Screenshot.grab(minecraft.gameDirectory, "changelog363-smoke.png", minecraft.mainRenderTarget) { }
//?} else {
                    Screenshot.grab(minecraft.gameDirectory, minecraft.mainRenderTarget) { }
//?}
                }
                Changelog.LOGGER.info(SUCCESS_MARKER)
                minecraft.stop()
            }
        }
    }
}
