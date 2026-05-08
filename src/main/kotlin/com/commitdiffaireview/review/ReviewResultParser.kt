package com.commitdiffaireview.review

import com.commitdiffaireview.model.ReviewFinding
import kotlinx.serialization.json.Json

class ReviewResultParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawResponse: String): List<ReviewFinding> {
        val candidate = extractJsonArray(rawResponse)
        return runCatching {
            json.decodeFromString<List<ReviewFinding>>(candidate)
        }.getOrElse {
            listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "AI Response",
                    line = null,
                    message = "AI 返回内容不是合法 JSON，已显示原始内容：${rawResponse.trim().take(1_000)}"
                )
            )
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
