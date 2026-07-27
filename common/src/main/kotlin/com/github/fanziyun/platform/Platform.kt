package com.github.fanziyun.platform

import java.nio.file.Path
import java.util.ServiceLoader

interface Platform {
    val gameDir: Path

    companion object {
        val INSTANCE: Platform by lazy {
            ServiceLoader.load(Platform::class.java, Platform::class.java.classLoader).findFirst().orElseThrow {
                IllegalStateException("No Platform implementation found")
            }
        }
    }
}
