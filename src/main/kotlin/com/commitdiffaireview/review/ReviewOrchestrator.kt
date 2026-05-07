package com.commitdiffaireview.review

import com.commitdiffaireview.ai.OpenAIProvider
import com.commitdiffaireview.git.GitStagedDiffProvider
import com.commitdiffaireview.model.ReviewFinding
import com.commitdiffaireview.settings.AIReviewSettingsService
import com.intellij.openapi.project.Project
import okhttp3.OkHttpClient

class ReviewOrchestrator(
    private val project: Project,
    private val diffProvider: GitStagedDiffProvider = GitStagedDiffProvider(project),
    private val promptBuilder: ReviewPromptBuilder = ReviewPromptBuilder(),
    private val parser: ReviewResultParser = ReviewResultParser(),
    private val client: OkHttpClient = OkHttpClient()
) {
    fun reviewStagedDiff(): List<ReviewFinding> {
        val diff = diffProvider.getStagedDiff()
        if (diff.isBlank()) {
            return listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "Git",
                    line = null,
                    message = "No staged changes to review."
                )
            )
        }

        val settings = AIReviewSettingsService.getInstance().state
        if (settings.apiKey.isBlank()) {
            return listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "Settings",
                    line = null,
                    message = "Please configure API Key in Settings / Tools / CommitDiffAIReview."
                )
            )
        }

        val provider = OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            client = client
        )
        val rawResponse = provider.review(promptBuilder.build(diff))
        return parser.parse(rawResponse)
    }
}
