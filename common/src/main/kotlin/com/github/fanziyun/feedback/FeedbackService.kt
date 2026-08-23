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
 * 只面向 GitHub（`https://api.github.com/repos/<owner>/<repo>/issues`），授权由
 * [GitHubOAuth] 设备流负责，token 属于玩家本人。与 [com.github.fanziyun.data.ChangelogLoader]
 * 一样使用 JDK HttpURLConnection + 单线程 daemon 执行器，绝不阻塞渲染线程。
 */
object FeedbackService {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val USER_AGENT = "363Changelog"

    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "363Changelog-Feedback").apply { isDaemon = true }
    }

    fun submit(
        repo: String,
        title: String,
        body: String,
        token: String,
    ): CompletableFuture<FeedbackResult> = CompletableFuture.supplyAsync({
        val repoTrim = repo.trim().trim('/')
        if (repoTrim.isBlank()) return@supplyAsync FeedbackResult(false, "反馈仓库未配置 (feedbackRepo)")
        if (!repoTrim.contains('/')) return@supplyAsync FeedbackResult(false, "反馈仓库格式应为 owner/repo")
        if (token.isBlank()) return@supplyAsync FeedbackResult(false, "尚未登录 GitHub")

        val url = "https://api.github.com/repos/$repoTrim/issues"
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github+json",
            "Content-Type" to "application/json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
        postJson(url, headers, jsonOf("title" to title, "body" to body))
    }, executor)

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
