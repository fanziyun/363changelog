plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-common")

neoForge {
    neoFormVersion = rootProject.property("neo_form_version") as String
}

dependencies {
    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}")
    compileOnly("me.shedaniel.cloth:cloth-config-forge:${rootProject.property("cloth_config_version")}") {
        isTransitive = false
    }
}

tasks.jar { enabled = false }
