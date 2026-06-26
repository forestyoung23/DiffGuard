package dev.diffguard.prompt

import dev.diffguard.review.PromptBudget
import dev.diffguard.workspace.WorkspaceContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptContextBuilderTest {
    @Test
    fun `prompt includes workspace sections before code context and git diff`() {
        val prompt = PromptContextBuilder(PromptBudget(maxDiffChars = 1_000, maxContextChars = 1_000))
            .build(
                stagedDiff = "diff --git a/Foo.java b/Foo.java",
                codeContexts = emptyList(),
                workspaceContext = WorkspaceContext(
                    rules = "- Redis Key 必须设置 TTL",
                    architecture = "DDD architecture",
                    reviewFocus = "- Transaction\n- Cache consistency",
                    ignorePatterns = emptyList()
                )
            )

        assertTrue(prompt.contains("# Project Rules\n\n- Redis Key 必须设置 TTL"), prompt)
        assertTrue(prompt.contains("# Architecture\n\nDDD architecture"), prompt)
        assertTrue(prompt.contains("# Review Focus\n\n- Transaction\n- Cache consistency"), prompt)
        assertTrue(prompt.contains("# Git Diff"), prompt)
        assertTrue(prompt.indexOf("# Project Rules") < prompt.indexOf("# Architecture"), prompt)
        assertTrue(prompt.indexOf("# Architecture") < prompt.indexOf("# Review Focus"), prompt)
        assertTrue(prompt.indexOf("# Review Focus") < prompt.indexOf("# Git Diff"), prompt)
    }

    @Test
    fun `prompt omits missing workspace sections`() {
        val prompt = PromptContextBuilder()
            .build(
                stagedDiff = "diff",
                codeContexts = emptyList(),
                workspaceContext = WorkspaceContext(
                    rules = "- Feign 必须配置 timeout",
                    architecture = null,
                    reviewFocus = null,
                    ignorePatterns = emptyList()
                )
            )

        assertTrue(prompt.contains("# Project Rules"), prompt)
        assertFalse(prompt.contains("# Architecture"), prompt)
        assertTrue(prompt.contains("# Git Diff"), prompt)
    }

    @Test
    fun `prompt sets quality bar for concrete actionable findings`() {
        val prompt = PromptContextBuilder()
            .build(
                stagedDiff = "diff --git a/Foo.java b/Foo.java",
                codeContexts = emptyList(),
                workspaceContext = null
            )

        assertTrue(prompt.contains("Prioritize correctness, security, concurrency, transaction, data consistency, and boundary-condition risks."), prompt)
        assertTrue(prompt.contains("Do not report generic advice, speculative risks, duplicate findings, or pure style preferences."), prompt)
        assertTrue(prompt.contains("Only report readability issues when they can hide a real behavior or maintenance risk."), prompt)
        assertTrue(prompt.contains("Each message must describe the problem, the impact, and a concrete suggested fix in Chinese."), prompt)
        assertTrue(prompt.contains("Point file and line to the most relevant added or modified line."), prompt)
        assertTrue(prompt.contains("Workspace rules can explain project-specific risk, but every finding must still be supported by this diff or code context."), prompt)
    }

    @Test
    fun `prompt keeps existing finding schema without metadata requirements`() {
        val prompt = PromptContextBuilder().build(stagedDiff = "diff", codeContexts = emptyList())

        assertTrue(prompt.contains("\"level\""), prompt)
        assertTrue(prompt.contains("\"file\""), prompt)
        assertTrue(prompt.contains("\"line\""), prompt)
        assertTrue(prompt.contains("\"message\""), prompt)
        assertFalse(prompt.contains("\"category\""), prompt)
        assertFalse(prompt.contains("\"confidence\""), prompt)
        assertFalse(prompt.contains("\"evidence\""), prompt)
    }
}
