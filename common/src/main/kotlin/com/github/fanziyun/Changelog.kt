package com.github.fanziyun

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 模组标识与共享日志器。
 *
 * 入口点由各加载器子项目提供（Fabric 的 `ClientModInitializer` / NeoForge 的 `@Mod`），
 * 它们都只是调用 [ChangelogService.init]。
 */
object Changelog {
    const val MOD_ID = "changelog363"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
}
