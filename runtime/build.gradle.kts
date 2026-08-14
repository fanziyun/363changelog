plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom")
}

base.archivesName.set("363changelog-runtime")

val common = project(":common")

sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
    kotlin.exclude("com/github/fanziyun/mixin/**")
    resources.srcDir(common.file("src/main/resources"))
}

loom {
    noIntermediateMappings()
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    compileOnly("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    compileOnly("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")
    compileOnly("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_config_version")}")
    compileOnly("com.google.code.gson:gson:2.13.2")
}

tasks.processResources {
    inputs.property("runtime_version", rootProject.property("runtime_version"))
    filesMatching("changelog-runtime.properties") {
        expand(mapOf("runtime_version" to rootProject.property("runtime_version")))
    }
}
