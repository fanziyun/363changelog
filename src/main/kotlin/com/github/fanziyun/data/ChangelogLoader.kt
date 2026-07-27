package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.github.fanziyun.util.SemVer
import com.google.gson.Gson
import com.google.gson.JsonParseException
import net.fabricmc.loader.api.FabricLoader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

object ChangelogLoader {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"
    private const val BUNDLED_RESOURCE = "/changelog.json"

    private val gson = Gson()

    private val cacheDir: Path by lazy { FabricLoader.getInstance().gameDir.resolve(".cache") }
    private val cacheFile: Path by lazy { cacheDir.resolve("changelog_cache.json") }
    private val etagFile: Path by lazy { cacheDir.resolve("changelog_cache.etag") }

    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var isError: Boolean = false
        private set

    @Volatile
    var errorMessage: String = ""
        private set

    private val dataRef = AtomicReference(ChangelogData.EMPTY)
    val data: ChangelogData get() = dataRef.get()

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

    fun load(remoteUrl: String, forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        synchronized(lock) {
            val running = inFlight?.takeIf { !it.isDone }
            if (running != null) {
                if (!forceRefresh || inFlightIsForced) return running
                return running.handle { _, _ -> null }.thenCompose { load(remoteUrl, true) }
            }

            inFlightIsForced = forceRefresh
            return CompletableFuture.supplyAsync { doLoad(remoteUrl, forceRefresh) }.also { inFlight = it }
        }
    }

    fun ensureLoaded(remoteUrl: String): CompletableFuture<Boolean> {
        synchronized(lock) {
            val previous = inFlight
            if (previous != null && (!previous.isDone || !isError)) return previous
        }
        return load(remoteUrl)
    }

    private fun doLoad(remoteUrl: String, forceRefresh: Boolean): Boolean {
        errorMessage = ""
        val loaded = loadFromRemote(remoteUrl, forceRefresh) || tryLoadCache() || loadFromResources()
        if (!loaded) {
            dataRef.set(ChangelogData.EMPTY)
            if (errorMessage.isBlank()) errorMessage = "No changelog source available"
            Changelog.LOGGER.error("Failed to load changelog from any source: {}", errorMessage)
        }
        isError = !loaded
        isLoaded = true
        return loaded
    }

    private fun loadFromRemote(urlStr: String, forceRefresh: Boolean): Boolean {
        if (urlStr.isBlank()) return false

        var connection: HttpURLConnection? = null
        return try {
            connection = (URI.create(urlStr).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                if (!forceRefresh && Files.exists(cacheFile)) {
                    readEtag()?.let { setRequestProperty("If-None-Match", it) }
                }
            }

            when (val code = connection.responseCode) {
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

            val parsed = connection.inputStream.use { parseJson(it.readAllBytes()) } ?: return false
            dataRef.set(parsed)
            saveCache(parsed, connection.getHeaderField("ETag"))
            Changelog.LOGGER.info("Changelog loaded from remote, {} entries", parsed.entries.size)
            true
        } catch (exception: Exception) {
            errorMessage = exception.message ?: exception.javaClass.simpleName
            Changelog.LOGGER.warn("Failed to fetch remote changelog, falling back to cache", exception)
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun tryLoadCache(): Boolean = try {
        if (Files.exists(cacheFile)) {
            parseJson(Files.readAllBytes(cacheFile))?.let {
                dataRef.set(it)
                Changelog.LOGGER.info("Changelog loaded from disk cache, {} entries", it.entries.size)
                true
            } ?: false
        } else {
            false
        }
    } catch (exception: Exception) {
        Changelog.LOGGER.warn("Failed to read changelog cache", exception)
        false
    }

    private fun loadFromResources(): Boolean = try {
        ChangelogLoader::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.use { stream ->
            parseJson(stream.readAllBytes())?.let {
                dataRef.set(it)
                Changelog.LOGGER.info("Changelog loaded from bundled resources, {} entries", it.entries.size)
                true
            }
        } ?: false
    } catch (exception: Exception) {
        Changelog.LOGGER.error("Failed to load bundled changelog", exception)
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

            val etag = etagHeader?.trim().orEmpty()
            cachedEtag = etag
            if (etag.isNotBlank()) Files.writeString(etagFile, etag, StandardCharsets.UTF_8)
            else Files.deleteIfExists(etagFile)
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to write changelog cache", exception)
        }
    }

    private fun parseJson(bytes: ByteArray): ChangelogData? = try {
        gson.fromJson(String(bytes, StandardCharsets.UTF_8), ChangelogData::class.java)
    } catch (_: JsonParseException) {
        errorMessage = "Invalid changelog JSON"
        null
    }
}
