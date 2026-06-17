package dev.diffguard.review

import dev.diffguard.model.ReviewFinding
import kotlinx.serialization.json.Json

sealed interface ReviewParseResult {
    data class Parsed(val findings: List<ReviewFinding>) : ReviewParseResult
    data class Fallback(
        val rawResponsePreview: String,
        val message: String = "AI 返回内容无法解析为结构化结果。"
    ) : ReviewParseResult
}

class ReviewResultParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawResponse: String): List<ReviewFinding> = when (val result = tryParse(rawResponse)) {
        is ReviewParseResult.Parsed -> result.findings
        is ReviewParseResult.Fallback -> listOf(
            ReviewFinding(
                level = "LOW",
                file = "AI Response",
                line = null,
                message = "AI 返回内容不是合法 JSON，已显示原始内容：${result.rawResponsePreview}"
            )
        )
    }

    fun tryParse(rawResponse: String): ReviewParseResult {
        for (candidate in jsonArrayCandidates(rawResponse).distinct()) {
            val parsed = runCatching {
                ReviewParseResult.Parsed(json.decodeFromString<List<ReviewFinding>>(candidate))
            }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        return ReviewParseResult.Fallback(rawResponsePreview = rawResponse.trim().take(1_000))
    }

    private fun jsonArrayCandidates(rawResponse: String): List<String> {
        val trimmed = rawResponse.trim()
        val candidates = mutableListOf<String>()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            candidates.add(trimmed)
        }

        Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .findAll(trimmed)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { it.startsWith("[") }
            .forEach { candidates.add(it) }

        candidates.addAll(extractBalancedArrays(trimmed))
        candidates.add(trimmed)
        return candidates
    }

    private fun extractBalancedArrays(text: String): List<String> {
        val arrays = mutableListOf<String>()
        for (index in text.indices) {
            if (text[index] == '[') {
                extractArrayAt(text, index)?.let { arrays.add(it) }
            }
        }
        return arrays
    }

    private fun extractArrayAt(text: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false

        for (index in startIndex until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(startIndex, index + 1)
                    }
                }
            }
        }
        return null
    }
}
