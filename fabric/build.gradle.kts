plugins {
    kotlin("jvm")
    // Loom 1.15 起 `net.fabricmc.fabric-loom` 这个 id 变成了"无重映射"版（面向不再混淆的 26.x）——
    // 它根本不注册 mappings / modImplementation 这些配置。
    // 1.21.1 还是混淆版本，要用 -remap 后缀的完整版；版本号继承根项目声明的同一个 loom 制品
    id("net.fabricmc.fabric-loom-remap")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-fabric")

val common = project(":common")

// 直接把 common 的源码编进本 jar，而不是依赖 common 的产物。
// 1.21.1 还是混淆版本，能这么共享的前提是本项目用 Mojang 官方映射（见下面的 mappings），
// 与 common 那边 NeoForm 给出的名字完全一致。
sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
    resources.srcDir(common.file("src/main/resources"))
}

loom {
    // 产物是 intermediary 名字，mixin 注解里的目标（类名、方法名）也得跟着重映射。
    // 常规方案是 Mixin 注解处理器生成 refmap，但那是 Java AP，看不见 Kotlin 写的 mixin，
    // 所以改用 tiny-remapper 的 mixin 扩展，remapJar 时直接改写字节码里的注解，不需要 refmap
    mixin {
        useLegacyMixinAp.set(false)
    }

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
    // 不用 Yarn 而用官方映射：这样 common 的源码在 :fabric 与 :neoforge 两边指的是同一批类名。
    // 产物由 remapJar 自动重映射回 intermediary，运行时不受影响。
    mappings(loom.officialMojangMappings())

    // 混淆版本上，依赖的模组都发布成 intermediary 名字，必须走 modImplementation
    // 让 Loom 把它们重映射到官方映射，否则编译期根本对不上（26.1.2 分支上用的是普通
    // implementation —— 那边 Minecraft 已经不混淆了，不需要这一层）
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("kotlin_loader_version")}")
    // 模组自身的代码没有用到 fabric-api，但 Cloth Config 与 Mod Menu 在运行时需要它
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")

    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    modImplementation("com.terraformersmc:modmenu:${rootProject.property("modmenu_version")}")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to rootProject.property("mod_id"),
        "mod_name" to rootProject.property("mod_name"),
        "mod_license" to rootProject.property("mod_license"),
        "fabric_loader_version" to rootProject.property("fabric_loader_version"),
        "fabric_loader_version_min" to rootProject.property("fabric_loader_version_min"),
        "kotlin_loader_version" to rootProject.property("kotlin_loader_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "pack.mcmeta")) { expand(props) }
}
