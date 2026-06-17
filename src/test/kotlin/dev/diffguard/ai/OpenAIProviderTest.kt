package dev.diffguard.ai

import dev.diffguard.model.AISettingsState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

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

    @Test
    fun `truncates long unsuccessful response body in error message`() {
        val provider = OpenAIProvider(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = clientReturning(
                code = 500,
                contentType = "application/json",
                body = "x".repeat(3_000)
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            provider.review("review this diff")
        }

        assertTrue(error.message.orEmpty().contains("HTTP 500"))
        assertTrue(error.message.orEmpty().contains("...[truncated]"))
        assertTrue(error.message.orEmpty().length < 2_100)
    }

    @Test
    fun `uses structured api error message for unsuccessful response`() {
        val provider = OpenAIProvider(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = clientReturning(
                code = 429,
                contentType = "application/json",
                body = """{"error":{"message":"quota exceeded","type":"rate_limit_error"}}"""
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            provider.review("review this diff")
        }

        assertEquals("DiffGuard 请求失败：HTTP 429 rate_limit_error: quota exceeded", error.message)
    }

    @Test
    fun `throws clear error when successful response has no choices`() {
        val provider = OpenAIProvider(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = clientReturning(
                code = 200,
                contentType = "application/json",
                body = """{"choices":[]}"""
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            provider.review("review this diff")
        }

        assertEquals("AI 服务返回成功响应，但没有 choices 内容。", error.message)
    }

    @Test
    fun `throws clear error when response is truncated by model`() {
        val provider = OpenAIProvider(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = clientReturning(
                code = 200,
                contentType = "application/json",
                body = """{"choices":[{"message":{"role":"assistant","content":"[]"},"finish_reason":"length"}]}"""
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            provider.review("review this diff")
        }

        assertEquals("AI 返回因长度限制被截断，请缩小 diff 或换用上下文更大的模型。", error.message)
    }

    @Test
    fun `does not send request when cancellation token is already cancelled`() {
        var requestSent = false
        val provider = OpenAIProvider(
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            model = "test-model",
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestSent = true
                    chain.proceed(chain.request())
                }
                .build()
        )
        val token = ReviewCancellationToken().apply { cancel() }

        assertThrows(CancellationException::class.java) {
            provider.review("review this diff", token)
        }

        assertEquals(false, requestSent)
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
