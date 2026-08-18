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
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 更新日志加载器。
 *
 * 数据来源按优先级回退：远程 URL → 磁盘缓存 → JAR 内置资源，任一环节成功即停止。
 * 远程请求带 `If-None-Match`，命中 304 时直接复用磁盘缓存以省流量。
 *
 * 整个加载受一个端到端超时约束，见 [load] 的 `timeoutMs`。
 */
object ChangelogLoader {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"
    private const val BUNDLED_RESOURCE = "/changelog.json"
    // 端到端超时。connectTimeout 只管 TCP 连接、管不到 DNS；readTimeout 是每次读而非整体，
    // 所以网络挂死时只有这个全局 deadline 才能保证加载一定在期限内结束、UI 不会永远停在"加载中"。
    // 这是 load()/ensureLoaded() 公共 API 的兜底默认；用户可配置的值来自 ModConfig.loadTimeoutSeconds。
    private const val DEFAULT_LOAD_TIMEOUT_MS = 30_000L

    private val gson: Gson = Gson()

    private data class LoadRequest(
        val remoteUrl: String,
        val forceRefresh: Boolean,
        val timeoutMs: Long,
        val deadlineNanos: Long,
        /** 在 load() 加锁瞬间捕获：该 URL 之前是否已有可展示的数据，失败/超时时据此保留上次好数据 */
        val keepExistingData: Boolean,
    )

    // 延迟解析：目录来自加载器提供的 Platform，等真正要读写缓存时再去查，
    // 这样单纯引用 ChangelogLoader 不会强制触发 ServiceLoader
    private val cacheDir: Path by lazy { Platform.INSTANCE.gameDir.resolve(".cache") }
    private val cacheFile: Path by lazy { cacheDir.resolve("changelog_cache.json") }
    private val etagFile: Path by lazy { cacheDir.resolve("changelog_cache.etag") }

    /**
     * 一次加载的终态快照。
     *
     * 三个标志必须原子地一起发布：超时兜底跑在 JDK Delayer 线程上，而卡住的加载线程随后
     * 可能仍会写入自己的结果，分开的 @Volatile 字段会被撕裂成"isError=true 但消息是成功态"
     * 这种自相矛盾的组合。
     */
    private data class State(
        val isLoaded: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String = "",
    )

    private val stateRef = AtomicReference(State())

    /** 至少完成过一次加载（无论成功与否） */
    val isLoaded: Boolean get() = stateRef.get().isLoaded

    /** 所有来源都失败，当前展示的是空数据 */
    val isError: Boolean get() = stateRef.get().isError

    val errorMessage: String get() = stateRef.get().errorMessage

    /**
     * 一次加载过程中各来源记下的失败原因。
     *
     * 每次 doLoad 新建一个，作为参数往下传，而不是放在对象字段上——两次加载可能重叠
     * （超时兜底已经把 future 完成、调用方随即发起下一次，而上一次的线程还卡在网络里），
     * 共享字段会让上一次的失败原因串到下一次的终态里去。
     */
    private class SourceErrors {
        var last: String = ""
    }

    private val _data = AtomicReference(ChangelogData.EMPTY)
    val data: ChangelogData get() = _data.get()

