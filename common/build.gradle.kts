plugins {
    kotlin("jvm")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-common")

dependencies {
    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}")
    compileOnly("me.shedaniel.cloth:cloth-config-forge:${rootProject.property("cloth_config_version")}") {
        isTransitive = false
    }
}

tasks.jar { enabled = false }
