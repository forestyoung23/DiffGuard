package com.commitdiffaireview.context

import com.intellij.openapi.project.Project

/**
 * 解析 unified diff 文本，提取每个 Java 文件的修改行号集合
 */
object DiffParser {

    /**
     * 解析 unified diff，返回 文件路径 → 修改行号集合（仅 + 侧）
     */
    fun parse(diff: String): Map<String, Set<Int>> {
        if (diff.isBlank()) return emptyMap()

        val result = mutableMapOf<String, MutableSet<Int>>()
        var currentFile: String? = null
        var newLineNum = 0

        for (line in diff.lines()) {
            when {
                // 匹配 diff --git a/path b/path
                line.startsWith("diff --git ") -> {
                    currentFile = extractFilePath(line)
                    if (currentFile != null && !currentFile.endsWith(".java")) {
                        currentFile = null
                    }
                }
                // 匹配 @@ -a,b +c,d @@
                line.startsWith("@@ ") -> {
                    newLineNum = extractNewStartLine(line)
                }
                // + 行（非 +++）
                currentFile != null && line.startsWith("+") && !line.startsWith("+++") -> {
                    result.getOrPut(currentFile) { mutableSetOf() }.add(newLineNum)
                    newLineNum++
                }
                // - 行（跳过，不增加 newLineNum）
                currentFile != null && line.startsWith("-") && !line.startsWith("---") -> {
                    // 跳过删除行
                }
                // 上下文行（以空格开头）
                currentFile != null && line.startsWith(" ") -> {
                    newLineNum++
                }
            }
        }

        return result.filterValues { it.isNotEmpty() }
    }

    /**
     * 从 diff --git a/path b/path 中提取文件路径（取 b/ 后面的部分）
     */
    private fun extractFilePath(line: String): String? {
        val parts = line.split(" ")
        if (parts.size < 4) return null
        val bPath = parts.lastOrNull() ?: return null
        return if (bPath.startsWith("b/")) bPath.substring(2) else bPath
    }

    /**
     * 从 @@ -a,b +c,d @@ 中提取新文件起始行号 c
     */
    private fun extractNewStartLine(line: String): Int {
        val regex = Regex("""@@\s+-\d+(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@""")
        val match = regex.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }
}

/**
 * Code Context 构建器
 * 当前仅包含 diff 解析功能，PSI 分析将在后续 Task 中集成
 */
class CodeContextBuilder(private val project: Project) {

    /**
     * 从 unified diff 构建 CodeContext 列表
     */
    fun buildFromDiff(diff: String): List<CodeContext> {
        val fileLineMap = DiffParser.parse(diff)
        if (fileLineMap.isEmpty()) return emptyList()

        // PSI 分析将在后续 Task 中实现
        return emptyList()
    }
}