    // 每次发布终态（成功/失败/超时）递增；界面据此在仍打开时重建，避免迟到结果被错过
    private val generation = AtomicLong()
    val dataVersion: Long get() = generation.get()

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
    /** 上一次完成加载的 URL，用于判断已保留的数据是否属于当前请求 */
    private var lastCompletedUrl: String? = null

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
     * @param timeoutMs 端到端预算：无论网络如何挂死，返回的 future 必定在此期限内完成
     */
    fun load(
        remoteUrl: String,
        forceRefresh: Boolean = false,
        timeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS,
    ): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(lock) {
            val running = inFlight?.takeIf { !it.isDone }
            if (running != null) {
                if (!forceRefresh || inFlightIsForced) return running
                // 想强制刷新但在跑的是普通加载：等它结束后再真正发一次强制请求，
                // 而不是并发两个请求去抢着写缓存
                return running.handle { _, _ -> null }
                    .thenCompose { load(normalizedUrl, true, timeoutMs) }
            }

            // 在 doLoad 重置状态之前、加锁瞬间捕获"该 URL 是否仍有可展示的数据"，
            // 供超时发布时决定保留上次好数据；也避免在 Delayer 线程上无锁读 lastCompletedUrl。
            // 判断依据是"该 URL 是否仍有已保留的数据"，而不是瞬时错误标志——一次保留数据的超时
            // 会把 state 标成 error 但保留 _data，随后的第二次超时也必须仍能识别这份数据。
            val lastState = stateRef.get()
            val keepExistingData =
                lastCompletedUrl == normalizedUrl &&
                    ((lastState.isLoaded && !lastState.isError) || _data.get() != ChangelogData.EMPTY)
            val request = LoadRequest(normalizedUrl, forceRefresh, timeoutMs, deadlineNanos, keepExistingData)

            inFlightIsForced = forceRefresh
            // orTimeout 是安全网：即使 doLoad 因为 DNS 卡死/慢速滴流而无限期不返回，
            // 返回给调用方的 future 也保证在 timeoutMs 内完成，UI 因此总能离开"加载中"。
            val bounded = CompletableFuture.supplyAsync { doLoad(request) }
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally { exception ->
                    when (unwrap(exception)) {
                        is TimeoutException -> {
                            // doLoad 其实已成功（终态已发布成功）时，别把成功报成超时
                            val current = stateRef.get()
                            if (current.isLoaded && !current.isError) {
                                true
                            } else {
                                publishTimeoutState(request)
                                false
                            }
                        }
                        // doLoad 抛异常（如 Platform 解析失败）不能留 state=loading：发布终态错误
                        else -> {
                            publishErrorState(exception)
                            false
                        }
                    }
                }
            inFlight = bounded
            return bounded
        }
    }

    /**
     * 若还没加载过就触发一次加载，否则复用上一次的结果。
     * 用于每次进入标题界面时避免重复发起网络请求。
     */
    fun ensureLoaded(remoteUrl: String, timeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS): CompletableFuture<Boolean> {
        synchronized(lock) {
            val previous = inFlight
            // 正在加载 → 搭车；已经成功且真的有数据 → 复用。
            // 失败过、或状态说成功但手上是空数据（超时兜底与迟到成功交错的残留），都要重试，
            // 否则界面会一直空着，只能靠用户手动点刷新。
            val healthy = !isError && _data.get() != ChangelogData.EMPTY
            if (previous != null && (!previous.isDone || healthy)) return previous
        }
        return load(remoteUrl, timeoutMs = timeoutMs)
    }

    private fun doLoad(request: LoadRequest): Boolean {
        val errors = SourceErrors()
        // 端到端超时：deadline 一到就跳过远端，直接走 cache -> bundled 兜底，绝不无限期等网络。
        // 每个阶段本身仍受 connect/read 超时限制，这里的检查只是把"已超时"提前暴露。
        val remote = if (deadlineExpired(request)) {
            errors.last = timeoutMessage(request)
            null
        } else {
            loadFromRemote(request.remoteUrl, request.forceRefresh, errors)
        }

        // 远程失败不是致命错误：只要能回退到缓存或内置资源，界面依然可用
        val loaded = remote ?: tryLoadCache(errors) ?: loadFromResources(errors)
        return if (loaded != null) {
            completeSuccess(request, loaded)
        } else {
            completeFailure(request, errors)
        }
    }

    /**
     * 数据与状态必须由同一个线程紧挨着发布。
     *
     * 各来源只把解析结果返回上来、不自己写 [_data]，就是为了这里能一次性发布：否则超时兜底
     * 可能挤在"来源已写好数据"与"终态已发布"之间，把这次成功的数据清成空，界面就永远停在
     * 空列表 + 成功态这种谁都修不了的组合上。
     */
    private fun completeSuccess(request: LoadRequest, loadedData: ChangelogData): Boolean {
        _data.set(loadedData)
        publish(State(isLoaded = true))
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        return true
    }

    private fun completeFailure(request: LoadRequest, errors: SourceErrors): Boolean {
        val message = errors.last.ifBlank { "No changelog source available" }
        // 全部来源失败时保留同 URL 上次成功的数据，避免网络抖动把可用列表清空
        if (!request.keepExistingData) _data.set(ChangelogData.EMPTY)
        publish(State(isLoaded = true, isError = true, errorMessage = message))
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        Changelog.LOGGER.error("Failed to load changelog from any source: {}", message)
        return false
    }

    /** 发布终态并递增生成号，让仍打开的界面下一帧收敛到新数据 */
    private fun publish(state: State) {
        stateRef.set(state)
        generation.incrementAndGet()
    }

    private fun deadlineExpired(request: LoadRequest): Boolean =
        System.nanoTime() - request.deadlineNanos >= 0

    private fun timeoutMessage(request: LoadRequest): String =
        "Changelog load timed out after ${request.timeoutMs / 1000}s"

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is CompletionException) throwable.cause ?: throwable else throwable

    /**
     * orTimeout 触发的兜底：在 JDK Delayer daemon 线程上发布一个终态，让 UI 离开"加载中"。
     *
     * 只写 state，不动 [_data]：卡住的 loader 线程此时可能正好走到 [completeSuccess]，
     * 在这里清空数据就会把那次成功的结果抹掉，而本线程无从恢复。数据的清空交给同样跑在
     * loader 线程上的 [completeFailure]，它与各来源的写入天然有序。
     */
    private fun publishTimeoutState(request: LoadRequest) {
        // 若 doLoad 其实已经成功（终态已写成成功），说明 Delayer 在 future 完成 CAS 上
        // 赢了正常完成——别再用超时错误覆盖这次真实成功。
        val current = stateRef.get()
        if (current.isLoaded && !current.isError) return

        publish(State(isLoaded = true, isError = true, errorMessage = timeoutMessage(request)))
        Changelog.LOGGER.warn(
            "Changelog load timed out after {}s; {}",
            request.timeoutMs / 1000,
            if (request.keepExistingData) "keeping last-good data" else "no usable data yet",
        )
    }

    /**
     * doLoad 抛出的非超时异常落到这里（exceptionally 线程上执行，无 IO）：发布终态错误。
     * 否则 state 会停在 loading、界面永远转圈，违背"加载一定在期限内结束"的承诺。
     * 同样只写 state，理由见 [publishTimeoutState]。
     */
    private fun publishErrorState(throwable: Throwable) {
        val cause = unwrap(throwable)
        val message = cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
        val current = stateRef.get()
        if (current.isLoaded && !current.isError) return
        publish(State(isLoaded = true, isError = true, errorMessage = message))
        Changelog.LOGGER.error("Changelog load failed unexpectedly", cause)
    }

    private fun loadFromRemote(urlStr: String, forceRefresh: Boolean, errors: SourceErrors): ChangelogData? {
        if (urlStr.isBlank()) return null
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
                    return tryLoadCache(errors)
                }

                HttpURLConnection.HTTP_OK -> Unit

                else -> {
                    errors.last = "HTTP $code"
                    Changelog.LOGGER.warn("Changelog request failed with HTTP {}", code)
                    return null
                }
            }

            val parsed = conn.inputStream.use { parseJson(it.readAllBytes(), errors) } ?: return null
            saveCache(parsed, conn.getHeaderField("ETag"))
            Changelog.LOGGER.info("Changelog loaded from remote, {} entries", parsed.entries.size)
            parsed
        } catch (e: Exception) {
            errors.last = e.message ?: e.javaClass.simpleName
            Changelog.LOGGER.warn("Failed to fetch remote changelog, falling back to cache", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun tryLoadCache(errors: SourceErrors): ChangelogData? = try {
        if (Files.exists(cacheFile)) {
            parseJson(Files.readAllBytes(cacheFile), errors)?.also {
                Changelog.LOGGER.info("Changelog loaded from disk cache, {} entries", it.entries.size)
            }
        } else {
            null
        }
    } catch (e: Exception) {
        Changelog.LOGGER.warn("Failed to read changelog cache", e)
        null
    }

    /** JAR 内置的兜底数据 */
    private fun loadFromResources(errors: SourceErrors): ChangelogData? = try {
        ChangelogLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.use { stream ->
            parseJson(stream.readAllBytes(), errors)?.also {
                Changelog.LOGGER.info("Changelog loaded from bundled resources, {} entries", it.entries.size)
            }
        }
    } catch (e: Exception) {
        Changelog.LOGGER.error("Failed to load bundled changelog", e)
        null
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

    private fun parseJson(bytes: ByteArray, errors: SourceErrors): ChangelogData? = try {
        gson.fromJson(String(bytes, StandardCharsets.UTF_8), ChangelogData::class.java)
    } catch (e: JsonParseException) {
        // URL 指向 HTML 页面（例如 GitHub blob 链接）时会走到这里
        errors.last = "Invalid changelog JSON"
        Changelog.LOGGER.error("Failed to parse changelog JSON", e)
        null
    }
}
