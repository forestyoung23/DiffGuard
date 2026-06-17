package dev.diffguard.ai

import dev.diffguard.model.AISettingsState
import dev.diffguard.model.OpenAIChatRequest
import dev.diffguard.model.OpenAIChatResponse
import dev.diffguard.model.OpenAIMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAIProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient()
) : AIProvider {
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val jsonMediaType = JSON_MEDIA_TYPE.toMediaType()

    override fun review(prompt: String): String {
        val requestBody = OpenAIChatRequest(
            model = model,
            messages = listOf(OpenAIMessage(role = "user", content = prompt))
        )

        val request = Request.Builder()
            .url(chatCompletionsUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", JSON_MEDIA_TYPE)
            .post(json.encodeToString(requestBody).toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (looksLikeHtml(responseBody)) {
                error("AI 服务返回了 HTML 页面，请检查 Base URL 是否为 OpenAI Compatible API 地址（通常以 /v1 结尾），不要填写 new-api 管理后台页面地址。")
            }
            if (!response.isSuccessful) {
                error("DiffGuard 请求失败：HTTP ${response.code} ${responseBodyPreview(responseBody)}")
            }

            val chatResponse = json.decodeFromString<OpenAIChatResponse>(responseBody)
            return chatResponse.choices.firstOrNull()?.message?.content.orEmpty()
        }
    }

    private fun looksLikeHtml(responseBody: String): Boolean =
        responseBody.trimStart().startsWith("<")

    private fun chatCompletionsUrl(): String =
        baseUrl.trimEnd('/') + "/chat/completions"

    private fun responseBodyPreview(responseBody: String): String =
        if (responseBody.length <= MAX_ERROR_BODY_CHARS) {
            responseBody
        } else {
            responseBody.take(MAX_ERROR_BODY_CHARS) + "...[truncated]"
        }

    companion object {
        private const val JSON_MEDIA_TYPE = "application/json"
        private const val MAX_ERROR_BODY_CHARS = 2_000

        fun defaultClient(): OkHttpClient = clientFor(AISettingsState())

        fun clientFor(settings: AISettingsState): OkHttpClient = clientFor(
            connectTimeoutSeconds = settings.connectTimeoutSeconds,
            writeTimeoutSeconds = settings.writeTimeoutSeconds,
            readTimeoutSeconds = settings.readTimeoutSeconds,
            callTimeoutSeconds = settings.callTimeoutSeconds
        )

        fun clientFor(
            connectTimeoutSeconds: Int,
            writeTimeoutSeconds: Int,
            readTimeoutSeconds: Int,
            callTimeoutSeconds: Int
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutOrDefault(connectTimeoutSeconds, 30), TimeUnit.SECONDS)
            .writeTimeout(timeoutOrDefault(writeTimeoutSeconds, 60), TimeUnit.SECONDS)
            .readTimeout(timeoutOrDefault(readTimeoutSeconds, 300), TimeUnit.SECONDS)
            .callTimeout(timeoutOrDefault(callTimeoutSeconds, 360), TimeUnit.SECONDS)
            .build()

        private fun timeoutOrDefault(value: Int, defaultValue: Int): Long =
            (if (value > 0) value else defaultValue).toLong()
    }
}
