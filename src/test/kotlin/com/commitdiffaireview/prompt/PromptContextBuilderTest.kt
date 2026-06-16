package com.commitdiffaireview.prompt

import com.commitdiffaireview.review.PromptBudget
import com.commitdiffaireview.workspace.WorkspaceContext
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
}
