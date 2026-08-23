import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

val mcVersion = stonecutter.current.version
val outputJavaVersion = if (stonecutter.current.parsed >= "26") 25 else 21
val modId = rootProject.property("mod_id") as String
val generatedStonecutter = layout.buildDirectory.dir("generated/stonecutter/main")

sourceSets.main {
    kotlin.setSrcDirs(listOf(generatedStonecutter.get().dir("kotlin")))
    resources.setSrcDirs(listOf(generatedStonecutter.get().dir("resources")))
}

group = rootProject.property("maven_group") as String
version = "${rootProject.property("mod_version")}+mc$mcVersion"
base.archivesName.set("${rootProject.property("archives_base_name")}-neoforge")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.toVersion(outputJavaVersion)
    targetCompatibility = JavaVersion.toVersion(outputJavaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(outputJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(outputJavaVersion.toString()))
}

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/") { name = "Cloth Config" }
    maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "Kotlin for Forge" }
}

neoForge {
    version = project.property("deps.neoforge") as String

    mods.register(modId) {
        sourceSet(sourceSets.main.get())
    }

    runs {
        register("client") {
            client()
            gameDirectory.set(file("runs/client"))
        }

        register("smoke") {
            client()
            gameDirectory.set(file("runs/smoke"))
            jvmArguments.addAll("-Dchangelog363.smokeTest=true")
        }
    }
}

dependencies {
    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}") {
        exclude(group = "org.ow2.asm")
    }
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("deps.kotlin_for_forge")}")
    implementation("me.shedaniel.cloth:cloth-config-neoforge:${property("deps.cloth_config")}")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
    if (name == "runSmoke") {
        environment("LIBGL_ALWAYS_SOFTWARE", "true")
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    exclude("fabric.mod.json")

    val kffVersion = project.property("deps.kotlin_for_forge") as String
    val kffRange = if (stonecutter.current.parsed >= "1.21.9") "[6,)" else "[5,)"
    val props = mapOf(
        "version" to project.version,
        "mod_id" to modId,
        "mod_name" to rootProject.property("mod_name"),
        "mod_author" to rootProject.property("mod_author"),
        "mod_license" to rootProject.property("mod_license"),
        "minecraft_version_range" to "[$mcVersion]",
        "neoforge_loader_version_range" to "[4,)",
        "neoforge_version" to project.property("deps.neoforge"),
        "cloth_config_version" to project.property("deps.cloth_config"),
        "kff_version_range" to kffRange,
        "java_compat" to "JAVA_$outputJavaVersion",
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "$modId.mixins.json")) {
        expand(props)
    }
}

tasks.matching { it.name in setOf("compileKotlin", "processResources", "sourcesJar") }.configureEach {
    dependsOn("stonecutterGenerate")
}

val buildAndCollect = tasks.register<Copy>("buildAndCollect") {
    from(tasks.jar)
    from(tasks.named("sourcesJar"))
    into(rootProject.layout.buildDirectory.dir("dist/$mcVersion/neoforge"))
    dependsOn(tasks.build)
}

tasks.register("smokeClient") {
    group = "verification"
    description = "Runs the automated client smoke test for $mcVersion NeoForge"
    dependsOn("runSmoke")
}
