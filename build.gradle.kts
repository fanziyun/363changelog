import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

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
    mavenCentral()
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")

    // 共享依赖 (main + client 都能使用)
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

    // Cloth Config (配置界面)
    val clothConfig = "me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}"
    implementation(clothConfig) { exclude(group = "net.fabricmc.fabric-api") }
    add("clientImplementation", clothConfig) { exclude(group = "net.fabricmc.fabric-api") }

    // Mod Menu
    val modMenu = "com.terraformersmc:modmenu:${project.property("modmenu_version")}"
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
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    // 仓库里的文件名是 LICENSE.txt，写成 "LICENSE" 会静默匹配不到任何文件
    from("LICENSE.txt") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
    }
}
