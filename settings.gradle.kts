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
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "363changelog"

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) {
            loaders.forEach { loader ->
                version("$version-$loader", version).buildscript = "build.$loader.gradle.kts"
            }
        }

        listOf(
            "1.21",
            "1.21.1",
            "1.21.2",
            "1.21.3",
            "1.21.4",
            "1.21.5",
            "1.21.6",
            "1.21.7",
            "1.21.8",
            "1.21.9",
            "1.21.10",
            "1.21.11",
            "26.1.2",
        ).forEach { version -> match(version, "fabric", "neoforge") }

        vcsVersion = "26.1.2-fabric"
    }
}
