package com.github.fanziyun.data

import com.github.fanziyun.Changelog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

//更新日志加载器
object ChangelogLoader {

    private val gson: Gson = GsonBuilder().create()
    private val cacheDir: Path = FabricLoader.getInstance().gameDir.resolve(".cache")

    @Volatile
    var isLoaded: Boolean = false
        private set

    @Volatile
    var isError: Boolean = false
        private set

    @Volatile
    var errorMessage: String = ""
        private set

    private val _data = AtomicReference(createDefaultData())
    val data: ChangelogData get() = _data.get()

    val latestVersionFromData: String
        get() = data.entriesOrEmpty.firstOrNull()?.versionOrEmpty ?: ""

    private val loading = AtomicBoolean(false)
    private var cachedEtag: String = loadCachedEtag()

    fun load(remoteUrl: String, forceRefresh: Boolean = false): CompletableFuture<Boolean> {
        if (!loading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(true)
        }
        return CompletableFuture.supplyAsync {
            try {
                val success = if (remoteUrl.isNotBlank() && !forceRefresh) {
                    loadFromRemote(remoteUrl)
                } else if (remoteUrl.isNotBlank() && forceRefresh) {
                    loadFromRemote(remoteUrl, forceRefresh = true)
                } else {
                    false
                }

                if (!success) {
                    if (!tryLoadCache()) {
                        loadFromResources()
                    }
                }

                isLoaded = true
                isError = false
                true
            } catch (e: Exception) {
                isError = true
                errorMessage = e.message ?: "Unknown error"
                Changelog.LOGGER.error("Failed to load changelog", e)
                // 最后尝试缓存
                if (!tryLoadCache()) {
                    _data.set(createDefaultData())
                }
                isLoaded = true
                false
            } finally {
                loading.set(false)
            }
        }
    }

    private fun loadFromRemote(urlStr: String, forceRefresh: Boolean = false): Boolean {
        val url = URI.create(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "363Changelog/1.0")

            if (!forceRefresh && cachedEtag.isNotBlank()) {
                conn.setRequestProperty("If-None-Match", cachedEtag)
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Changelog.LOGGER.info("Changelog cache valid (304)")
                return tryLoadCache()
            }

            if (responseCode != HttpURLConnection.HTTP_OK) return false

            conn.headerFields["ETag"]?.firstOrNull()?.trim()?.let { etag ->
                cachedEtag = etag
            }

            val data = conn.inputStream.use { stream ->
                parseJson(stream.readAllBytes())
            } ?: return false

            _data.set(data)

            saveCache(data)

            Changelog.LOGGER.info(" Changelog loaded from remote, ${data.entriesOrEmpty.size} entries")
            true
        } finally {
            conn.disconnect()
        }
    }

    //JAR 资源文件加载
    private fun loadFromResources(): Boolean {
        val stream = ChangelogLoader::class.java.getResourceAsStream("/changelog.json")
            ?: return false

        return try {
            val data = parseJson(stream.readAllBytes()) ?: return false
            _data.set(data)
            Changelog.LOGGER.info(" Changelog loaded from resources, ${data.entriesOrEmpty.size} entries")
            true
        } catch (e: Exception) {
            Changelog.LOGGER.error("Failed to load changelog from resources", e)
            false
        }
    }

    private fun tryLoadCache(): Boolean {
        return try {
            val cacheFile = cacheDir.resolve("changelog_cache.json")
            if (Files.exists(cacheFile)) {
                val data = parseJson(Files.readAllBytes(cacheFile)) ?: return false
                _data.set(data)
                loadCachedEtag().takeIf { it.isNotBlank() }?.let { cachedEtag = it }
                Changelog.LOGGER.info("Changelog loaded from cache")
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun saveCache(data: ChangelogData) {
        try {
            Files.createDirectories(cacheDir)
            val cacheFile = cacheDir.resolve("changelog_cache.json")
            Files.write(cacheFile, gson.toJson(data).toByteArray(StandardCharsets.UTF_8))

            if (cachedEtag.isNotBlank()) {
                Files.writeString(cacheDir.resolve("changelog_cache.etag"), cachedEtag)
            }
        } catch (e: Exception) {
            Changelog.LOGGER.warn("Failed to write changelog cache", e)
        }
    }

    private fun loadCachedEtag(): String {
        return try {
            val etagFile = cacheDir.resolve("changelog_cache.etag")
            if (Files.exists(etagFile)) {
                Files.readString(etagFile, StandardCharsets.UTF_8).trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseJson(bytes: ByteArray): ChangelogData? {
        return try {
            val jsonStr = String(bytes, StandardCharsets.UTF_8)
            gson.fromJson(jsonStr, ChangelogData::class.java)
        } catch (e: Exception) {
            Changelog.LOGGER.error("Failed to parse changelog JSON", e)
            null
        }
    }

    private fun createDefaultData() = ChangelogData(
        footer = "",
        tagColors = emptyMap(),
        entries = emptyList()
    )
}
