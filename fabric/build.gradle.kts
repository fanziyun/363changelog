plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-fabric")

val common = project(":common")

// 直接把 common 的源码编进本 jar，而不是依赖 common 的产物。
// 26.1 起 Minecraft 不再混淆，两个加载器引用的是同一套官方名字，
// 所以共享源码不需要任何重映射中间层。
sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
    resources.srcDir(common.file("src/main/resources"))
}

loom {
    runs {
        named("client") {
            client()
            ideConfigGenerated(true)
            runDir("runs/client")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("kotlin_loader_version")}")
    // 模组自身的代码没有用到 fabric-api，但 Cloth Config 与 Mod Menu 在运行时需要它
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")

    implementation("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    implementation("com.terraformersmc:modmenu:${rootProject.property("modmenu_version")}")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to rootProject.property("mod_id"),
        "mod_name" to rootProject.property("mod_name"),
        "mod_license" to rootProject.property("mod_license"),
        "minecraft_version" to rootProject.property("minecraft_version"),
        "fabric_loader_version" to rootProject.property("fabric_loader_version"),
        "kotlin_loader_version" to rootProject.property("kotlin_loader_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "pack.mcmeta")) { expand(props) }
}
