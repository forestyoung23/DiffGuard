package dev.diffguard.prompt

import dev.diffguard.context.CodeContext
import dev.diffguard.context.SpringSemantic
import dev.diffguard.review.PromptBudget
import dev.diffguard.workspace.WorkspaceContext

/**
 * 统一组装 DiffGuard Prompt 的上下文区域。
 *
 * Workspace 文件按纯 Markdown 注入 Prompt，不在插件内解释成规则 DSL。
 */
class PromptContextBuilder(
    private val budget: PromptBudget = PromptBudget()
) {
    fun build(
        stagedDiff: String,
        codeContexts: List<CodeContext> = emptyList(),
        workspaceContext: WorkspaceContext? = null
    ): String = buildString {
        appendLine("You are a senior code reviewer. Review the following staged unified diff.")
        appendLine("Use workspace rules as authoritative project-specific review guidance when present.")
        appendLine("Workspace rules can explain project-specific risk, but every finding must still be supported by this diff or code context.")
        appendLine("Prioritize correctness, security, concurrency, transaction, data consistency, and boundary-condition risks.")
        appendLine("Do not report generic advice, speculative risks, duplicate findings, or pure style preferences.")
        appendLine("Only report readability issues when they can hide a real behavior or maintenance risk.")
        appendLine("If content is truncated, mention only risks that are supported by the visible diff/context.")
        appendLine()

        appendWorkspaceSection("Project Rules", workspaceContext?.rules)
        appendWorkspaceSection("Architecture", workspaceContext?.architecture)
        appendWorkspaceSection("Review Focus", workspaceContext?.reviewFocus ?: defaultReviewFocus())

        if (codeContexts.isNotEmpty()) {
            appendLineWithBudget("Code Context", renderCodeContext(codeContexts), budget.maxContextChars)
        }

        appendLineWithBudget("Git Diff", stagedDiff, budget.maxDiffChars, fencedLanguage = "diff")
        appendOutputFormat()
    }

    private fun StringBuilder.appendWorkspaceSection(title: String, content: String?) {
        if (content.isNullOrBlank()) return
        appendLine("# $title")
        appendLine()
        appendLine(content.trim())
        appendLine()
    }

    private fun renderCodeContext(codeContexts: List<CodeContext>): String = buildString {
        for (ctx in codeContexts) {
            appendLine("### File: ${ctx.filePath}")
            appendLine("Class: ${ctx.className}")
            appendLine("Package: ${ctx.packageName}")

            if (ctx.annotations.isNotEmpty()) {
                appendLine("Annotations: ${ctx.annotations.joinToString(", ")}")
            }
            if (ctx.springSemantic != SpringSemantic.NONE) {
                appendLine("Spring Semantic: ${ctx.springSemantic.name}")
            }
            if (ctx.superClass != null) {
                appendLine("Extends: ${ctx.superClass}")
            }
            if (ctx.interfaces.isNotEmpty()) {
                appendLine("Implements: ${ctx.interfaces.joinToString(", ")}")
            }

            if (ctx.dependencies.isNotEmpty()) {
                appendLine()
                appendLine("Dependencies:")
                for (dep in ctx.dependencies) {
                    appendLine("- ${dep.fieldName}: ${dep.typeName} [${dep.injectionType}]")
                }
            }

            if (ctx.modifiedMethods.isNotEmpty()) {
                appendLine()
                appendLine("Modified Methods:")
                for (method in ctx.modifiedMethods) {
                    appendLine("- ${method.signature}: ${method.returnType}")
                    if (method.annotations.isNotEmpty()) {
                        appendLine("  Annotations: ${method.annotations.joinToString(", ")}")
                    }
                    // 只显示有意义的基础设施调用，过滤掉 UNKNOWN。
                    val significantCalls = method.methodCalls.filter { it.callType != "UNKNOWN" }
                    if (significantCalls.isNotEmpty()) {
                        appendLine("  Calls:")
                        for (call in significantCalls) {
                            val callDesc = if (call.qualifier.isNotEmpty()) {
                                "${call.qualifier}.${call.methodName}"
                            } else {
                                call.methodName
                            }
                            appendLine("  - $callDesc [${call.callType}]")
                        }
                    }
                }
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendLineWithBudget(
        title: String,
        content: String,
        maxChars: Int,
        fencedLanguage: String? = null
    ) {
        val effectiveMaxChars = maxChars.coerceAtLeast(0)
        appendLine("# $title")
        appendLine()
        val budgetedContent = content.withCharBudget(effectiveMaxChars)
        if (fencedLanguage != null) {
            appendLine("```$fencedLanguage")
            appendLine(budgetedContent)
            appendLine("```")
        } else {
            appendLine(budgetedContent)
        }
        if (content.length > effectiveMaxChars) {
            appendLine()
            appendLine("[Truncated: original ${content.length} chars, included $effectiveMaxChars chars.]")
        }
        appendLine()
    }

    private fun StringBuilder.appendOutputFormat() {
        appendLine("Return only a JSON array in this exact structure:")
        appendLine("""[
  {
    "level": "HIGH",
    "file": "UserService.java",
    "line": 42,
    "message": "问题描述（中文）"
  }
]""")
        appendLine()
        appendLine("Use level HIGH, MEDIUM, or LOW. Use null for line when no exact line is available.")
        appendLine("Point file and line to the most relevant added or modified line.")
        appendLine("Each message must describe the problem, the impact, and a concrete suggested fix in Chinese.")
        appendLine("The message must be written in Chinese.")
    }

    private fun String.withCharBudget(maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (length <= maxChars) return this
        return take(maxChars).trimEnd()
    }

    private fun defaultReviewFocus(): String = """
        - Bug 风险
        - null pointer issues
        - concurrency issues
        - transaction issues
        - SQL risk
        - security issues
        - readability
    """.trimIndent()
}
