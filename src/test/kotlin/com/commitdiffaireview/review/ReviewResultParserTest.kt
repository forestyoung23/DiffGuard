package com.commitdiffaireview.review

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
}
