package com.github.fanziyun.runtime

/** Immutable settings copied from the stable Fabric host into one runtime. */
data class RuntimeSettings(
    val changelogUrl: String,
    val packName: String,
    val modpackVersion: String,
    val showOnTitle: Boolean,
    val enableVersionCheck: Boolean,
    val loadTimeoutSeconds: Int,
    val versionYOffset: Int,
    val externalLinkName: String,
    val externalLinkUrl: String,
)
