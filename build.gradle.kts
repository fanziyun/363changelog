import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("fabric-loom") version "1.5.8"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

// Define client source set manually (Loom 1.5.x compat without splitEnvironmentSourceSets)
val client by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

loom {
    mods {
        register("changelog363") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
}

repositories {
    maven("https://maven.shedaniel.me/") {
        name = "Cloth Config"
    }
    maven("https://maven.terraformersmc.com/releases/") {
        name = "Mod Menu"
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:1.20.1+build.9")

    val sharedDeps = listOf(
        "net.fabricmc:fabric-loader:${project.property("loader_version")}",
        "net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}",
        "net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}",
        "com.google.code.gson:gson:2.14.0"
    )

    sharedDeps.forEach { dep ->
        implementation(dep)
        add("clientImplementation", dep)
    }

    val clothConfig = "me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}"
    implementation(clothConfig) { exclude(group = "net.fabricmc.fabric-api") }
    add("clientImplementation", clothConfig) { exclude(group = "net.fabricmc.fabric-api") }

    val modMenu = "maven.modrinth:modmenu:${project.property("modmenu_version")}"
    implementation(modMenu)
    add("clientImplementation", modMenu)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version")!!,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}
