package dev.diffguard.settings

import dev.diffguard.ai.AIProvider
import dev.diffguard.ai.ReviewCancellationToken
import dev.diffguard.model.AISettingsState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderConnectionTesterTest {
    @Test
    fun `returns configuration error when api key is blank`() {
        val result = ProviderConnectionTester().test(AISettingsState(apiKey = ""))

        assertEquals(ConnectionTestResult.Failed("请先填写 API Key。"), result)
    }

    @Test
    fun `returns success when provider responds`() {
        val result = ProviderConnectionTester(providerFactory = { EchoProvider("[]") })
            .test(AISettingsState(apiKey = "sk-test"))

        assertEquals(ConnectionTestResult.Succeeded("连接成功，Provider 已返回响应。"), result)
    }

    @Test
    fun `returns provider error message when request fails`() {
        val result = ProviderConnectionTester(providerFactory = { FailingProvider("model not found") })
            .test(AISettingsState(apiKey = "sk-test"))

        assertEquals(ConnectionTestResult.Failed("连接失败：model not found"), result)
    }

    @Test
    fun `uses short timeout settings for connection test provider`() {
        var receivedSettings: AISettingsState? = null
        val result = ProviderConnectionTester(providerFactory = { settings ->
            receivedSettings = settings
            EchoProvider("[]")
        }).test(
            AISettingsState(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                model = "test-model",
                connectTimeoutSeconds = 30,
                writeTimeoutSeconds = 60,
                readTimeoutSeconds = 300,
                callTimeoutSeconds = 360
            )
        )

        assertEquals(ConnectionTestResult.Succeeded("连接成功，Provider 已返回响应。"), result)
        assertEquals(5, receivedSettings?.connectTimeoutSeconds)
        assertEquals(5, receivedSettings?.writeTimeoutSeconds)
        assertEquals(10, receivedSettings?.readTimeoutSeconds)
        assertEquals(15, receivedSettings?.callTimeoutSeconds)
    }

    private class EchoProvider(private val response: String) : AIProvider {
        override fun review(prompt: String): String = response
        override fun review(prompt: String, cancellationToken: ReviewCancellationToken): String = response
    }

    private class FailingProvider(private val message: String) : AIProvider {
        override fun review(prompt: String): String = error(message)
        override fun review(prompt: String, cancellationToken: ReviewCancellationToken): String = error(message)
    }
}
