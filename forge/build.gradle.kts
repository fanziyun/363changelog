plugins {
    kotlin("jvm")
    id("net.minecraftforge.gradle")
}

base.archivesName.set("${rootProject.property("archives_base_name")}-forge")

val common = project(":common")
val modId = rootProject.property("mod_id") as String

sourceSets.main {
    kotlin.srcDir(common.file("src/main/kotlin"))
    resources.srcDir(common.file("src/main/resources"))
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
    implementation("thedarkcolour:kotlinforforge:${rootProject.property("kotlin_for_forge_version")}")
    implementation("me.shedaniel.cloth:cloth-config-forge:${rootProject.property("cloth_config_version")}")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to modId,
        "mod_name" to rootProject.property("mod_name"),
        "mod_author" to rootProject.property("mod_author"),
        "mod_license" to rootProject.property("mod_license"),
        "minecraft_version_range" to rootProject.property("minecraft_version_range"),
        "forge_loader_version_range" to rootProject.property("forge_loader_version_range"),
        "forge_version_range" to rootProject.property("forge_version_range"),
        "cloth_config_version" to rootProject.property("cloth_config_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Specification-Title" to modId,
            "Specification-Vendor" to rootProject.property("mod_author"),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to rootProject.property("mod_author"),
        )
    }
    finalizedBy("reobfJar")
}
