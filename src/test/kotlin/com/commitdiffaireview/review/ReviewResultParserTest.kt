package com.commitdiffaireview.review

import com.commitdiffaireview.review.ReviewParseResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReviewResultParserTest {
    private val parser = ReviewResultParser()

    @Test
    fun `parse pure json array`() {
        val result = parser.parse(
            """
            [
              {"level":"HIGH","file":"UserService.java","line":42,"message":"Potential null pointer"}
            ]
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals("HIGH", result[0].level)
        assertEquals("UserService.java", result[0].file)
        assertEquals(42, result[0].line)
        assertEquals("Potential null pointer", result[0].message)
    }

    @Test
    fun `parse fenced json block`() {
        val result = parser.parse(
            """
            ```json
            [{"level":"MEDIUM","file":"OrderService.kt","line":17,"message":"Transaction boundary is unclear"}]
            ```
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals("MEDIUM", result[0].level)
        assertEquals("OrderService.kt", result[0].file)
        assertEquals(17, result[0].line)
    }

    @Test
    fun `parse embedded json array`() {
        val result = parser.parse(
            "Here is the review: [{\"level\":\"LOW\",\"file\":\"README.md\",\"line\":null,\"message\":\"Improve readability\"}] Thanks."
        )

        assertEquals(1, result.size)
        assertEquals("LOW", result[0].level)
        assertEquals("README.md", result[0].file)
        assertNull(result[0].line)
    }

    @Test
    fun `fallback when output is invalid`() {
        val result = parser.parse("not json")

        assertEquals(1, result.size)
        assertEquals("LOW", result[0].level)
        assertEquals("AI Response", result[0].file)
        assertNull(result[0].line)
        assertEquals("AI 返回内容不是合法 JSON，已显示原始内容：not json", result[0].message)
    }

    @Test
    fun `tryParse returns parsed findings when output is valid`() {
        val result = parser.tryParse(
            """
            [{"level":"HIGH","file":"UserService.kt","line":42,"message":"可能空指针"}]
            """.trimIndent()
        )

        val parsed = result as ReviewParseResult.Parsed
        assertEquals(1, parsed.findings.size)
        assertEquals("HIGH", parsed.findings[0].level)
        assertEquals("UserService.kt", parsed.findings[0].file)
        assertEquals(42, parsed.findings[0].line)
        assertEquals("可能空指针", parsed.findings[0].message)
    }

    @Test
    fun `tryParse returns fallback with raw response preview when output is invalid`() {
        val result = parser.tryParse("not json")

        val fallback = result as ReviewParseResult.Fallback
        assertEquals("not json", fallback.rawResponsePreview)
        assertEquals("AI 返回内容无法解析为结构化结果。", fallback.message)
    }

    @Test
    fun `tryParse skips earlier invalid bracketed text and parses later json array`() {
        val result = parser.tryParse(
            """
            Model notes: [not json]
            Review result:
            [{"level":"LOW","file":"A.kt","line":1,"message":"建议补充测试"}]
            """.trimIndent()
        )

        val parsed = result as ReviewParseResult.Parsed
        assertEquals(1, parsed.findings.size)
        assertEquals("A.kt", parsed.findings[0].file)
    }

    @Test
    fun `tryParse handles brackets inside json strings`() {
        val result = parser.tryParse(
            """[{"level":"LOW","file":"A.kt","line":1,"message":"数组文本 [a, b] 不应截断 JSON"}]"""
        )

        val parsed = result as ReviewParseResult.Parsed
        assertEquals("数组文本 [a, b] 不应截断 JSON", parsed.findings[0].message)
    }
}
