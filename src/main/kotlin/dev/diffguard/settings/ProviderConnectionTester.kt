package dev.diffguard.settings

import dev.diffguard.ai.AIProvider
import dev.diffguard.ai.OpenAIProvider
import dev.diffguard.ai.ReviewCancellationToken
import dev.diffguard.model.AISettingsState

sealed interface ConnectionTestResult {
    data class Succeeded(val message: String) : ConnectionTestResult
    data class Failed(val message: String) : ConnectionTestResult
}

class ProviderConnectionTester(
    private val providerFactory: (AISettingsState) -> AIProvider = { settings ->
        OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            client = OpenAIProvider.clientFor(settings)
        )
    }
) {
    fun test(settings: AISettingsState, cancellationToken: ReviewCancellationToken = ReviewCancellationToken()): ConnectionTestResult {
        if (settings.apiKey.isBlank()) {
            return ConnectionTestResult.Failed("请先填写 API Key。")
        }
        if (settings.baseUrl.isBlank()) {
            return ConnectionTestResult.Failed("请先填写 Base URL。")
        }
        if (settings.model.isBlank()) {
            return ConnectionTestResult.Failed("请先填写 Model。")
        }

        val testSettings = settings.copy(
            connectTimeoutSeconds = CONNECTION_TEST_CONNECT_TIMEOUT_SECONDS,
            writeTimeoutSeconds = CONNECTION_TEST_WRITE_TIMEOUT_SECONDS,
            readTimeoutSeconds = CONNECTION_TEST_READ_TIMEOUT_SECONDS,
            callTimeoutSeconds = CONNECTION_TEST_CALL_TIMEOUT_SECONDS
        )
        return runCatching {
            providerFactory(testSettings).review(CONNECTION_TEST_PROMPT, cancellationToken)
        }.fold(
            onSuccess = { ConnectionTestResult.Succeeded("连接成功，Provider 已返回响应。") },
            onFailure = { ConnectionTestResult.Failed("连接失败：${it.message ?: it::class.java.simpleName}") }
        )
    }

    private companion object {
        const val CONNECTION_TEST_PROMPT = "Return only an empty JSON array: []"
        const val CONNECTION_TEST_CONNECT_TIMEOUT_SECONDS = 5
        const val CONNECTION_TEST_WRITE_TIMEOUT_SECONDS = 5
        const val CONNECTION_TEST_READ_TIMEOUT_SECONDS = 10
        const val CONNECTION_TEST_CALL_TIMEOUT_SECONDS = 15
    }
}
