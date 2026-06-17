package dev.diffguard.toolwindow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ReviewErrorPresenterTest {
    private val presenter = ReviewErrorPresenter()

    @Test
    fun `maps unauthorized http errors to api key guidance`() {
        val state = presenter.present(IllegalStateException("DiffGuard 请求失败：HTTP 401 unauthorized"))

        assertEquals("DiffGuard 请求失败", state.title)
        assertEquals("请检查 API Key 是否正确，或确认当前账号有权限访问该模型。", state.nextStep)
    }

    @Test
    fun `maps forbidden http errors to api key guidance`() {
        val state = presenter.present(IllegalStateException("DiffGuard 请求失败：HTTP 403 forbidden"))

        assertEquals("DiffGuard 请求失败", state.title)
        assertEquals("请检查 API Key 是否正确，或确认当前账号有权限访问该模型。", state.nextStep)
    }

    @Test
    fun `maps not found http errors to base url or model guidance`() {
        val state = presenter.present(IllegalStateException("DiffGuard 请求失败：HTTP 404 model not found"))

        assertEquals("请检查 Base URL、API endpoint 或模型名称是否正确。", state.nextStep)
    }

    @Test
    fun `maps rate limit http errors to retry guidance`() {
        val state = presenter.present(IllegalStateException("DiffGuard 请求失败：HTTP 429 too many requests"))

        assertEquals("请求过于频繁，请稍后重试，或换用限额更充足的模型。", state.nextStep)
    }

    @Test
    fun `maps timeout exceptions to timeout guidance`() {
        val state = presenter.present(SocketTimeoutException("timeout"))

        assertEquals("请求超时。可以调大 Read/Call Timeout，或换用响应更快的模型。", state.nextStep)
    }

    @Test
    fun `maps timeout details ignoring case to timeout guidance`() {
        val state = presenter.present(IllegalStateException("Request TIMEOUT while reading response"))

        assertEquals("请求超时。可以调大 Read/Call Timeout，或换用响应更快的模型。", state.nextStep)
    }

    @Test
    fun `maps io exceptions to network proxy or base url guidance`() {
        val state = presenter.present(IOException("connection reset"))

        assertEquals("请检查网络、代理设置或 Base URL 后重试。", state.nextStep)
    }

    @Test
    fun `defaults missing error messages to generic failure detail`() {
        val state = presenter.present(IllegalStateException())

        assertEquals("DiffGuard 失败。", state.detail)
    }

    @Test
    fun `maps generic errors to concise fallback guidance`() {
        val state = presenter.present(IllegalStateException("boom"))

        assertEquals("boom", state.detail)
        assertEquals("请检查网络、Base URL、模型名称和 API Key 后重试。", state.nextStep)
    }
}
