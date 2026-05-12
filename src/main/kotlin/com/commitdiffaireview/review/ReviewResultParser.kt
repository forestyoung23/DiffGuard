package com.commitdiffaireview.review

import com.commitdiffaireview.model.ReviewFinding
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
        val candidate = extractJsonArray(rawResponse)
        return runCatching {
            ReviewParseResult.Parsed(json.decodeFromString<List<ReviewFinding>>(candidate))
        }.getOrElse {
            ReviewParseResult.Fallback(rawResponsePreview = rawResponse.trim().take(1_000))
        }
    }

    private fun extractJsonArray(rawResponse: String): String {
        val trimmed = rawResponse.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
        }

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null && fenced.startsWith("[")) {
            return fenced
        }

        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }
}
