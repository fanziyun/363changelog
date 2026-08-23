plugins {
    kotlin("jvm")
    // NeoForm 模式：只要原版 Minecraft，不带任何加载器。
    // 26.1 起 Minecraft 不再混淆，所以这里编出来的类在两个加载器上都能直接用。
    id("net.neoforged.moddev")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-common")

neoForge {
    neoFormVersion = rootProject.property("neo_form_version") as String
}

dependencies {
    // Fabric 与 NeoForge 都内置 Fabric Mixin，因此 mixin 可以放在 common 里编译
    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}")
    // 平台无关的 Cloth Config：ModConfig 上的注解与 AutoConfig 调用都来自它
    compileOnly("me.shedaniel.cloth:cloth-config:${rootProject.property("cloth_config_version")}")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// common 只提供源码给 :fabric / :neoforge，自身不产出可用的模组 jar。
// 不关掉的话 common/build/libs/ 下会多出一个既没有 fabric.mod.json、
// 也没有 neoforge.mods.toml 的"伪模组"jar，容易被误当成产物分发。
// compileKotlin 仍然照常执行 —— 它才是"common 必须与加载器无关"这条约束的守卫。
tasks.jar { enabled = false }
