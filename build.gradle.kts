import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("fabric-loom") version "1.9.1"
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

val mainSourceSet = sourceSets.main.get()
val clientSourceSet = sourceSets.create("client") {
    compileClasspath += mainSourceSet.output + mainSourceSet.compileClasspath
    runtimeClasspath += mainSourceSet.output + mainSourceSet.runtimeClasspath
}

loom {
    mods {
        register("changelog363") {
            sourceSet(mainSourceSet)
            sourceSet(clientSourceSet)
        }
    }
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
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    val clothConfig = "me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}"
    modImplementation(clothConfig) {
        exclude(group = "net.fabricmc.fabric-api")
    }

    val modMenu = "maven.modrinth:modmenu:${project.property("modmenu_version")}"
    modImplementation(modMenu)
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("loader_version"),
        "kotlin_loader_version" to project.property("kotlin_loader_version"),
    )
    inputs.properties(properties)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(properties)
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
    from("LICENSE.txt") {
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
