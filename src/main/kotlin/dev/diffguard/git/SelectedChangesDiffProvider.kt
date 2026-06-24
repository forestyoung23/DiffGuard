package dev.diffguard.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.application.runReadAction

class SelectedChangesDiffProvider(
    private val project: Project,
    private val changes: List<Change>
) {
    fun getDiff(): String = runReadAction {
        val result = StringBuilder()

        changes.forEach { change ->
            val diff = generateDiffForChange(change)
            if (diff.isNotBlank()) {
                result.append(diff)
                result.append("\n")
            }
        }

        result.toString().trim()
    }

    private fun generateDiffForChange(change: Change): String {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision

        if (beforeRevision == null && afterRevision == null) return ""

        val filePath = (afterRevision?.file?.path ?: beforeRevision?.file?.path) ?: return ""
        val beforeContent = beforeRevision?.content ?: ""
        val afterContent = afterRevision?.content ?: ""

        if (beforeContent == afterContent) return ""

        return buildGitStyleDiff(filePath, beforeContent, afterContent)
    }

    private fun buildGitStyleDiff(filePath: String, beforeContent: String, afterContent: String): String {
        val beforeLines = beforeContent.lines()
        val afterLines = afterContent.lines()

        val result = StringBuilder()
        result.append("diff --git a/$filePath b/$filePath\n")

        when {
            beforeContent.isEmpty() -> {
                result.append("new file mode 100644\n")
                result.append("index 0000000..0000000\n")
                result.append("--- /dev/null\n")
                result.append("+++ b/$filePath\n")
                result.append("@@ -0,0 +1,${afterLines.size} @@\n")
                afterLines.forEach { line ->
                    result.append("+$line\n")
                }
            }
            afterContent.isEmpty() -> {
                result.append("deleted file mode 100644\n")
                result.append("index 0000000..0000000\n")
                result.append("--- a/$filePath\n")
                result.append("+++ /dev/null\n")
                result.append("@@ -1,${beforeLines.size} +0,0 @@\n")
                beforeLines.forEach { line ->
                    result.append("-$line\n")
                }
            }
            else -> {
                result.append("index 0000000..0000000 100644\n")
                result.append("--- a/$filePath\n")
                result.append("+++ b/$filePath\n")
                result.append(generateUnifiedDiff(beforeLines, afterLines))
            }
        }

        return result.toString()
    }

    private fun generateUnifiedDiff(beforeLines: List<String>, afterLines: List<String>): String {
        val result = StringBuilder()
        val contextLines = 3

        var i = 0
        var j = 0

        while (i < beforeLines.size || j < afterLines.size) {
            // Find next difference
            val matchStart = findNextMatch(beforeLines, afterLines, i, j)

            if (matchStart.first == i && matchStart.second == j && i < beforeLines.size && j < afterLines.size) {
                // Lines match, skip
                i++
                j++
                continue
            }

            // Found a difference, create a hunk
            val hunkStartBefore = maxOf(0, i - contextLines)
            val hunkStartAfter = maxOf(0, j - contextLines)

            val hunkContent = StringBuilder()
            var hunkI = hunkStartBefore
            var hunkJ = hunkStartAfter

            // Add context before
            while (hunkI < i && hunkJ < j) {
                hunkContent.append(" ${beforeLines[hunkI]}\n")
                hunkI++
                hunkJ++
            }

            // Add removed lines
            while (hunkI < matchStart.first && hunkI < beforeLines.size) {
                hunkContent.append("-${beforeLines[hunkI]}\n")
                hunkI++
                i = hunkI
            }

            // Add added lines
            while (hunkJ < matchStart.second && hunkJ < afterLines.size) {
                hunkContent.append("+${afterLines[hunkJ]}\n")
                hunkJ++
                j = hunkJ
            }

            // Add context after
            val contextEnd = minOf(matchStart.first + contextLines, beforeLines.size)
            while (hunkI < contextEnd && hunkJ < afterLines.size) {
                if (beforeLines[hunkI] == afterLines[hunkJ]) {
                    hunkContent.append(" ${beforeLines[hunkI]}\n")
                    hunkI++
                    hunkJ++
                } else {
                    break
                }
            }

            val beforeCount = hunkI - hunkStartBefore
            val afterCount = hunkJ - hunkStartAfter

            if (beforeCount > 0 || afterCount > 0) {
                result.append("@@ -${hunkStartBefore + 1},$beforeCount +${hunkStartAfter + 1},$afterCount @@\n")
                result.append(hunkContent)
            }

            i = hunkI
            j = hunkJ
        }

        return result.toString()
    }

    private fun findNextMatch(beforeLines: List<String>, afterLines: List<String>, startI: Int, startJ: Int): Pair<Int, Int> {
        for (i in startI until minOf(startI + 100, beforeLines.size)) {
            for (j in startJ until minOf(startJ + 100, afterLines.size)) {
                if (beforeLines[i] == afterLines[j]) {
                    return Pair(i, j)
                }
            }
        }
        return Pair(beforeLines.size, afterLines.size)
    }
}
