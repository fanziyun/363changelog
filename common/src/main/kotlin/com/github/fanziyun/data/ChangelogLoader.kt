package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer
import com.github.fanziyun.platform.Platform
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * 更新日志加载器。
 *
 * 数据来源按优先级回退：远程 URL → 磁盘缓存 → JAR 内置资源，任一环节成功即停止。
 * 远程请求带 `If-None-Match`，命中 304 时直接复用磁盘缓存以省流量。
 */
object ChangelogLoader {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"
    private const val BUNDLED_RESOURCE = "/changelog.json"

    private val gson: Gson = Gson()

    // 延迟解析：目录来自加载器提供的 Platform，等真正要读写缓存时再去查，
    // 这样单纯引用 ChangelogLoader 不会强制触发 ServiceLoader
    private val cacheDir: Path by lazy { Platform.INSTANCE.gameDir.resolve(".cache") }
    private val cacheFile: Path by lazy { cacheDir.resolve("changelog_cache.json") }
    private val etagFile: Path by lazy { cacheDir.resolve("changelog_cache.etag") }

    /** 至少完成过一次加载（无论成功与否） */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /** 所有来源都失败，当前展示的是空数据 */
    @Volatile
    var isError: Boolean = false
        private set

    @Volatile
    var errorMessage: String = ""
        private set

    private val _data = AtomicReference(ChangelogData.EMPTY)
    val data: ChangelogData get() = _data.get()

    /** 数据中最高的版本号，按语义化版本比较，不依赖 JSON 中的书写顺序。 */
    val latestVersion: String
        get() = data.entries
            .map { it.version }
            .filter { it.isNotBlank() }
            .maxWithOrNull(SemVer.COMPARATOR)
            .orEmpty()

    private val lock = Any()
    private var inFlight: CompletableFuture<Boolean>? = null
    private var inFlightIsForced: Boolean = false

    @Volatile
    private var cachedEtag: String? = null

    /**
     * 加载更新日志。同一时刻只会有一次加载在进行。
     *
     * 复用正在进行的加载有个前提：它得满足调用方的意图。普通加载可以搭已有的车，
     * 但强制刷新不能——否则客户端初始化时发起的那次普通加载还没结束时点"刷新"，
     * 就会拿到一个仍然带 If-None-Match 的请求，服务端回 304，用户看到的还是旧数据。
     *
     * @param forceRefresh 忽略 ETag 与磁盘缓存，强制重新拉取远程数据
     */
    fun load(remoteUrl: String, forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        synchronized(lock) {
            val running = inFlight?.takeIf { !it.isDone }
            if (running != null) {
                if (!forceRefresh || inFlightIsForced) return running
                // 想强制刷新但在跑的是普通加载：等它结束后再真正发一次强制请求，
                // 而不是并发两个请求去抢着写缓存
                return running.handle { _, _ -> null }.thenCompose { load(remoteUrl, true) }
            }
            inFlightIsForced = forceRefresh
            return CompletableFuture.supplyAsync { doLoad(remoteUrl, forceRefresh) }.also { inFlight = it }
        }
    }

    /**
     * 若还没加载过就触发一次加载，否则复用上一次的结果。
     * 用于每次进入标题界面时避免重复发起网络请求。
     */
    fun ensureLoaded(remoteUrl: String): CompletableFuture<Boolean> {
        synchronized(lock) {
            val previous = inFlight
            // 正在加载或已经成功过 → 直接复用；上次所有来源都失败才重试
            if (previous != null && (!previous.isDone || !isError)) return previous
        }
        return load(remoteUrl)
    }

    private fun doLoad(remoteUrl: String, forceRefresh: Boolean): Boolean {
        errorMessage = ""
        // 远程失败不是致命错误：只要能回退到缓存或内置资源，界面依然可用
        val loaded = loadFromRemote(remoteUrl, forceRefresh) || tryLoadCache() || loadFromResources()
        if (!loaded) {
            _data.set(ChangelogData.EMPTY)
            if (errorMessage.isBlank()) errorMessage = "No changelog source available"
            Changelog.LOGGER.error("Failed to load changelog from any source: {}", errorMessage)
        }
        isError = !loaded
        isLoaded = true
        return loaded
    }

