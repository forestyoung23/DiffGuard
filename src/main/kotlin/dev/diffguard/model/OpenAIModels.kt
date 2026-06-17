package dev.diffguard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.1
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIChatResponse(
    val choices: List<OpenAIChoice> = emptyList(),
    val error: OpenAIError? = null
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAIError(
    val message: String? = null,
    val type: String? = null
)
