package com.commitdiffaireview.workspace

/**
 * 轻量 ignore 匹配器。
 *
 * 支持目录（generated/）、文件路径（src/Foo.java）和简单 glob（*.generated.java）。
 * 它不是完整 gitignore 引擎，只覆盖 Workspace Review 需要的稳定路径过滤。
 */
class IgnoreMatcher(patterns: List<String>) {
    private val rules = patterns.mapNotNull { IgnoreRule.from(it) }

    fun isIgnored(path: String): Boolean {
        val normalizedPath = normalizePath(path)
        return normalizedPath.isNotEmpty() && rules.any { it.matches(normalizedPath) }
    }

    /**
     * 按 diff --git section 过滤被忽略文件，避免 ignored 内容进入 AI Prompt 和 PSI Context。
     */
    fun filterDiff(diff: String): String {
        if (diff.isBlank() || rules.isEmpty()) return diff

        val keptSections = mutableListOf<String>()
        var currentPath: String? = null
        var currentLines = mutableListOf<String>()

        fun flushCurrentSection() {
            if (currentLines.isEmpty()) return
            if (currentPath == null || !isIgnored(currentPath.orEmpty())) {
                keptSections.add(currentLines.joinToString("\n"))
            }
        }

        for (line in diff.lineSequence()) {
            if (line.startsWith("diff --git ")) {
                flushCurrentSection()
                currentPath = extractGitDiffPath(line)
                currentLines = mutableListOf(line)
            } else {
                if (line.startsWith("+++ ")) {
                    extractNewFilePath(line)?.let { currentPath = it }
                }
                currentLines.add(line)
            }
        }
        flushCurrentSection()

        return keptSections.joinToString("\n").trim()
    }

    private data class IgnoreRule(
        val pattern: String,
        val directoryOnly: Boolean,
        val hasSlash: Boolean,
        val regex: Regex
    ) {
        fun matches(path: String): Boolean = if (directoryOnly) {
            matchesDirectory(path)
        } else {
            matchesFile(path)
        }

        private fun matchesDirectory(path: String): Boolean {
            val directoryCandidates = path.directoryCandidates()
            return if (hasSlash) {
                directoryCandidates.any { regex.matches(it) }
            } else {
                directoryCandidates.any { regex.matches(it.substringAfterLast('/')) }
            }
        }

        private fun matchesFile(path: String): Boolean = if (hasSlash) {
            regex.matches(path)
        } else {
            regex.matches(path.substringAfterLast('/'))
        }

        companion object {
            fun from(rawPattern: String): IgnoreRule? {
                val trimmed = rawPattern.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

                val normalized = normalizePath(trimmed)
                if (normalized.isEmpty()) return null

                val directoryOnly = normalized.endsWith("/")
                val pattern = normalized.trim('/')
                if (pattern.isEmpty()) return null

                return IgnoreRule(
                    pattern = pattern,
                    directoryOnly = directoryOnly,
                    hasSlash = pattern.contains('/'),
                    regex = globRegex(pattern)
                )
            }
        }
    }

    private companion object {
        fun normalizePath(path: String): String =
            path.trim()
                .replace('\\', '/')
                .removePrefix("/")
                .removePrefix("a/")
                .removePrefix("b/")

        fun extractGitDiffPath(line: String): String? {
            val tokens = parseGitPathTokens(line.removePrefix("diff --git ").trim())
            return tokens.getOrNull(1)?.let { normalizePath(it) }
        }

        fun extractNewFilePath(line: String): String? {
            val rawPath = parseGitPathTokens(line.removePrefix("+++ ").trim()).firstOrNull() ?: return null
            return rawPath.takeUnless { it == "/dev/null" }?.let { normalizePath(it) }
        }

        fun parseGitPathTokens(text: String): List<String> {
            val tokens = mutableListOf<String>()
            var index = 0
            while (index < text.length) {
                while (index < text.length && text[index].isWhitespace()) index++
                if (index >= text.length) break

                val token = StringBuilder()
                if (text[index] == '"') {
                    index++
                    var escaped = false
                    while (index < text.length) {
                        val char = text[index++]
                        when {
                            escaped -> {
                                token.append(char)
                                escaped = false
                            }
                            char == '\\' -> escaped = true
                            char == '"' -> break
                            else -> token.append(char)
                        }
                    }
                } else {
                    while (index < text.length && !text[index].isWhitespace()) {
                        token.append(text[index++])
                    }
                }
                tokens.add(token.toString())
            }
            return tokens
        }

        fun globRegex(pattern: String): Regex {
            val regex = StringBuilder("^")
            var index = 0
            while (index < pattern.length) {
                val char = pattern[index]
                when (char) {
                    '*' -> {
                        if (index + 1 < pattern.length && pattern[index + 1] == '*') {
                            regex.append(".*")
                            index++
                        } else {
                            regex.append("[^/]*")
                        }
                    }
                    '?' -> regex.append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%' -> regex.append('\\').append(char)
                    else -> regex.append(char)
                }
                index++
            }
            regex.append('$')
            return Regex(regex.toString())
        }

        fun String.directoryCandidates(): List<String> {
            val segments = split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return emptyList()
            return segments.dropLast(1).runningFold("") { prefix, segment ->
                if (prefix.isEmpty()) segment else "$prefix/$segment"
            }.drop(1)
        }
    }
}
