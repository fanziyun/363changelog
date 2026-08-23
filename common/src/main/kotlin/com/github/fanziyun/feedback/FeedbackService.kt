package com.github.fanziyun.feedback

import com.github.fanziyun.Changelog
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** 一次反馈提交的最终结果，供界面回显，不做任何主线程阻塞。 */
data class FeedbackResult(val success: Boolean, val message: String)

/**
 * 反馈提交服务：在后台线程把反馈 POST 到 GitHub issues API。
 *
 * 面向 GitHub/GitHub Enterprise 兼容 API，授权 token 可来自 [GitHubOAuth] 设备流或 PAT。
 * 与 [com.github.fanziyun.data.ChangelogLoader]
 * 一样使用 JDK HttpURLConnection + 单线程 daemon 执行器，绝不阻塞渲染线程。
 */
object FeedbackService {

    private const val USER_AGENT = "363Changelog"

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Feedback").apply { isDaemon = true }
    }

    fun submit(
        endpoint: FeedbackEndpoint,
        title: String,
        body: String,
        token: String,
    ): CompletableFuture<FeedbackResult> = CompletableFuture.supplyAsync({
        if (token.isBlank()) return@supplyAsync FeedbackResult(false, "尚未登录 GitHub")

        val url = try { endpoint.issueUrl() } catch (exception: IllegalArgumentException) {
            return@supplyAsync FeedbackResult(false, exception.message ?: "反馈服务配置无效")
        }
        val headers = requestHeaders(token)
        postJson(url, headers, jsonOf("title" to title, "body" to body))
    }, executor)

    internal fun requestHeaders(token: String): Map<String, String> = mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github+json",
            "Content-Type" to "application/json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )

    private fun jsonOf(vararg pairs: Pair<String, String>): String {
        val obj = JsonObject()
        pairs.forEach { (key, value) -> obj.addProperty(key, value) }
        return gson.toJson(obj)
    }

    private fun postJson(url: String, headers: Map<String, String>, body: String): FeedbackResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                // 0 means no timeout. Azure Container Apps may need an unbounded cold-start wait.
                connectTimeout = 0
                readTimeout = 0
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
                val reason = if (response.isNotBlank()) " - ${response.take(160)}" else ""
                Changelog.LOGGER.warn("Feedback request failed with HTTP {}: {}", code, response.take(200))
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
