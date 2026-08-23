package com.github.fanziyun.feedback

import com.github.fanziyun.Changelog
import com.github.fanziyun.platform.Platform
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

/**
 * GitHub OAuth 设备流（Device Flow）登录 + 令牌持久化。
 *
 * 玩家不需要也不应该填写 Personal Access Token：作者只需提供一个公开的 GitHub OAuth App `client_id`，
 * 玩家在游戏里点登录后，模组拿到 device code 展示给玩家，玩家在浏览器确认后由模组轮询换 token。
 * 获取到的 token 属于玩家本人，用于在作者的**公开**仓库里创建 issue（GitHub 文档：任何对仓库拥有
 * pull 权限的用户都能创建 issue，公开仓库即所有登录用户）。
 *
 * 纯阻塞式 HTTP，全部在后台线程调用，绝不进渲染线程。
 */
object GitHubOAuth {

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val GRANT_TYPE_DEVICE = "urn:ietf:params:oauth:grant-type:device_code"
    private const val DEFAULT_SCOPE = "public_repo"
    private const val POLL_MAX_MS = 15 * 60 * 1000L
    private const val REFRESH_SKEW_MS = 60_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
    )

    data class Token(
        val accessToken: String,
        val refreshToken: String?,
        /** 0 = 永不过期（旧式 token）；否则为到期时间戳 */
        val expiresAt: Long,
    )

    private val gson = Gson()
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-OAuth").apply { isDaemon = true }
    }
    private val tokenPath: Path by lazy {
        Platform.INSTANCE.gameDir.resolve(".cache").resolve(Changelog.MOD_ID).resolve("github_token.json")
    }

    /** 异步请求 device code，交互期间用。 */
    fun requestDeviceCodeAsync(clientId: String, scope: String = DEFAULT_SCOPE): CompletableFuture<DeviceCode> =
        CompletableFuture.supplyAsync({ requestDeviceCode(clientId, scope) }, executor)

    /** 异步轮询换取 token（阻塞到玩家授权成功/失败/超时）。 */
    fun pollForTokenAsync(clientId: String, deviceCode: String, interval: Int): CompletableFuture<Token> =
        CompletableFuture.supplyAsync({ pollForToken(clientId, deviceCode, interval) }, executor)

    /** 请求 device code，供界面展示给玩家。 */
    fun requestDeviceCode(clientId: String, scope: String = DEFAULT_SCOPE): DeviceCode {
        val body = urlEncoded(mapOf("client_id" to clientId, "scope" to scope))
        val response = httpPost(DEVICE_CODE_URL, acceptJson = true, body)
        val json = parseJson(response)
        return DeviceCode(
            deviceCode = json.required("device_code"),
            userCode = json.required("user_code"),
            verificationUri = json.required("verification_uri"),
            interval = json["interval"]?.asInt ?: 5,
        )
    }

    /**
     * 轮询换取 access token。阻塞直到成功、失败或超时；玩家在浏览器确认前会一直循环。
     */
    fun pollForToken(clientId: String, deviceCode: String, interval: Int): Token {
        val deadline = System.currentTimeMillis() + POLL_MAX_MS
        var waitSeconds = interval.coerceAtLeast(5)

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(waitSeconds * 1000L)
            val body = urlEncoded(
                mapOf(
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                    "grant_type" to GRANT_TYPE_DEVICE,
                ),
            )
            val json = parseJson(httpPost(TOKEN_URL, acceptJson = true, body))

            if (json.has("access_token")) return toToken(json)

            when (json["error"]?.asString) {
                "authorization_pending" -> { /* 继续等待玩家授权 */ }
                "slow_down" -> waitSeconds += 5
                else -> throw IllegalStateException(
                    "GitHub 登录失败: ${json["error"]?.asString ?: json["error_description"]?.asString ?: "未知错误"}",
                )
            }
        }
        throw TimeoutException("GitHub 登录已超时，请重试")
    }

    /** 用 refresh token 换新 access token。 */
    fun refresh(clientId: String, refreshToken: String): Token {
        val body = urlEncoded(
            mapOf("client_id" to clientId, "refresh_token" to refreshToken, "grant_type" to "refresh_token"),
        )
        return toToken(parseJson(httpPost(TOKEN_URL, acceptJson = true, body)))
    }

    fun load(): Token? = try {
        if (!Files.isRegularFile(tokenPath)) null
        else gson.fromJson(Files.readString(tokenPath, StandardCharsets.UTF_8), Token::class.java)
    } catch (_: Exception) {
        null
    }

    fun save(token: Token) {
        try {
            Files.createDirectories(tokenPath.parent)
            val temp = Files.createTempFile(tokenPath.parent, "github_token", ".tmp")
            Files.writeString(temp, gson.toJson(token), StandardCharsets.UTF_8)
            try {
                Files.move(temp, tokenPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, tokenPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Changelog.LOGGER.warn("Failed to persist GitHub login token", exception)
        }
    }

    fun clear() {
        try {
            Files.deleteIfExists(tokenPath)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun isUsable(token: Token): Boolean =
        token.accessToken.isNotBlank() && (token.expiresAt == 0L || token.expiresAt > System.currentTimeMillis() + REFRESH_SKEW_MS)

    /** token 已过期，但还带 refresh token（可后台静默刷新，无需重新走设备流）。 */
    fun needsRefresh(token: Token): Boolean =
        !isUsable(token) && !token.refreshToken.isNullOrBlank()

    /** 仅读本地文件判断是否已有一个可用（或可刷新）的会话，绝不触发网络。 */
    fun hasSession(): Boolean = load()?.let { isUsable(it) || needsRefresh(it) } == true

    /** 后台刷新 token（过期且有 refresh token 时用，避免渲染线程阻塞）。 */
    fun refreshAsync(clientId: String, refreshToken: String): CompletableFuture<Token> =
        CompletableFuture.supplyAsync({ refresh(clientId, refreshToken) }, executor)

    private fun toToken(json: JsonObject): Token {
        val access = json.remove("access_token")?.asString
            ?: throw IllegalStateException("GitHub 登录失败：未返回 access_token")
        val refresh = json["refresh_token"]?.asString
        val expiresIn = json["expires_in"]?.asInt ?: 0
        return Token(
            accessToken = access,
            refreshToken = refresh?.takeIf(String::isNotBlank),
            expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000L else 0L,
        )
    }

    private fun JsonObject.required(key: String): String =
        this[key]?.asString?.takeIf(String::isNotBlank) ?: throw IllegalStateException("GitHub 响应缺少字段: $key")

    private fun parseJson(body: String): JsonObject {
        val json = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (exception: Exception) {
            throw IllegalStateException("GitHub 返回了无法解析的响应", exception)
        }
        return json ?: throw IllegalStateException("GitHub 返回了空响应")
    }

    private fun httpPost(url: String, acceptJson: Boolean, body: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                if (acceptJson) setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }.orEmpty()
            if (code in 200..299) {
                response
            } else {
                throw IllegalStateException("GitHub 请求失败 (HTTP $code): ${response.take(120)}")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun urlEncoded(params: Map<String, String>): String =
        params.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
}
