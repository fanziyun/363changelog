plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-neoforge")

val common = project(":common")
val modId = rootProject.property("mod_id") as String

// 与 Fabric 子项目一样，直接编译 common 的源码
sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
    resources.srcDir(common.file("src/main/resources"))
}

neoForge {
    version = rootProject.property("neoforge_version") as String

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory.set(file("runs/client"))
        }
    }
}

dependencies {
    // Kotlin 语言提供者：把 Kotlin 标准库带到 NeoForge 运行时
    implementation("thedarkcolour:kotlinforforge-neoforge:${rootProject.property("kotlin_for_forge_version")}")
    implementation("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to modId,
        "mod_name" to rootProject.property("mod_name"),
        "mod_author" to rootProject.property("mod_author"),
        "mod_license" to rootProject.property("mod_license"),
        "minecraft_version_range" to rootProject.property("minecraft_version_range"),
        "neoforge_loader_version_range" to rootProject.property("neoforge_loader_version_range"),
        "neoforge_version" to rootProject.property("neoforge_version"),
        "cloth_config_version" to rootProject.property("cloth_config_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(props) }
}
