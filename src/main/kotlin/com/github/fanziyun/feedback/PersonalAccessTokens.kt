package com.github.fanziyun.feedback

import com.github.fanziyun.Changelog
import com.github.fanziyun.platform.Platform
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object PersonalAccessTokens {
    private val gson = Gson()
    private val path: Path by lazy {
        Platform.INSTANCE.gameDir.resolve(".cache").resolve(Changelog.MOD_ID).resolve("personal_access_tokens.json")
    }

    @Synchronized
    fun load(storageKey: String): String? = read()[storageKey]?.takeIf(String::isNotBlank)

    @Synchronized
    fun save(storageKey: String, token: String) {
        val values = read().toMutableMap()
        values[storageKey] = token
        write(values)
    }

    @Synchronized
    fun remove(storageKey: String) {
        val values = read().toMutableMap()
        values.remove(storageKey)
        write(values)
    }

    private fun read(): Map<String, String> = try {
        if (!Files.isRegularFile(path)) emptyMap()
        else gson.fromJson<Map<String, String>>(
            Files.readString(path, StandardCharsets.UTF_8),
            object : TypeToken<Map<String, String>>() {}.type,
        ) ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private fun write(values: Map<String, String>) {
        try {
            Files.createDirectories(path.parent)
            val temp = Files.createTempFile(path.parent, "personal_access_tokens", ".tmp")
            Files.writeString(temp, gson.toJson(values), StandardCharsets.UTF_8)
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to persist personal access token", exception)
        }
    }
}
