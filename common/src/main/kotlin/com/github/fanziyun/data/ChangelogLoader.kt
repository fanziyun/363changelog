package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.github.fanziyun.platform.Platform
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ChangelogLoader {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_CHANGELOG_BYTES = 4 * 1024 * 1024
    private const val USER_AGENT = "363Changelog"
    private const val BUNDLED_RESOURCE = "/changelog.json"
    // 端到端超时。connectTimeout 只管 TCP 连接、管不到 DNS；readTimeout 是每次读而非整体，
    // 所以网络挂死时只有这个全局 deadline 才能保证加载一定在期限内结束、UI 不会永远停在"加载中"。
    // 这是 load()/ensureLoaded() 公共 API 的兜底默认；用户可配置的值来自 ModConfig.loadTimeoutSeconds。
    private const val DEFAULT_LOAD_TIMEOUT_MS = 30_000L

    private data class State(
        val isLoaded: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String = "",
        val remoteError: String = "",
    )

    private data class LoadRequest(
        val remoteUrl: String,
        val forceRefresh: Boolean,
        val timeoutMs: Long,
        val deadlineNanos: Long,
        /** 在 load() 加锁瞬间捕获：该 URL 之前是否已有一次成功加载，超时时据此保留上次好数据 */
        val keepDataOnTimeout: Boolean,
    )

    private data class ActiveLoad(
        val request: LoadRequest,
        val future: CompletableFuture<Boolean>,
    )

    private data class CacheFiles(
        val data: Path,
        val etag: Path,
    )

    private data class SourceResult(
        val data: ChangelogData? = null,
        val error: String = "",
    ) {
        val isSuccess: Boolean get() = data != null

        companion object {
            fun success(data: ChangelogData) = SourceResult(data = data)
            fun failure(error: String = "") = SourceResult(error = error)
        }
    }

    private sealed interface HttpResult {
        data class Success(val data: ChangelogData, val etag: String?) : HttpResult
        data object NotModified : HttpResult
        data class Failure(val message: String, val cause: Exception? = null) : HttpResult
    }

    private val gson = Gson()
    private val cacheDir: Path by lazy {
        Platform.INSTANCE.gameDir.resolve(".cache").resolve(Changelog.MOD_ID)
    }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Loader").apply { isDaemon = true }
    }

    private val stateRef = AtomicReference(State())
    private val dataRef = AtomicReference(ChangelogData.EMPTY)
    // 每次发布终态（成功/失败/超时）递增；界面据此在仍打开时重建，避免迟到结果被错过
    private val generation = AtomicLong()
    private val lock = Any()
    private var activeLoad: ActiveLoad? = null
    private var lastCompletedUrl: String? = null

    val isLoaded: Boolean get() = stateRef.get().isLoaded
    val isError: Boolean get() = stateRef.get().isError
    val errorMessage: String get() = stateRef.get().errorMessage
    val remoteError: String get() = stateRef.get().remoteError
    val data: ChangelogData get() = dataRef.get()
    val dataVersion: Long get() = generation.get()

    val latestVersion: String
        get() = data.entries
            .asSequence()
            .map(ChangelogEntry::version)
            .filter(String::isNotBlank)
            .maxWithOrNull(SemVer.COMPARATOR)
            .orEmpty()

    fun shutdown() {
        synchronized(lock) {
            activeLoad?.future?.cancel(true)
            activeLoad = null
            lastCompletedUrl = null
        }
        executor.shutdownNow()
        runCatching { executor.awaitTermination(2, TimeUnit.SECONDS) }
        dataRef.set(ChangelogData.EMPTY)
        stateRef.set(State())
        generation.incrementAndGet()
    }

    fun load(
        remoteUrl: String,
        forceRefresh: Boolean = false,
        timeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS,
    ): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(lock) {
            // 在 doLoad 重置 stateRef 之前、加锁瞬间捕获"该 URL 是否仍有可展示的数据"，
            // 供超时发布时决定保留上次好数据；也避免在 Delayer 线程上无锁读 lastCompletedUrl。
            // 判断依据是"该 URL 是否仍有已保留的数据"，而不是瞬时错误标志——一次保留数据的超时
            // 会把 state 标成 error 但保留 dataRef，随后的第二次超时也必须仍能识别这份数据。
            val lastState = stateRef.get()
            val keepDataOnTimeout =
                lastCompletedUrl == normalizedUrl &&
                    ((lastState.isLoaded && !lastState.isError) || dataRef.get() != ChangelogData.EMPTY)
            val request = LoadRequest(normalizedUrl, forceRefresh, timeoutMs, deadlineNanos, keepDataOnTimeout)

            val running = activeLoad?.takeIf { !it.future.isDone }
            if (running != null) {
                val sameUrl = running.request.remoteUrl == request.remoteUrl
                val satisfiesRefresh = !request.forceRefresh || running.request.forceRefresh
                if (sameUrl && satisfiesRefresh) return running.future

                return running.future.handle { _, _ -> Unit }
                    .thenCompose { load(request.remoteUrl, request.forceRefresh, request.timeoutMs) }
            }

            val future = CompletableFuture.supplyAsync({ doLoad(request) }, executor)
            // orTimeout 是安全网：即使 doLoad 因为 DNS 卡死/慢速滴流而无限期不返回，
            // 返回给调用方的 future 也保证在 timeoutMs 内完成，UI 因此总能离开"加载中"。
            val bounded = future
                .orTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally { exception ->
                    when (exception) {
                        is TimeoutException -> {
                            // doLoad 其实已成功（completeSuccess 已发布 success）时，别把成功报成超时
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
                            publishErrorState(request, exception)
                            false
                        }
                    }
                }
            activeLoad = ActiveLoad(request, bounded)
            bounded.whenComplete { _, _ ->
                synchronized(lock) {
                    if (activeLoad?.future === bounded) activeLoad = null
                }
            }
            return bounded
        }
    }

    fun ensureLoaded(remoteUrl: String, timeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        synchronized(lock) {
            val running = activeLoad?.takeIf { !it.future.isDone }
            if (running?.request?.remoteUrl == normalizedUrl) return running.future

            val state = stateRef.get()
            if (lastCompletedUrl == normalizedUrl && state.isLoaded && !state.isError) {
                return CompletableFuture.completedFuture(true)
            }
        }
        return load(normalizedUrl, timeoutMs = timeoutMs)
    }

    private fun doLoad(request: LoadRequest): Boolean {
        val previous = stateRef.get()
        // 刷新全部失败时保留同 URL 上次成功的数据，避免网络抖动把可用列表清空
        val keepDataOnFailure = previous.isLoaded && !previous.isError && lastCompletedUrl == request.remoteUrl
        stateRef.set(State())

        var remoteError = ""
        val failures = mutableListOf<String>()
        val cacheFiles = request.remoteUrl.takeIf(String::isNotBlank)?.let(::cacheFilesFor)

        if (cacheFiles != null) {
            // 端到端超时：deadline 一到就跳过远端，直接走 cache -> bundled 兜底，绝不无限期等网络。
            // 每个阶段本身仍受 connect/read 超时限制，这里的检查只是把"已超时"提前暴露。
            val remote = if (deadlineExpired(request)) {
                SourceResult.failure(timeoutMessage(request))
            } else {
                loadFromRemote(request, cacheFiles)
            }
            if (remote.isSuccess) return completeSuccess(request, remote.data!!, remoteError = "")
            remote.error.takeIf(String::isNotBlank)?.let {
                failures.add(it)
                remoteError = it
            }

            val cached = loadFromCache(cacheFiles)
            if (cached.isSuccess) return completeSuccess(request, cached.data!!, remoteError)
            cached.error.takeIf(String::isNotBlank)?.let(failures::add)
        }

        val bundled = loadFromResources()
        if (bundled.isSuccess) return completeSuccess(request, bundled.data!!, remoteError)
        bundled.error.takeIf(String::isNotBlank)?.let(failures::add)

        val message = failures.distinct().joinToString("; ")
            .ifBlank { "No changelog source available" }
        if (!keepDataOnFailure) dataRef.set(ChangelogData.EMPTY)
        stateRef.set(State(isLoaded = true, isError = true, errorMessage = message, remoteError = remoteError))
        generation.incrementAndGet()
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        Changelog.LOGGER.error("Failed to load changelog from any source: {}", message)
        return false
    }

    private fun completeSuccess(request: LoadRequest, loadedData: ChangelogData, remoteError: String): Boolean {
        dataRef.set(loadedData)
        stateRef.set(State(isLoaded = true, remoteError = remoteError))
        generation.incrementAndGet()
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        return true
    }

    private fun deadlineExpired(request: LoadRequest): Boolean =
        System.nanoTime() >= request.deadlineNanos

    private fun timeoutMessage(request: LoadRequest): String =
        "Changelog load timed out after ${request.timeoutMs / 1000}s"

    /**
     * orTimeout 触发的兜底：在 JDK Delayer daemon 线程上发布一个终态，让 UI 离开"加载中"。
     * 只做锁无关的原子状态写，不做任何 IO —— 卡住的 loader 线程此时可能仍在跑，等它返回后
     * 它的原子写会覆盖这里发布的超时态；界面通过 dataVersion 生成号在仍打开时重建收敛。
     */
    private fun publishTimeoutState(request: LoadRequest) {
        // 若 doLoad 其实已经成功（completeSuccess 已把 state 写成成功），说明 Delayer 在
        // future 完成 CAS 上赢了正常完成——别再用超时错误覆盖这次真实成功。
        val current = stateRef.get()
        if (current.isLoaded && !current.isError) return

        val message = timeoutMessage(request)
        if (!request.keepDataOnTimeout) dataRef.set(ChangelogData.EMPTY)
        stateRef.set(State(isLoaded = true, isError = true, errorMessage = message, remoteError = message))
        generation.incrementAndGet()
        Changelog.LOGGER.warn(
            "Changelog load timed out after {}s; {}",
            request.timeoutMs / 1000,
            if (request.keepDataOnTimeout) "keeping last-good data" else "no usable data yet",
        )
    }

    /**
     * doLoad 抛出的非超时异常落到这里（exceptionally 线程上执行，无 IO）：发布终态错误。
     * 否则 state 会停在 loading、界面永远转圈，违背"加载一定在期限内结束"的承诺。
     */
    private fun publishErrorState(request: LoadRequest, throwable: Throwable) {
        val cause = if (throwable is CompletionException) throwable.cause ?: throwable else throwable
        val message = cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
        val current = stateRef.get()
        if (current.isLoaded && !current.isError) return
        if (!request.keepDataOnTimeout) dataRef.set(ChangelogData.EMPTY)
        stateRef.set(State(isLoaded = true, isError = true, errorMessage = message, remoteError = message))
        generation.incrementAndGet()
        Changelog.LOGGER.error("Changelog load failed unexpectedly", cause)
    }

    private fun loadFromRemote(request: LoadRequest, cacheFiles: CacheFiles): SourceResult {
        val uri = parseHttpUri(request.remoteUrl)
            ?: return SourceResult.failure("Changelog URL must use HTTP or HTTPS")
        val etag = if (!request.forceRefresh && Files.exists(cacheFiles.data)) readEtag(cacheFiles.etag) else null

        return when (val result = fetch(uri, etag)) {
            is HttpResult.Success -> storeRemoteResult(cacheFiles, result)
            HttpResult.NotModified -> {
                val cached = loadFromCache(cacheFiles)
                if (cached.isSuccess) {
                    Changelog.LOGGER.info("Changelog unchanged (304), reusing disk cache")
                    cached
                } else {
                    Changelog.LOGGER.warn("Server returned 304 but the local cache is unavailable; retrying without ETag")
                    when (val retry = fetch(uri, null)) {
                        is HttpResult.Success -> storeRemoteResult(cacheFiles, retry)
                        HttpResult.NotModified -> SourceResult.failure("HTTP 304 without a usable cache")
                        is HttpResult.Failure -> remoteFailure(retry)
                    }
                }
            }
            is HttpResult.Failure -> remoteFailure(result)
        }
    }

    private fun storeRemoteResult(cacheFiles: CacheFiles, result: HttpResult.Success): SourceResult {
        saveCache(cacheFiles, result.data, result.etag)
        Changelog.LOGGER.info("Changelog loaded from remote, {} entries", result.data.entries.size)
        return SourceResult.success(result.data)
    }

    private fun remoteFailure(failure: HttpResult.Failure): SourceResult {
        if (failure.cause != null) {
            Changelog.LOGGER.warn("Failed to fetch remote changelog, falling back to cache", failure.cause)
        } else {
            Changelog.LOGGER.warn("Changelog request failed: {}", failure.message)
        }
        return SourceResult.failure(failure.message)
    }

    private fun fetch(uri: URI, etag: String?): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                etag?.let { setRequestProperty("If-None-Match", it) }
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val parsed = connection.inputStream.use { stream ->
                        parseJson(stream.readLimitedBytes(), "remote changelog")
                    }
                    if (parsed.isSuccess) {
                        HttpResult.Success(parsed.data!!, connection.getHeaderField("ETag"))
                    } else {
                        HttpResult.Failure(parsed.error)
                    }
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> HttpResult.NotModified
                else -> HttpResult.Failure("HTTP $code")
            }
        } catch (exception: Exception) {
            HttpResult.Failure(exception.message ?: exception.javaClass.simpleName, exception)
        } finally {
            connection?.disconnect()
        }
    }

    private fun loadFromCache(cacheFiles: CacheFiles): SourceResult {
        if (!Files.isRegularFile(cacheFiles.data)) return SourceResult.failure()
        return try {
            if (Files.size(cacheFiles.data) > MAX_CHANGELOG_BYTES) {
                invalidateCache(cacheFiles)
                return SourceResult.failure("Cached changelog exceeds the size limit")
            }

            val parsed = parseJson(Files.readAllBytes(cacheFiles.data), "cached changelog")
            if (parsed.isSuccess) {
                Changelog.LOGGER.info("Changelog loaded from disk cache, {} entries", parsed.data!!.entries.size)
            } else {
                invalidateCache(cacheFiles)
            }
            parsed
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to read changelog cache", exception)
            SourceResult.failure(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun loadFromResources(): SourceResult {
        return try {
            val stream = ChangelogLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)
                ?: return SourceResult.failure("Bundled changelog resource is missing")
            stream.use {
                val parsed = parseJson(it.readLimitedBytes(), "bundled changelog")
                if (parsed.isSuccess) {
                    Changelog.LOGGER.info(
                        "Changelog loaded from bundled resources, {} entries",
                        parsed.data!!.entries.size,
                    )
                }
                parsed
            }
        } catch (exception: Exception) {
            Changelog.LOGGER.error("Failed to load bundled changelog", exception)
            SourceResult.failure(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun parseJson(bytes: ByteArray, source: String): SourceResult {
        return try {
            val parsed = gson.fromJson(String(bytes, StandardCharsets.UTF_8), ChangelogData::class.java)
                ?: return SourceResult.failure("$source is empty")
            SourceResult.success(parsed)
        } catch (exception: JsonParseException) {
            SourceResult.failure("Invalid JSON in $source: ${exception.message ?: exception.javaClass.simpleName}")
        }
    }

    private fun parseHttpUri(raw: String): URI? = runCatching { URI.create(raw) }
        .getOrNull()
        ?.takeIf { uri ->
            uri.isAbsolute && !uri.host.isNullOrBlank() &&
                (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))
        }

    private fun cacheFilesFor(remoteUrl: String): CacheFiles {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(remoteUrl.toByteArray(StandardCharsets.UTF_8))
        val key = HexFormat.of().formatHex(digest).take(24)
        return CacheFiles(cacheDir.resolve("$key.json"), cacheDir.resolve("$key.etag"))
    }

    private fun readEtag(path: Path): String? = try {
        Files.readString(path, StandardCharsets.UTF_8).trim().ifBlank { null }
    } catch (_: Exception) {
        null
    }

    private fun saveCache(cacheFiles: CacheFiles, loadedData: ChangelogData, etagHeader: String?) {
        try {
            Files.createDirectories(cacheDir)
            writeAtomically(cacheFiles.data, gson.toJson(loadedData))

            val etag = etagHeader?.trim().orEmpty()
            if (etag.isNotBlank()) writeAtomically(cacheFiles.etag, etag)
            else Files.deleteIfExists(cacheFiles.etag)
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to write changelog cache", exception)
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun invalidateCache(cacheFiles: CacheFiles) {
        try {
            Files.deleteIfExists(cacheFiles.data)
            Files.deleteIfExists(cacheFiles.etag)
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to remove invalid changelog cache", exception)
        }
    }

    private fun InputStream.readLimitedBytes(): ByteArray {
        val bytes = readNBytes(MAX_CHANGELOG_BYTES + 1)
        if (bytes.size > MAX_CHANGELOG_BYTES) {
            throw IOException("Changelog exceeds the ${MAX_CHANGELOG_BYTES / 1024 / 1024} MiB size limit")
        }
        return bytes
    }
}
