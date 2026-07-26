pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

plugins {
    // 本机没装 Java 25 时自动下载对应的 toolchain，
    // 否则 build.gradle.kts 里的 languageVersion = 25 会直接让构建失败
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
