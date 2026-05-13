package com.commitdiffaireview.review

import com.commitdiffaireview.context.CodeContext
import com.commitdiffaireview.context.SpringSemantic

data class PromptBudget(
    val maxDiffChars: Int = 80_000,
    val maxContextChars: Int = 30_000
)

class ReviewPromptBuilder(
    private val budget: PromptBudget = PromptBudget()
) {

    /**
     * 构建 Review Prompt
     * @param stagedDiff unified diff 文本
     * @param codeContexts PSI 分析得到的代码上下文列表（可为空）
     */
    fun build(stagedDiff: String, codeContexts: List<CodeContext> = emptyList()): String = buildString {
        appendLine("You are a senior code reviewer. Review the following staged unified diff.")
        appendLine("Prioritize concrete correctness, security, concurrency, transaction, and data consistency risks.")
        appendLine("If content is truncated, mention only risks that are supported by the visible diff/context.")
        appendLine()

        // PSI Context 区域
        if (codeContexts.isNotEmpty()) {
            appendLineWithBudget("Code Context", renderCodeContext(codeContexts), budget.maxContextChars)
        }

        // Diff 区域
        appendLineWithBudget("Diff", stagedDiff, budget.maxDiffChars, fencedLanguage = "diff")
        appendLine()

        // Focus Areas
        appendLine("## Focus Areas")
        appendLine()
        appendLine("- Bug 风险")
        appendLine("- null pointer issues")
        appendLine("- concurrency issues")
        appendLine("- transaction issues")
        appendLine("- SQL risk")
        appendLine("- security issues")
        appendLine("- readability")
        appendLine()

        // 输出格式要求
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
        appendLine("The message must be written in Chinese.")
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
                    // 只显示有意义的基础设施调用，过滤掉 UNKNOWN
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
        appendLine("## $title")
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

    private fun String.withCharBudget(maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (length <= maxChars) return this
        return take(maxChars).trimEnd()
    }
}
