import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21" apply false
    id("net.minecraftforge.gradle") version "6.0.36" apply false
}

val javaVersion = (property("java_version") as String).toInt()

subprojects {
    group = rootProject.property("maven_group") as String
    version = rootProject.property("mod_version") as String

    repositories {
        mavenCentral()
        maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
        maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
        maven("https://maven.minecraftforge.net") { name = "MinecraftForge" }
    }

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
