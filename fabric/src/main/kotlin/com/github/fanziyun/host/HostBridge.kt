package com.github.fanziyun.host

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.AutoConfigClient
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

/** Stable Fabric-owned boundary around the reloadable 363 runtime. */
object HostBridge {
    private const val RUNTIME_ENTRYPOINT = "com.github.fanziyun.runtime.RuntimeEntrypoint"
    private const val BUNDLED_RUNTIME = "/runtime/363changelog-runtime.jar"
    private const val RUNTIME_CONFIG_DIR = "config/changelog363"
    private val LOGGER = LoggerFactory.getLogger("changelog363-host")
    private val lock = Any()

    @Volatile
    private var initialized = false

    private var runtime: RuntimeHandle? = null

    fun initialize() {
        synchronized(lock) {
            ensureConfigRegistered()
            if (runtime != null) return
            runCatching { loadRuntimeLocked(null) }
                .onFailure { LOGGER.error("Unable to start the reloadable 363 runtime", it) }
        }
    }

    fun ensureLoaded() {
        synchronized(lock) {
            ensureConfigRegistered()
            if (runtime == null) loadRuntimeLocked(null)
        }
    }

    fun unloadRuntime() {
        synchronized(lock) {
            runtime?.close()
            runtime = null
        }
    }

    fun loadRuntime() {
        loadRuntime(null)
    }

    fun loadRuntime(replacementJar: String?) {
        synchronized(lock) {
            ensureConfigRegistered()
            if (runtime != null) error("363 runtime is already loaded")
            loadRuntimeLocked(replacementJar)
        }
    }

    fun reloadRuntime(): Boolean {
        return reloadRuntime(null)
    }

    fun reloadRuntime(replacementJar: String?): Boolean {
        synchronized(lock) {
            ensureConfigRegistered()
            runtime?.close()
            runtime = null
            loadRuntimeLocked(replacementJar)
            return true
        }
    }

    fun configScreen(parent: Screen?): Screen = synchronized(lock) {
        ensureConfigRegistered()
        AutoConfigClient.getConfigScreen(HostConfig::class.java, parent).get()
    }

    fun openOverview(parent: Screen?): Screen? {
        return synchronized(lock) {
            ensureConfigRegistered()
            if (runtime == null) {
                runCatching { loadRuntimeLocked(null) }
                    .onFailure { LOGGER.error("Unable to start the 363 runtime for the overview screen", it) }
            }
            runCatching { runtime?.createOverviewScreen(parent) }
                .onFailure { LOGGER.error("Unable to create the 363 overview screen", it) }
                .getOrNull()
        }
    }

    fun showOnTitle(): Boolean = config().showOnTitle

    fun versionYOffset(): Int = config().versionYOffset

    fun versionLine(): String {
        synchronized(lock) {
            return runtime?.versionLine().orEmpty().ifBlank {
                config().displayLabel()
            }
        }
    }

    fun runtimeMarker(): String = synchronized(lock) {
        runtime?.runtimeMarker().orEmpty().ifBlank { "unloaded" }
    }

    fun runtimeJarPath(): Path {
        val configured = System.getProperty("changelog363.runtimeJar")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (configured != null) return Path.of(configured).toAbsolutePath().normalize()

        val target = FabricLoader.getInstance().gameDir.resolve(RUNTIME_CONFIG_DIR).resolve("runtime.jar")
        if (Files.isRegularFile(target)) return target

        Files.createDirectories(target.parent)
        val resource = HostBridge::class.java.getResourceAsStream(BUNDLED_RUNTIME)
            ?: error("Bundled runtime JAR is missing: $BUNDLED_RUNTIME")
        resource.use { Files.copy(it, target) }
        return target
    }

    private fun ensureConfigRegistered() {
        if (initialized) return
        AutoConfig.register(HostConfig::class.java) { definition, clazz ->
            GsonConfigSerializer(definition, clazz)
        }
        initialized = true
    }

