package com.commitdiffaireview.review

import com.commitdiffaireview.ai.AIProvider
import com.commitdiffaireview.ai.OpenAIProvider
import com.commitdiffaireview.git.GitStagedDiffProvider
import com.commitdiffaireview.model.AISettingsState
import com.commitdiffaireview.model.ReviewFinding
import com.commitdiffaireview.settings.AIReviewSettingsService
import com.intellij.openapi.project.Project

sealed interface ReviewOutcome {
    data object NoChanges : ReviewOutcome
    data object NeedsConfiguration : ReviewOutcome
    data class Completed(val findings: List<ReviewFinding>) : ReviewOutcome
    data class ParseFallback(val rawResponsePreview: String) : ReviewOutcome
}

fun ReviewOutcome.compatibilityFindings(): List<ReviewFinding> = when (this) {
    ReviewOutcome.NoChanges -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Git",
            line = null,
            message = "没有可 Review 的变更。工作区与 HEAD 完全一致，无需 Review。"
        )
    )
    ReviewOutcome.NeedsConfiguration -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Settings",
            line = null,
            message = "请先在 Settings / Tools / CommitDiffAIReview 中配置 API Key。"
        )
    )
    is ReviewOutcome.Completed -> findings
    is ReviewOutcome.ParseFallback -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "AI Response",
            line = null,
            message = "AI 返回内容不是合法 JSON，已显示原始内容：$rawResponsePreview"
        )
    )
}

class ReviewOrchestrator(
    private val diffProvider: () -> String,
    private val settingsProvider: () -> AISettingsState,
    private val promptBuilder: ReviewPromptBuilder = ReviewPromptBuilder(),
    private val parser: ReviewResultParser = ReviewResultParser(),
    private val providerFactory: (AISettingsState) -> AIProvider = { settings ->
        OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            client = OpenAIProvider.clientFor(settings)
        )
    },
    private val onStatus: (String) -> Unit = {}
) {
    constructor(project: Project, onStatus: (String) -> Unit = {}) : this(
        diffProvider = { GitStagedDiffProvider(project).getStagedDiff() },
        settingsProvider = { AIReviewSettingsService.getInstance().state },
        onStatus = onStatus
    )

    fun reviewStagedDiff(): ReviewOutcome {
        onStatus("正在读取本次变更...")
        val diff = diffProvider()
        if (diff.isBlank()) {
            return ReviewOutcome.NoChanges
        }

        val settings = settingsProvider()
        if (settings.apiKey.isBlank()) {
            return ReviewOutcome.NeedsConfiguration
        }

        onStatus("正在准备 Review 请求...")
        val prompt = promptBuilder.build(diff)
        val provider = providerFactory(settings)
        onStatus("正在请求 AI，非流式模型可能需要等待一段时间...")
        val rawResponse = provider.review(prompt)
        onStatus("正在解析 AI 返回结果...")
        return when (val parseResult = parser.tryParse(rawResponse)) {
            is ReviewParseResult.Parsed -> ReviewOutcome.Completed(parseResult.findings)
            is ReviewParseResult.Fallback -> ReviewOutcome.ParseFallback(parseResult.rawResponsePreview)
        }
    }
}
