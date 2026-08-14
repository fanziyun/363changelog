package com.github.fanziyun.host

import java.net.URL
import java.net.URLClassLoader

/**
 * Loads only 363's own packages from the replacement JAR. Minecraft, Fabric,
 * Kotlin, Gson, Cloth Config and the JVM remain parent-owned singletons.
 */
internal class RuntimeClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("com.github.fanziyun.")) return super.loadClass(name, resolve)

        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { loaded ->
                if (resolve) resolveClass(loaded)
                return loaded
            }

            val loaded = runCatching { findClass(name) }.getOrElse { super.loadClass(name, resolve) }
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }
}