    private fun config(): HostConfig = synchronized(lock) {
        ensureConfigRegistered()
        AutoConfig.getConfigHolder(HostConfig::class.java).config
    }

    private fun loadRuntimeLocked(replacementJar: String?) {
        val requestedJar = replacementJar
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: runtimeJarPath()
        require(Files.isRegularFile(requestedJar)) { "Runtime JAR does not exist: $requestedJar" }
        val jar = extractRuntimeIfNeeded(requestedJar)

        val loader = RuntimeClassLoader(
            arrayOf(jar.toUri().toURL()),
            HostBridge::class.java.classLoader,
        )
        try {
            val entrypointType = Class.forName(RUNTIME_ENTRYPOINT, true, loader)
            val entrypoint = entrypointType.getConstructor().newInstance()
            val start = entrypointType.getMethod(
                "start",
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
            )
            val cfg = config()
            start.invoke(
                entrypoint,
                FabricLoader.getInstance().gameDir.toString(),
                cfg.changelogUrl,
                cfg.packName,
                cfg.modpackVersion,
                cfg.showOnTitle,
                cfg.enableVersionCheck,
                cfg.loadTimeoutSeconds,
                cfg.versionYOffset,
                cfg.externalLinkName,
                cfg.externalLinkUrl,
            )
            runtime = RuntimeHandle(loader, entrypoint, entrypointType)
            LOGGER.info("Loaded 363 runtime {} from {}", runtime?.runtimeMarker(), jar)
        } catch (exception: Throwable) {
            runCatching { loader.close() }
            throw unwrap(exception)
        }
    }

    /** Accept both a standalone runtime JAR and a complete 363 mod JAR. */
    private fun extractRuntimeIfNeeded(requestedJar: Path): Path {
        JarFile(requestedJar.toFile()).use { archive ->
            if (archive.getEntry("com/github/fanziyun/runtime/RuntimeEntrypoint.class") != null) {
                return requestedJar
            }

            val nested = archive.getJarEntry("runtime/363changelog-runtime.jar")
                ?: error("JAR does not contain a 363 runtime: $requestedJar")
            val target = FabricLoader.getInstance().gameDir
                .resolve(RUNTIME_CONFIG_DIR)
                .resolve("replacement-runtime.jar")
            Files.createDirectories(target.parent)
            archive.getInputStream(nested).use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        }
    }

    private fun unwrap(exception: Throwable): Throwable = when (exception) {
        is InvocationTargetException -> exception.targetException ?: exception
        else -> exception
    }

    private class RuntimeHandle(
        private val loader: RuntimeClassLoader,
        private val entrypoint: Any,
        entrypointType: Class<*>,
    ) : AutoCloseable {
        private val stop: Method = entrypointType.getMethod("stop")
        private val overview: Method = entrypointType.getMethod("createOverviewScreen", Screen::class.java)
        private val version: Method = entrypointType.getMethod("versionLine")
        private val marker: Method = entrypointType.getMethod("runtimeMarker")

        fun createOverviewScreen(parent: Screen?): Screen =
            overview.invoke(entrypoint, parent) as Screen

        fun versionLine(): String = version.invoke(entrypoint) as String

        fun runtimeMarker(): String = marker.invoke(entrypoint) as String

        override fun close() {
            val current = Minecraft.getInstance().screen
            if (current != null && current.javaClass.classLoader === loader) {
                Minecraft.getInstance().setScreen(null)
            }
            runCatching { stop.invoke(entrypoint) }
                .onFailure { LOGGER.warn("363 runtime stop reported an error", it) }
            runCatching { loader.close() }
                .onFailure { LOGGER.warn("Unable to close 363 runtime classloader", it) }
        }
    }
}

private fun HostConfig.displayLabel(): String = listOfNotNull(
    packName.trim().takeIf(String::isNotEmpty),
    modpackVersion.trim().takeIf(String::isNotEmpty)?.let { "v$it" },
).joinToString(" ")
