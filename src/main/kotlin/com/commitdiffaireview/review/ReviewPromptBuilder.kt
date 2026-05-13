package com.commitdiffaireview.review

import com.commitdiffaireview.context.CodeContext
import com.commitdiffaireview.context.SpringSemantic

class ReviewPromptBuilder {

    /**
     * 构建 Review Prompt
     * @param stagedDiff unified diff 文本
     * @param codeContexts PSI 分析得到的代码上下文列表（可为空）
     */
    fun build(stagedDiff: String, codeContexts: List<CodeContext> = emptyList()): String = buildString {
        appendLine("You are a senior code reviewer. Review the following staged unified diff.")
        appendLine()

        // PSI Context 区域
        if (codeContexts.isNotEmpty()) {
            appendLine("## Code Context")
            appendLine()
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
                        if (method.methodCalls.isNotEmpty()) {
                            appendLine("  Calls:")
                            for (call in method.methodCalls) {
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

        // Diff 区域
        appendLine("## Diff")
        appendLine()
        appendLine("```diff")
        appendLine(stagedDiff)
        appendLine("```")
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
}
