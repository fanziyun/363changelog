import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("dev.kikugie.loom-back-compat")
}

val mcVersion = stonecutter.current.version
val outputJavaVersion = if (stonecutter.current.parsed >= "26") 25 else 21
val minecraftVersionRange = "[$mcVersion]"
val generatedStonecutter = layout.buildDirectory.dir("generated/stonecutter/main")

sourceSets.main {
    kotlin.setSrcDirs(listOf(generatedStonecutter.get().dir("kotlin")))
    resources.setSrcDirs(listOf(generatedStonecutter.get().dir("resources")))
}

group = rootProject.property("maven_group") as String
version = "${rootProject.property("mod_version")}+mc$mcVersion"
base.archivesName.set(rootProject.property("archives_base_name") as String)

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
    maven("https://maven.terraformersmc.com/releases/") { name = "Mod Menu" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    if (stonecutter.current.parsed < "26") {
        mappings(loom.officialMojangMappings())
    }

    compileOnly("net.fabricmc:sponge-mixin:${rootProject.property("mixin_version")}")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_language_kotlin")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${property("deps.cloth_config")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    modImplementation("com.terraformersmc:modmenu:${property("deps.modmenu")}")
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

loom {
    mixin {
        useLegacyMixinAp.set(false)
    }

    runs {
        named("client") {
            client()
            ideConfigGenerated(true)
            runDir("runs/client")
        }

        register("smoke") {
            client()
            ideConfigGenerated(true)
            runDir("runs/smoke")
            property("changelog363.smokeTest", "true")
            vmArg("-Dchangelog363.smokeTest=true")
        }
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    exclude("META-INF/neoforge.mods.toml")

    val props = mapOf(
        "version" to project.version,
        "mod_id" to rootProject.property("mod_id"),
        "mod_name" to rootProject.property("mod_name"),
        "mod_license" to rootProject.property("mod_license"),
        "fabric_loader_version" to project.property("deps.fabric_loader"),
        "kotlin_loader_version" to project.property("deps.fabric_language_kotlin"),
        "minecraft_version" to mcVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "java_compat" to "JAVA_$outputJavaVersion",
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "${props["mod_id"]}.mixins.json")) {
        expand(props)
    }
}

tasks.matching { it.name in setOf("compileKotlin", "processResources", "sourcesJar") }.configureEach {
    dependsOn("stonecutterGenerate")
}

val primaryJarTask = if (stonecutter.current.parsed < "26") "remapJar" else "jar"
val primarySourcesTask = if (stonecutter.current.parsed < "26") "remapSourcesJar" else "sourcesJar"

val buildAndCollect = tasks.register<Copy>("buildAndCollect") {
    from(tasks.named(primaryJarTask))
    from(tasks.named(primarySourcesTask))
    into(rootProject.layout.buildDirectory.dir("dist/$mcVersion/fabric"))
    dependsOn(tasks.build)
}

tasks.register("smokeClient") {
    group = "verification"
    description = "Runs the automated client smoke test for $mcVersion Fabric"
    dependsOn("runSmoke")
}