    private fun loadFromRemote(urlStr: String, forceRefresh: Boolean): Boolean {
        if (urlStr.isBlank()) return false
        var conn: HttpURLConnection? = null
        return try {
            conn = (URI.create(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                // 缓存文件不存在时带 ETag 没有意义：304 之后无处可读
                if (!forceRefresh && Files.exists(cacheFile)) {
                    readEtag()?.let { setRequestProperty("If-None-Match", it) }
                }
            }

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    Changelog.LOGGER.info("Changelog unchanged (304), reusing disk cache")
                    return tryLoadCache()
                }

                HttpURLConnection.HTTP_OK -> Unit

                else -> {
                    errorMessage = "HTTP $code"
                    Changelog.LOGGER.warn("Changelog request failed with HTTP {}", code)
                    return false
                }
            }

            val parsed = conn.inputStream.use { parseJson(it.readAllBytes()) } ?: return false
            _data.set(parsed)
            saveCache(parsed, conn.getHeaderField("ETag"))
            Changelog.LOGGER.info("Changelog loaded from remote, {} entries", parsed.entries.size)
            true
        } catch (e: Exception) {
            errorMessage = e.message ?: e.javaClass.simpleName
            Changelog.LOGGER.warn("Failed to fetch remote changelog, falling back to cache", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun tryLoadCache(): Boolean = try {
        if (Files.exists(cacheFile)) {
            parseJson(Files.readAllBytes(cacheFile))?.let {
                _data.set(it)
                Changelog.LOGGER.info("Changelog loaded from disk cache, {} entries", it.entries.size)
                true
            } ?: false
        } else {
            false
        }
    } catch (e: Exception) {
        Changelog.LOGGER.warn("Failed to read changelog cache", e)
        false
    }

    /** JAR 内置的兜底数据 */
    private fun loadFromResources(): Boolean = try {
        ChangelogLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.use { stream ->
            parseJson(stream.readAllBytes())?.let {
                _data.set(it)
                Changelog.LOGGER.info("Changelog loaded from bundled resources, {} entries", it.entries.size)
                true
            }
        } ?: false
    } catch (e: Exception) {
        Changelog.LOGGER.error("Failed to load bundled changelog", e)
        false
    }

    private fun readEtag(): String? {
        cachedEtag?.let { return it.ifBlank { null } }
        val fromDisk = try {
            if (Files.exists(etagFile)) Files.readString(etagFile, StandardCharsets.UTF_8).trim() else ""
        } catch (_: Exception) {
            ""
        }
        cachedEtag = fromDisk
        return fromDisk.ifBlank { null }
    }

    private fun saveCache(data: ChangelogData, etagHeader: String?) {
        try {
            Files.createDirectories(cacheDir)
            Files.writeString(cacheFile, gson.toJson(data), StandardCharsets.UTF_8)

            // 原样保存服务端返回的 ETag（含引号 / W/ 前缀），下次直接回发
            val etag = etagHeader?.trim().orEmpty()
            cachedEtag = etag
            if (etag.isNotBlank()) Files.writeString(etagFile, etag, StandardCharsets.UTF_8)
            else Files.deleteIfExists(etagFile)
        } catch (e: Exception) {
            Changelog.LOGGER.warn("Failed to write changelog cache", e)
        }
    }

    private fun parseJson(bytes: ByteArray): ChangelogData? = try {
        gson.fromJson(String(bytes, StandardCharsets.UTF_8), ChangelogData::class.java)
    } catch (e: JsonParseException) {
        // URL 指向 HTML 页面（例如 GitHub blob 链接）时会走到这里
        errorMessage = "Invalid changelog JSON"
        Changelog.LOGGER.error("Failed to parse changelog JSON", e)
        null
    }
}
