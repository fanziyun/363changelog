import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.10" apply false
    // see https://fabricmc.net/develop/ for new versions
    id("net.fabricmc.fabric-loom") version "1.17.17" apply false
    // see https://projects.neoforged.net/neoforged/moddevgradle for new versions
    id("net.neoforged.moddev") version "2.0.142" apply false
}

val javaVersion = (property("java_version") as String).toInt()

subprojects {
    group = rootProject.property("maven_group") as String
    version = rootProject.property("mod_version") as String

    repositories {
        mavenCentral()
        maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
        maven("https://maven.terraformersmc.com/releases/") { name = "Mod Menu" }
        maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
    }

    // 用 withId 守卫：各子项目自己在 plugins 块里应用 kotlin/loom/moddev，
    // 这里不能假设应用顺序
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }

    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_${rootProject.property("archives_base_name")}" }
        }
    }
}
