package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.config.ModConfig
import com.github.fanziyun.util.SemVer
import com.github.fanziyun.platform.Platform
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
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

    private val gson: Gson = Gson()

    // 所有 doLoad 都排在这一个线程上，两次加载因此不可能真正并行。
    // 这一点是正确性前提，不只是省线程：orTimeout 只能提前完成 future、不能中断卡在
    // socket 里的加载，没有这个串行化，超时后新发起的加载会与上一次同时跑，抢着写缓存、
    // 抢着发布终态。daemon 线程，不会拖住游戏退出。
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Loader").apply { isDaemon = true }
    }

    private data class LoadRequest(
        val remoteUrl: String,
        val forceRefresh: Boolean,
        val timeoutMs: Long,
        val deadlineNanos: Long,
        /** 单调递增的加载序号，用于判断一个终态属于哪一轮加载，见 [publishTerminal] */
        val epoch: Long,
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
     * 各字段必须原子地一起发布：超时兜底跑在 JDK Delayer 线程上，而卡住的加载线程随后
     * 可能仍会写入自己的结果，分开的 @Volatile 字段会被撕裂成"这轮已成功"配"上轮的序号"
     * 这种自相矛盾的组合。
     *
     * [epoch] 是这个状态所属的加载轮次，0 表示"还没有任何加载开始"。有了它，
     * 「这一轮是否已经有终态了」才能与「上一轮留下的终态」区分开——否则第一次成功
     * 之后的每次超时都会被误判成"我已经成功了"而被静默丢掉。
     *
     * 失败原因不进这里：没有任何界面显示它，只在发布时打一条日志就够了。
     */
    private data class State(
        val epoch: Long = 0,
        val isLoaded: Boolean = false,
        val isError: Boolean = false,
    )

    private val stateRef = AtomicReference(State())

    /**
     * 最近一次加载的所有来源都失败了（含超时兜底），当前展示的可能是空数据或上次的旧数据。
     *
     * 版本检测据此决定要不要下结论：失败时手上大概率是空数据，拿去比较会得出
     * "已是最新版本"这种凭空的结论。
     */
    val isError: Boolean get() = stateRef.get().isError

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
    /** 正在进行的加载请求的 URL；用于判断调用方要的是不是同一个地址 */
    private var inFlightUrl: String? = null
    /** 上一次完成加载的 URL，用于判断已保留的数据是否属于当前请求 */
    private var lastCompletedUrl: String? = null
    /** 加载序号发号器，只在 lock 内自增 */
    private var epochCounter: Long = 0

    @Volatile
    private var cachedEtag: String? = null

    /**
     * 加载更新日志。同一时刻只会有一次加载在进行。
     *
     * 复用正在进行的加载有个前提：它得满足调用方的意图。普通加载可以搭已有的车，
     * 但强制刷新不能——否则客户端初始化时发起的那次普通加载还没结束时点"刷新"，
     * 就会拿到一个仍然带 If-None-Match 的请求，服务端回 304，用户看到的还是旧数据。
     * URL 不同同样不能搭车：那是另一份数据。
     *
     * @param forceRefresh 忽略 ETag 与磁盘缓存，强制重新拉取远程数据
     * @param timeoutMs 单轮加载的端到端预算：无论网络如何挂死，这一轮必定在此期限内出结果。
     *   若调用要排在另一轮之后（见下），返回的 future 最坏要等两轮，但每轮各自有界。
     */
    fun load(
        remoteUrl: String,
        forceRefresh: Boolean = false,
        timeoutMs: Long = ModConfig.DEFAULT_LOAD_TIMEOUT_SECONDS * 1000L,
    ): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(lock) {
            val running = inFlight?.takeIf { !it.isDone }
            if (running != null) {
                val sameUrl = inFlightUrl == normalizedUrl
                if (sameUrl && (!forceRefresh || inFlightIsForced)) return running
                // 想强制刷新但在跑的是普通加载（或换了 URL）：等它结束后再真正发一次请求，
                // 而不是并发两个请求去抢着写缓存。注意返回的 future 因此可能耗时约两倍
                // timeoutMs——每一段各自有界，但串起来会累加。
                return running.handle { _, _ -> null }
                    .thenCompose { load(normalizedUrl, forceRefresh, timeoutMs) }
            }

            // 在 doLoad 重置状态之前、加锁瞬间捕获"该 URL 是否仍有可展示的数据"，
            // 供失败/超时发布时决定保留上次好数据；也避免在 Delayer 线程上无锁读 lastCompletedUrl。
            // 判断依据是"该 URL 是否仍有已保留的数据"，而不是瞬时错误标志——一次保留数据的超时
            // 会把 state 标成 error 但保留 _data，随后的第二次超时也必须仍能识别这份数据。
            val lastState = stateRef.get()
            val keepExistingData =
                lastCompletedUrl == normalizedUrl &&
                    ((lastState.isLoaded && !lastState.isError) || !_data.get().isEmpty)
            val request = LoadRequest(
                remoteUrl = normalizedUrl,
                forceRefresh = forceRefresh,
                timeoutMs = timeoutMs,
                deadlineNanos = deadlineNanos,
                epoch = ++epochCounter,
                keepExistingData = keepExistingData,
            )

            inFlightIsForced = forceRefresh
            inFlightUrl = normalizedUrl
            // orTimeout 是安全网：即使 doLoad 因为 DNS 卡死/慢速滴流而无限期不返回，
            // 返回给调用方的 future 也保证在 timeoutMs 内完成，UI 因此总能离开"加载中"。
            // 它只是提前完成 future，并不能中断卡住的加载线程——串行 executor 才是
            // "两次加载不会同时跑"的保证。
            val bounded = CompletableFuture.supplyAsync({ doLoad(request) }, executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally { exception ->
                    // 超时与"doLoad 抛异常"（如 Platform 解析失败）都不能把 state 留在
                    // loading 上，否则界面永远转圈，违背"加载一定在期限内结束"的承诺
                    publishInterrupted(request, unwrap(exception))
                }
            inFlight = bounded
            return bounded
        }
    }

    /**
     * 若还没加载过就触发一次加载，否则复用上一次的结果。
     * 用于每次进入标题界面时避免重复发起网络请求。
     */
    fun ensureLoaded(
        remoteUrl: String,
        timeoutMs: Long = ModConfig.DEFAULT_LOAD_TIMEOUT_SECONDS * 1000L,
    ): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        synchronized(lock) {
            val running = inFlight?.takeIf { !it.isDone }
            // 同一个 URL 正在加载 → 搭车。URL 变了（用户改了配置）就必须重新拉，
            // 否则界面会一直显示上一个地址的数据，只能靠手动刷新。
            if (running != null && inFlightUrl == normalizedUrl) return running

            // 已经成功过就复用。这里不再额外要求"数据非空"：终态带 epoch 之后，
            // "状态说成功但数据是空的"只可能是一次合法的空更新日志，再重试也是同样的结果；
            // 真正失败的加载（含超时兜底）都带 isError，自然会走下面的重新加载。
            val state = stateRef.get()
            if (lastCompletedUrl == normalizedUrl && state.isLoaded && !state.isError) {
                return CompletableFuture.completedFuture(true)
            }
        }
        return load(normalizedUrl, timeoutMs = timeoutMs)
    }

    private fun doLoad(request: LoadRequest): Boolean {
        // 本轮开始：把状态推进到"本轮、尚无终态"。epoch 只增不减，所以上一轮迟到的
        // 兜底再也写不进来；而本轮自己的终态仍然写得进去。少了这一步，上一次的成功
        // 会让本次的超时/异常兜底误判成"我已经成功了"而被静默丢掉。
        beginLoad(request.epoch)

        var lastError = ""
        fun fail(message: String): ChangelogData? {
            lastError = message
            return null
        }

        // 端到端超时：deadline 一到就跳过远端，直接走 cache -> bundled 兜底，绝不无限期等网络。
        // 每个阶段本身仍受 connect/read 超时限制，这里的检查只是把"已超时"提前暴露。
        val remote = if (deadlineExpired(request)) {
            fail(timeoutMessage(request))
        } else {
            loadFromRemote(request.remoteUrl, request.forceRefresh, ::fail)
        }

        // 远程失败不是致命错误：只要能回退到缓存或内置资源，界面依然可用
        val loaded = remote ?: tryLoadCache(::fail) ?: loadFromResources(::fail)
        return if (loaded != null) {
            completeSuccess(request, loaded)
        } else {
            completeFailure(request, lastError.ifBlank { "No changelog source available" })
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
        if (isSuperseded(request)) return false
        _data.set(loadedData)
        publishResult(request.epoch, State(request.epoch, isLoaded = true))
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        return true
    }

    private fun completeFailure(request: LoadRequest, message: String): Boolean {
        if (isSuperseded(request)) return false
        // 全部来源失败时保留同 URL 上次成功的数据，避免网络抖动把可用列表清空
        if (!request.keepExistingData) _data.set(ChangelogData.EMPTY)
        val published = publishResult(request.epoch, State(request.epoch, isLoaded = true, isError = true))
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        if (published) Changelog.LOGGER.error("Failed to load changelog from any source: {}", message)
        return false
    }

    /**
     * 本轮已经被更新的一轮取代了吗？
     *
     * 会发生在：上一次加载卡在网络里迟迟不返回，它的超时兜底完成了 future，调用方于是发起了
     * 下一次加载（在串行 executor 上排队）——排队的这次超时兜底也可能先于卡住的那次返回而
     * 发布终态。此时卡住的那次的结果已经过期，连 [_data] 都不该再覆盖。
     */
    private fun isSuperseded(request: LoadRequest): Boolean = stateRef.get().epoch > request.epoch

    /**
     * 本次加载开始：把状态推进到"本轮、尚无终态"（isLoaded=false）。
     *
     * 与 [publishResult] 共用 epoch 单调性规则，所以上一轮迟到的兜底再也写不进来。
     * 它本身不递增 [generation]——界面只关心数据变化，"又开始加载了"不该触发无意义的重排。
     */
    private fun beginLoad(epoch: Long) {
        while (true) {
            val current = stateRef.get()
            if (current.epoch >= epoch) return
            if (stateRef.compareAndSet(current, State(epoch))) return
        }
    }

    /**
     * 加载线程发布真实结果。除了"更新的一轮已经发布过终态"，一律算数——包括覆盖本轮
     * 超时兜底刚刚发布的那个错误态：迟到的成功正是要靠这一步让界面收敛过来。
     */
    private fun publishResult(epoch: Long, state: State): Boolean =
        publishTerminal(state) { current -> current.epoch <= epoch }

    /**
     * 兜底线程（JDK Delayer / exceptionally）发布超时或异常态：只在本轮尚无终态、
     * 且没有更新一轮接手时才写。
     */
    private fun publishFallback(epoch: Long, state: State): Boolean =
        publishTerminal(state) { current ->
            current.epoch < epoch || (current.epoch == epoch && !current.isLoaded)
        }

    /**
     * 发布终态并递增生成号，让仍打开的界面下一帧收敛到新数据。
     *
     * 判定与写入必须是同一个原子操作：兜底跑在 Delayer 线程上、与加载线程真正并发，
     * check-then-act 会让超时错误覆盖掉刚好在检查之后发布的真实成功。
     *
     * @return 是否真的由本次调用写入（调用方据此决定要不要打日志，避免重复输出）
     */
    private fun publishTerminal(state: State, canWrite: (State) -> Boolean): Boolean {
        while (true) {
            val current = stateRef.get()
            if (!canWrite(current)) return false
            if (stateRef.compareAndSet(current, state)) {
                generation.incrementAndGet()
                return true
            }
        }
    }

    private fun deadlineExpired(request: LoadRequest): Boolean =
        System.nanoTime() - request.deadlineNanos >= 0

    private fun timeoutMessage(request: LoadRequest): String =
        "Changelog load timed out after ${request.timeoutMs / 1000}s"

    private fun unwrap(throwable: Throwable): Throwable =
        if (throwable is CompletionException) throwable.cause ?: throwable else throwable

    private fun describe(cause: Throwable): String =
        cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName

    /**
     * 超时兜底与 doLoad 抛异常的共同出口：在 JDK Delayer / exceptionally 线程上发布一个终态，
     * 让 UI 离开"加载中"。否则 state 会停在 loading、界面永远转圈，违背"加载一定在期限内结束"。
     *
     * 只写 state，不动 [_data]：卡住的 loader 线程此时可能正好走到 [completeSuccess]，
     * 在这里清空数据就会把那次成功的结果抹掉，而本线程无从恢复。数据的清空交给同样跑在
     * loader 线程上的 [completeFailure]，它与各来源的写入天然有序。
     *
     * @return 交给 exceptionally 的返回值：本次加载确实成功过就报 true，否则 false
     */
    private fun publishInterrupted(request: LoadRequest, cause: Throwable): Boolean {
        if (!publishFallback(request.epoch, State(request.epoch, isLoaded = true, isError = true))) {
            // 没写进去：本轮的终态已经由加载线程发布了（成功就报 true，失败它自己记过日志），
            // 或者已经有更新的一轮接手（那轮的结果才算数）。
            return !stateRef.get().isError
        }

        if (cause is TimeoutException) {
            Changelog.LOGGER.warn(
                "Changelog load timed out after {}s; {}",
                request.timeoutMs / 1000,
                if (request.keepExistingData) "keeping last-good data" else "no usable data yet",
            )
        } else {
            Changelog.LOGGER.error("Changelog load failed unexpectedly", cause)
        }
        return false
    }

    /**
     * 各来源共用的失败回调 [fail]：记下原因并返回 null，让 doLoad 的回退链继续往下走。
     * 用回调而不是共享字段，是因为原因只属于当次加载，不能串到下一次的终态里去。
     */
    private fun loadFromRemote(
        urlStr: String,
        forceRefresh: Boolean,
        fail: (String) -> ChangelogData?,
    ): ChangelogData? {
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
                    return tryLoadCache(fail)
                }

                HttpURLConnection.HTTP_OK -> Unit

                else -> {
                    Changelog.LOGGER.warn("Changelog request failed with HTTP {}", code)
                    return fail("HTTP $code")
                }
            }

            val parsed = conn.inputStream.use { parseJson(it.readAllBytes(), fail) } ?: return null
            saveCache(parsed, conn.getHeaderField("ETag"))
            Changelog.LOGGER.info("Changelog loaded from remote, {} entries", parsed.entries.size)
            parsed
        } catch (e: Exception) {
            Changelog.LOGGER.warn("Failed to fetch remote changelog, falling back to cache", e)
            fail(describe(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun tryLoadCache(fail: (String) -> ChangelogData?): ChangelogData? = try {
        if (Files.exists(cacheFile)) {
            parseJson(Files.readAllBytes(cacheFile), fail)?.also {
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
    private fun loadFromResources(fail: (String) -> ChangelogData?): ChangelogData? = try {
        ChangelogLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.use { stream ->
            parseJson(stream.readAllBytes(), fail)?.also {
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

    /**
     * 写缓存。先写临时文件再原子改名：加载虽然是串行的，但游戏可以开多个实例共用同一个
     * .minecraft 目录，直接截断写会让另一个实例读到写了一半的 JSON。
     * ETag 与正文分两个文件，中途崩溃可能让两者不配对——所以先落正文，再落 ETag，
     * 这样最坏情况是 ETag 偏旧（多下一次），而不是拿新 ETag 配旧正文（永远看不到更新）。
     */
    private fun saveCache(data: ChangelogData, etagHeader: String?) {
        try {
            Files.createDirectories(cacheDir)
            writeAtomically(cacheFile, gson.toJson(data))

            // 原样保存服务端返回的 ETag（含引号 / W/ 前缀），下次直接回发
            val etag = etagHeader?.trim().orEmpty()
            cachedEtag = etag
            if (etag.isNotBlank()) writeAtomically(etagFile, etag)
            else Files.deleteIfExists(etagFile)
        } catch (e: Exception) {
            Changelog.LOGGER.warn("Failed to write changelog cache", e)
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // 某些文件系统（如部分网络盘）不支持原子改名，退回普通改名
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun parseJson(bytes: ByteArray, fail: (String) -> ChangelogData?): ChangelogData? = try {
        gson.fromJson(String(bytes, StandardCharsets.UTF_8), ChangelogData::class.java)
    } catch (e: JsonParseException) {
        // URL 指向 HTML 页面（例如 GitHub blob 链接）时会走到这里
        Changelog.LOGGER.error("Failed to parse changelog JSON", e)
        fail("Invalid changelog JSON")
    }
}
