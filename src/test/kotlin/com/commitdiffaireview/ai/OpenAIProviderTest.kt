package com.commitdiffaireview.ai

import com.commitdiffaireview.model.AISettingsState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAIProviderTest {
    @Test
    fun `default settings include AI request timeout values`() {
        val settings = AISettingsState()

        assertEquals(30, settings.connectTimeoutSeconds)
        assertEquals(60, settings.writeTimeoutSeconds)
        assertEquals(300, settings.readTimeoutSeconds)
        assertEquals(360, settings.callTimeoutSeconds)
    }

    @Test
    fun `client uses configured timeout values`() {
        val client = OpenAIProvider.clientFor(
            connectTimeoutSeconds = 11,
            writeTimeoutSeconds = 22,
            readTimeoutSeconds = 33,
            callTimeoutSeconds = 44
        )

        assertEquals(11_000, client.connectTimeoutMillis)
        assertEquals(22_000, client.writeTimeoutMillis)
        assertEquals(33_000, client.readTimeoutMillis)
        assertEquals(44_000, client.callTimeoutMillis)
    }

    @Test
    fun `client uses default timeout when configured value is not positive`() {
        val client = OpenAIProvider.clientFor(
            connectTimeoutSeconds = 0,
            writeTimeoutSeconds = -1,
            readTimeoutSeconds = 0,
            callTimeoutSeconds = -1
        )

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        assertEquals(300_000, client.readTimeoutMillis)
        assertEquals(360_000, client.callTimeoutMillis)
    }

    @Test
    fun `default client waits long enough for slow non-streaming AI responses`() {
        val client = OpenAIProvider.defaultClient()

        assertTrue(client.readTimeoutMillis >= 120_000)
        assertTrue(client.callTimeoutMillis >= 180_000)
    }

    @Test
    fun `throws clear error when successful response is html`() {
        val provider = OpenAIProvider(
            baseUrl = "https://new-api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = clientReturning(
                code = 200,
                contentType = "text/html; charset=utf-8",
                body = "<!doctype html><html lang=\"zh\"><body>login</body></html>"
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            provider.review("review this diff")
        }

        assertEquals(
            "AI 服务返回了 HTML 页面，请检查 Base URL 是否为 OpenAI Compatible API 地址（通常以 /v1 结尾），不要填写 new-api 管理后台页面地址。",
            error.message
        )
    }

    private fun clientReturning(code: Int, contentType: String, body: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
            .build()
}
