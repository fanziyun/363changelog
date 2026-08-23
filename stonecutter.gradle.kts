plugins {
    id("dev.kikugie.stonecutter")
    kotlin("jvm") version "2.3.21" apply false
    id("net.fabricmc.fabric-loom") version "1.15.5" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.15.5" apply false
    id("dev.kikugie.loom-back-compat") version "0.4.1" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

stonecutter active "26.1.2-fabric"

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")
}
