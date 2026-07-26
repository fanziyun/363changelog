package com.github.fanziyun.platform

import com.github.fanziyun.Changelog
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * common 需要、但只有加载器才能提供的那一点点能力。
 *
 * 实现类由各子项目通过 `META-INF/services/com.github.fanziyun.platform.Platform` 注册。
 * 目前只有一个方法——刻意保持这么小：其余 1000 多行代码只依赖原版 API。
 */
interface Platform {

    /** 游戏根目录，更新日志的磁盘缓存放在它下面的 `.cache/` 里 */
    val gameDir: Path

    /**
     * 加载器在标题界面左下角画了几行版本信息。
     *
     * 原版只画一行"Minecraft <版本>"；NeoForge 用自己的 branding 覆盖掉那行，
     * 通常是两行（Minecraft 一行、NeoForge + 模组数一行），而且是自下而上堆的。
     * 我们的整合包版本行要排在这些行之上，否则会和它们叠在一起。
     */
    val titleScreenBrandingLines: Int

    companion object {
        val INSTANCE: Platform by lazy {
            // 必须显式传 classloader。ServiceLoader.load(Class) 用的是线程上下文类加载器，
            // 而这个 lazy 第一次被解析发生在 ChangelogLoader 的 CompletableFuture.supplyAsync 内，
            // 也就是 ForkJoinPool.commonPool 的工作线程上 —— 那些线程的上下文类加载器是系统
            // AppClassLoader，看不到模组 jar，会一个实现都找不到，
            // 于是整条加载链静默退化成"只读内置 changelog.json"，远程与缓存全部失效。
            val loaded = ServiceLoader.load(Platform::class.java, Platform::class.java.classLoader)
                .findFirst()
                .orElseThrow {
                    IllegalStateException(
                        "No Platform implementation found — is META-INF/services missing from the jar?"
                    )
                }
            Changelog.LOGGER.debug("Platform helper: {}", loaded.javaClass.name)
            loaded
        }
    }
}
