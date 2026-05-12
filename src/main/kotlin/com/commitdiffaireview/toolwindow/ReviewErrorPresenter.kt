package com.commitdiffaireview.toolwindow

import java.io.IOException
import java.net.SocketTimeoutException

class ReviewErrorPresenter {
    fun present(error: Throwable): ReviewUiState.Failed {
        val detail = error.message ?: "AI Review 失败。"
        return ReviewUiState.Failed(
            title = "AI Review 请求失败",
            detail = detail,
            nextStep = nextStepFor(error, detail)
        )
    }

    private fun nextStepFor(error: Throwable, detail: String): String = when {
        detail.contains("HTTP 401") || detail.contains("HTTP 403") ->
            "请检查 API Key 是否正确，或确认当前账号有权限访问该模型。"
        detail.contains("HTTP 404") ->
            "请检查 Base URL、API endpoint 或模型名称是否正确。"
        detail.contains("HTTP 429") ->
            "请求过于频繁，请稍后重试，或换用限额更充足的模型。"
        error is SocketTimeoutException || detail.contains("timeout", ignoreCase = true) ->
            "请求超时。可以调大 Read/Call Timeout，或换用响应更快的模型。"
        error is IOException ->
            "请检查网络、代理设置或 Base URL 后重试。"
        else ->
            "请检查网络、Base URL、模型名称和 API Key 后重试。"
    }
}
