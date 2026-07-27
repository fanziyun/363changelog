import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("net.minecraftforge.gradle") version "6.0.36"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set("${property("archives_base_name")}-forge")
}

val javaVersion = (property("java_version") as String).toInt()
val modId = rootProject.property("mod_id") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
    maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
    maven("https://maven.minecraftforge.net") { name = "MinecraftForge" }
}

sourceSets.main {
    kotlin.srcDirs("common/src/main/kotlin", "forge/src/main/kotlin")
    resources.srcDirs("common/src/main/resources", "forge/src/main/resources")
}

minecraft {
    mappings("official", rootProject.property("minecraft_version") as String)

    runs {
        create("client") {
            workingDirectory(project.file("runs/client"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:${rootProject.property("forge_version")}")
    compileOnly("org.spongepowered:mixin:0.8.5")
    implementation(fg.deobf("thedarkcolour:kotlinforforge:${rootProject.property("kotlin_for_forge_version")}"))
    implementation(fg.deobf("me.shedaniel.cloth:cloth-config-forge:${rootProject.property("cloth_config_version")}"))
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to rootProject.property("mod_id"),
        "mod_name" to rootProject.property("mod_name"),
        "mod_author" to rootProject.property("mod_author"),
        "mod_license" to rootProject.property("mod_license"),
        "minecraft_version_range" to rootProject.property("minecraft_version_range"),
        "forge_loader_version_range" to rootProject.property("forge_loader_version_range"),
        "forge_version_range" to rootProject.property("forge_version_range"),
        "cloth_config_version" to rootProject.property("cloth_config_version"),
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE.txt")) {
        rename { "${it}_${base.archivesName.get()}" }
    }
    manifest {
        attributes(
            "Specification-Title" to modId,
            "Specification-Vendor" to rootProject.rootProject.property("mod_author"),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to rootProject.rootProject.property("mod_author"),
        )
    }
    finalizedBy("reobfJar")
}
