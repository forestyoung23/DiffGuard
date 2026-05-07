package com.commitdiffaireview.ai

import com.commitdiffaireview.model.OpenAIChatRequest
import com.commitdiffaireview.model.OpenAIChatResponse
import com.commitdiffaireview.model.OpenAIMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient()
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
            if (!response.isSuccessful) {
                error("AI review request failed: HTTP ${response.code} $responseBody")
            }

            val chatResponse = json.decodeFromString<OpenAIChatResponse>(responseBody)
            return chatResponse.choices.firstOrNull()?.message?.content.orEmpty()
        }
    }

    private fun chatCompletionsUrl(): String =
        baseUrl.trimEnd('/') + "/chat/completions"

    private companion object {
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
