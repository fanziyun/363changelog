pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.minecraftforge.net") { name = "MinecraftForge" }
        maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "363changelog"

include("common")
include("forge")
