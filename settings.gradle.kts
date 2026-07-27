pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.minecraftforge.net") { name = "MinecraftForge" }
        maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
        maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "363changelog"
