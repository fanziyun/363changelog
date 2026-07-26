pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven("https://maven.fabricmc.net/") { name = "Fabric" }
            }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }
}

plugins {
    // 本机没装 Java 25 时自动下载对应的 toolchain，
    // 否则 java_version = 25 会直接让构建失败
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "363changelog"

// common 装全部与加载器无关的代码；两个加载器子项目把 common 的源码一起编进各自的 jar
include("common")
include("fabric")
include("neoforge")
