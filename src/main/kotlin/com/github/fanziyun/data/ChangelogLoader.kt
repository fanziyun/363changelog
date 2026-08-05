package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer
import com.google.gson.Gson
import com.google.gson.JsonParseException
import net.fabricmc.loader.api.FabricLoader
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

object ChangelogLoader {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_CHANGELOG_BYTES = 4 * 1024 * 1024
    private const val USER_AGENT = "363Changelog"
    private const val BUNDLED_RESOURCE = "/changelog.json"

    private data class State(
        val isLoaded: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String = "",
        val remoteError: String = "",
    )

    private data class LoadRequest(
        val remoteUrl: String,
        val forceRefresh: Boolean,
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
        FabricLoader.getInstance().gameDir.resolve(".cache").resolve(Changelog.MOD_ID)
    }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Loader").apply { isDaemon = true }
    }

    private val stateRef = AtomicReference(State())
    private val dataRef = AtomicReference(ChangelogData.EMPTY)
    private val lock = Any()
    private var activeLoad: ActiveLoad? = null
    private var lastCompletedUrl: String? = null

    val isLoaded: Boolean get() = stateRef.get().isLoaded
    val isError: Boolean get() = stateRef.get().isError
    val errorMessage: String get() = stateRef.get().errorMessage
    val remoteError: String get() = stateRef.get().remoteError
    val data: ChangelogData get() = dataRef.get()

    val latestVersion: String
        get() = data.entries
            .asSequence()
            .map(ChangelogEntry::version)
            .filter(String::isNotBlank)
            .maxWithOrNull(SemVer.COMPARATOR)
            .orEmpty()

    fun load(remoteUrl: String, forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        val request = LoadRequest(remoteUrl.trim(), forceRefresh)
        synchronized(lock) {
            val running = activeLoad?.takeIf { !it.future.isDone }
            if (running != null) {
                val sameUrl = running.request.remoteUrl == request.remoteUrl
                val satisfiesRefresh = !request.forceRefresh || running.request.forceRefresh
                if (sameUrl && satisfiesRefresh) return running.future

                return running.future.handle { _, _ -> Unit }
                    .thenCompose { load(request.remoteUrl, request.forceRefresh) }
            }

            val future = CompletableFuture.supplyAsync({ doLoad(request) }, executor)
            activeLoad = ActiveLoad(request, future)
            future.whenComplete { _, _ ->
                synchronized(lock) {
                    if (activeLoad?.future === future) activeLoad = null
                }
            }
            return future
        }
    }

    fun ensureLoaded(remoteUrl: String): CompletableFuture<Boolean> {
        val normalizedUrl = remoteUrl.trim()
        synchronized(lock) {
            val running = activeLoad?.takeIf { !it.future.isDone }
            if (running?.request?.remoteUrl == normalizedUrl) return running.future

            val state = stateRef.get()
            if (lastCompletedUrl == normalizedUrl && state.isLoaded && !state.isError) {
                return CompletableFuture.completedFuture(true)
            }
        }
        return load(normalizedUrl)
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
            val remote = loadFromRemote(request, cacheFiles)
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
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        Changelog.LOGGER.error("Failed to load changelog from any source: {}", message)
        return false
    }

    private fun completeSuccess(request: LoadRequest, loadedData: ChangelogData, remoteError: String): Boolean {
        dataRef.set(loadedData)
        stateRef.set(State(isLoaded = true, remoteError = remoteError))
        synchronized(lock) { lastCompletedUrl = request.remoteUrl }
        return true
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
