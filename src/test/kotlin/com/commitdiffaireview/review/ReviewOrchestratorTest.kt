package com.commitdiffaireview.review

import com.commitdiffaireview.ai.AIProvider
import com.commitdiffaireview.model.AISettingsState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewOrchestratorTest {
    @Test
    fun `reports waiting status before requesting AI review`() {
        val statuses = mutableListOf<String>()
        val provider = object : AIProvider {
            override fun review(prompt: String): String {
                assertEquals(listOf("正在请求 AI Review，非流式模型可能需要等待数分钟..."), statuses)
                return "[]"
            }
        }
        val orchestrator = ReviewOrchestrator(
            diffProvider = { "diff --git a/App.kt b/App.kt" },
            settingsProvider = { AISettingsState(apiKey = "test-key") },
            providerFactory = { provider },
            onStatus = { status -> statuses.add(status) }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(emptyList<Any>(), result)
    }
}
