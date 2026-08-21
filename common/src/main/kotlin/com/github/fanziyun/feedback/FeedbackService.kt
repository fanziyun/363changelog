package com.github.fanziyun.feedback

import com.github.fanziyun.Changelog
import com.github.fanziyun.ChangelogService
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** 一次反馈提交的最终结果，供界面回显，不做任何主线程阻塞。 */
data class FeedbackResult(val success: Boolean, val message: String)

/**
 * 反馈提交服务：在后台线程把反馈 POST 到可配置的 GitHub / Gitee issue API。
 *
 * 与 [com.github.fanziyun.data.ChangelogLoader] 一样使用 JDK HttpURLConnection + 单线程 daemon 执行器，
 * 绝不阻塞渲染线程。后端由配置的 feedbackUrl 决定：
 *  - 含 `api.github.com`    -> GitHub (Bearer token)
 *  - 含 `gitee.com/api/v5`  -> Gitee（国内可直连，作为"国内加速"方案，access_token）
 *  - 其它                    -> 不支持，返回失败
 */
object FeedbackService {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"
    private const val TITLE_MAX = 30

    private enum class Provider { GITHUB, GITEE }

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Feedback").apply { isDaemon = true }
    }

    fun submit(
        packName: String,
        playerName: String,
        contact: String,
        version: String,
        text: String,
    ): CompletableFuture<FeedbackResult> = CompletableFuture.supplyAsync({
        val cfg = ChangelogService.config
        val url = cfg?.feedbackUrl?.trim().orEmpty()
        val token = cfg?.feedbackToken?.trim().orEmpty()

        if (url.isBlank()) return@supplyAsync FeedbackResult(false, "反馈后端未配置 (feedbackUrl)")
        val provider = providerFor(url)
            ?: return@supplyAsync FeedbackResult(false, "不支持的后端: $url")
        if (token.isBlank()) return@supplyAsync FeedbackResult(false, "反馈 Token 未配置 (feedbackToken)")

        val title = buildTitle(packName, playerName, text)
        val body = buildBody(playerName, version, contact, text)

        when (provider) {
            Provider.GITHUB -> {
                val headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json",
                )
                httpPost(url, headers, jsonOf("title" to title, "body" to body))
            }

            Provider.GITEE -> {
                val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8")
                val form = linkedMapOf(
                    "access_token" to token,
                    "title" to title,
                    "body" to body,
                )
                httpPost(giteeRepoUrl(url), headers, urlEncoded(form))
            }
        }
    }, executor)

    private fun providerFor(url: String): Provider? = when {
        url.contains("api.github.com") -> Provider.GITHUB
        url.contains("gitee.com/api/v5") -> Provider.GITEE
        else -> null
    }

    /** [https://gitee.com/api/v5/repos/<owner>/<repo>/issues] 解析为稳定的创建 issue 端点。 */
    private fun giteeRepoUrl(url: String): String {
        val marker = "/repos/"
        val idx = url.indexOf(marker)
        if (idx < 0) return url
        val rest = url.substring(idx + marker.length).trim('/')
        val segments = rest.split('/').filter(String::isNotBlank)
        return if (segments.size >= 2) {
            "https://gitee.com/api/v5/repos/${segments[0]}/${segments[1]}/issues"
        } else {
            url
        }
    }

    private fun buildTitle(packName: String, playerName: String, text: String): String {
        val preview = text.trim().replace(Regex("\\s+"), " ")
        val truncated = if (preview.length <= TITLE_MAX) preview else preview.take(TITLE_MAX) + "…"
        return if (packName.isBlank()) {
            "$playerName: $truncated"
        } else {
            "[$packName] $playerName: $truncated"
        }
    }

    private fun buildBody(playerName: String, version: String, contact: String, text: String): String = buildString {
        append(text.trim())
        append("\n\n---")
        append("\nFrom: ").append(playerName)
        if (version.isNotBlank()) append("\nVersion: ").append(version)
        if (contact.isNotBlank()) append("\nContact: ").append(contact)
    }

    private fun jsonOf(vararg pairs: Pair<String, String>): String {
        val obj = JsonObject()
        pairs.forEach { (key, value) -> obj.addProperty(key, value) }
        return gson.toJson(obj)
    }

    private fun urlEncoded(params: Map<String, String>): String =
        params.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }

    private fun httpPost(url: String, headers: Map<String, String>, body: String): FeedbackResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }.orEmpty()

            if (code in 200..299) {
                Changelog.LOGGER.info("Feedback submitted (HTTP {}): {}", code, response.take(160))
                FeedbackResult(true, response.take(120).ifBlank { "OK" })
            } else {
                val reason = if (response.isNotBlank()) " - ${response.take(120)}" else ""
                Changelog.LOGGER.warn("Feedback request failed with HTTP {}: {}", code, response.take(160))
                FeedbackResult(false, "HTTP $code$reason")
            }
        } catch (exception: Exception) {
            val message = exception.message?.takeIf(String::isNotBlank) ?: exception.javaClass.simpleName
            Changelog.LOGGER.warn("Feedback request failed", exception)
            FeedbackResult(false, message)
        } finally {
            connection?.disconnect()
        }
    }
}
