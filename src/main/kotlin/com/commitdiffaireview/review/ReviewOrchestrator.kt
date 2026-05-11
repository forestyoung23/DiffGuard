package com.commitdiffaireview.review

import com.commitdiffaireview.ai.AIProvider
import com.commitdiffaireview.ai.OpenAIProvider
import com.commitdiffaireview.git.GitStagedDiffProvider
import com.commitdiffaireview.model.AISettingsState
import com.commitdiffaireview.model.ReviewFinding
import com.commitdiffaireview.settings.AIReviewSettingsService
import com.intellij.openapi.project.Project

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

    fun reviewStagedDiff(): List<ReviewFinding> {
        val diff = diffProvider()
        if (diff.isBlank()) {
            return listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "Git",
                    line = null,
                    message = "没有可 Review 的变更。工作区与 HEAD 完全一致，无需 Review。"
                )
            )
        }

        val settings = settingsProvider()
        if (settings.apiKey.isBlank()) {
            return listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "Settings",
                    line = null,
                    message = "请先在 Settings / Tools / CommitDiffAIReview 中配置 API Key。"
                )
            )
        }

        val provider = providerFactory(settings)
        onStatus("正在请求 AI Review，非流式模型可能需要等待数分钟...")
        val rawResponse = provider.review(promptBuilder.build(diff))
        return parser.parse(rawResponse)
    }
}
