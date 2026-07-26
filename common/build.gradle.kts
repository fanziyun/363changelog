plugins {
    kotlin("jvm")
    // NeoForm 模式：只要原版 Minecraft，不带任何加载器。
    // 1.21.1 仍是混淆版本，NeoForm 反混淆到 Mojang 官方映射；
    // :fabric 那边也指定了同一套映射，所以这里编出来的类在两个加载器上都能直接用。
    id("net.neoforged.moddev")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-common")

neoForge {
    neoFormVersion = rootProject.property("neo_form_version") as String
}

dependencies {
    // Fabric 与 NeoForge 都内置 Fabric Mixin，因此 mixin 可以放在 common 里编译
    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}")
    // Cloth Config 提供 ModConfig 上的注解与 AutoConfig 调用。
    // 1.21.1 上平台无关的 cloth-config 制品是 intermediary 名字（getConfigScreen 返回 class_437），
    // 和 NeoForm 给出的官方映射对不上，所以 common 借用 mojmap 命名的 NeoForge 构建来编译；
    // 运行时用的是各加载器自己带的 Cloth Config，这里只是编译期借个名字
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}") {
        isTransitive = false
    }
}

// common 只提供源码给 :fabric / :neoforge，自身不产出可用的模组 jar。
// 不关掉的话 common/build/libs/ 下会多出一个既没有 fabric.mod.json、
// 也没有 neoforge.mods.toml 的"伪模组"jar，容易被误当成产物分发。
// compileKotlin 仍然照常执行 —— 它才是"common 必须与加载器无关"这条约束的守卫。
tasks.jar { enabled = false }
