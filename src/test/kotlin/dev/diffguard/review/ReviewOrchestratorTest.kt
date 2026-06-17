package dev.diffguard.review

import dev.diffguard.ai.AIProvider
import dev.diffguard.model.AISettingsState
import dev.diffguard.workspace.WorkspaceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewOrchestratorTest {
    @Test
    fun `returns no changes outcome when diff is blank`() {
        val orchestrator = ReviewOrchestrator(
            diffProvider = { "" },
            settingsProvider = { AISettingsState(apiKey = "test-key") }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(ReviewOutcome.NoChanges, result)
    }

    @Test
    fun `returns needs configuration outcome when api key is blank`() {
        val orchestrator = ReviewOrchestrator(
            diffProvider = { "diff --git a/App.kt b/App.kt" },
            settingsProvider = { AISettingsState(apiKey = "") }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(ReviewOutcome.NeedsConfiguration, result)
    }

    @Test
    fun `reports staged progress before and after requesting AI review`() {
        val statuses = mutableListOf<String>()
        val provider = object : AIProvider {
            override fun review(prompt: String): String {
                assertEquals(
                    listOf(
                        "正在读取本次变更...",
                        "正在准备 Review 请求...",
                        "正在请求 AI，非流式模型可能需要等待一段时间..."
                    ),
                    statuses
                )
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

        assertEquals(ReviewOutcome.Completed(emptyList()), result)
        assertEquals(
            listOf(
                "正在读取本次变更...",
                "正在准备 Review 请求...",
                "正在请求 AI，非流式模型可能需要等待一段时间...",
                "正在解析 AI 返回结果..."
            ),
            statuses
        )
    }

    @Test
    fun `returns parse fallback outcome when AI response is not structured json`() {
        val provider = object : AIProvider {
            override fun review(prompt: String): String = "not json"
        }
        val orchestrator = ReviewOrchestrator(
            diffProvider = { "diff --git a/App.kt b/App.kt" },
            settingsProvider = { AISettingsState(apiKey = "test-key") },
            providerFactory = { provider }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(ReviewOutcome.ParseFallback("not json"), result)
    }

    @Test
    fun `includes workspace context in real review prompt`() {
        var capturedPrompt = ""
        val provider = object : AIProvider {
            override fun review(prompt: String): String {
                capturedPrompt = prompt
                return "[]"
            }
        }
        val orchestrator = ReviewOrchestrator(
            diffProvider = { "diff --git a/src/Foo.java b/src/Foo.java\n+++ b/src/Foo.java\n@@ -1 +1 @@\n+class Foo {}" },
            settingsProvider = { AISettingsState(apiKey = "test-key") },
            providerFactory = { provider },
            workspaceProvider = {
                WorkspaceContext(
                    rules = "- Redis Key 必须设置 TTL",
                    architecture = "Hexagonal architecture",
                    reviewFocus = "- Transaction boundary",
                    ignorePatterns = emptyList()
                )
            }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(ReviewOutcome.Completed(emptyList()), result)
        assertTrue(capturedPrompt.contains("# Project Rules\n\n- Redis Key 必须设置 TTL"), capturedPrompt)
        assertTrue(capturedPrompt.contains("# Architecture\n\nHexagonal architecture"), capturedPrompt)
        assertTrue(capturedPrompt.contains("# Review Focus\n\n- Transaction boundary"), capturedPrompt)
    }

    @Test
    fun `filters ignored diff before context and prompt building`() {
        var contextDiff = ""
        var capturedPrompt = ""
        val diff = """
            diff --git a/src/Foo.java b/src/Foo.java
            +++ b/src/Foo.java
            @@ -1 +1 @@
            +class Foo {}
            diff --git a/generated/Generated.java b/generated/Generated.java
            +++ b/generated/Generated.java
            @@ -1 +1 @@
            +class Generated {}
        """.trimIndent()
        val provider = object : AIProvider {
            override fun review(prompt: String): String {
                capturedPrompt = prompt
                return "[]"
            }
        }
        val orchestrator = ReviewOrchestrator(
            diffProvider = { diff },
            settingsProvider = { AISettingsState(apiKey = "test-key") },
            providerFactory = { provider },
            contextBuilder = { filteredDiff ->
                contextDiff = filteredDiff
                emptyList()
            },
            workspaceProvider = {
                WorkspaceContext(
                    rules = null,
                    architecture = null,
                    reviewFocus = null,
                    ignorePatterns = listOf("generated/")
                )
            }
        )

        val result = orchestrator.reviewStagedDiff()

        assertEquals(ReviewOutcome.Completed(emptyList()), result)
        assertTrue(contextDiff.contains("src/Foo.java"), contextDiff)
        assertFalse(contextDiff.contains("generated/Generated.java"), contextDiff)
        assertTrue(capturedPrompt.contains("src/Foo.java"), capturedPrompt)
        assertFalse(capturedPrompt.contains("generated/Generated.java"), capturedPrompt)
    }
}
