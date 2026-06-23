package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding

sealed interface ReviewUiState {
    data object Ready : ReviewUiState

    data class NeedsConfiguration(
        val title: String = "需要先配置 API Key",
        val detail: String = "DiffGuard 需要调用 OpenAI-compatible API。",
        val nextStep: String = "打开 Settings / Tools / DiffGuard，填写 API Key、Base URL 和 Model。"
    ) : ReviewUiState

    data class NoChanges(
        val title: String = "没有可 Review 的变更",
        val detail: String = "当前 Review 范围为空，因此无需调用 AI。",
        val nextStep: String = "请先勾选需要审查的文件，或确认当前 Review 范围是否符合预期。"
    ) : ReviewUiState

    data class Reviewing(
        val message: String
    ) : ReviewUiState

    data class Completed(
        val findings: List<ReviewFinding>
    ) : ReviewUiState

    data class Failed(
        val title: String,
        val detail: String,
        val nextStep: String
    ) : ReviewUiState

    data class ParseFallback(
        val title: String = "AI 返回内容无法解析",
        val detail: String = "模型没有返回预期的 JSON 结构化结果。",
        val rawResponsePreview: String,
        val nextStep: String = "可以重试，或调整模型后再试。"
    ) : ReviewUiState
}
