package com.commitdiffaireview.review

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewPromptBuilderTest {
    private val builder = ReviewPromptBuilder()

    @Test
    fun `build prompt includes review focus and structured json format`() {
        val prompt = builder.build("diff --git a/UserService.java b/UserService.java")

        assertTrue(prompt.contains("Bug risk"))
        assertTrue(prompt.contains("null pointer"))
        assertTrue(prompt.contains("concurrency"))
        assertTrue(prompt.contains("transaction"))
        assertTrue(prompt.contains("SQL"))
        assertTrue(prompt.contains("security"))
        assertTrue(prompt.contains("readability"))
        assertTrue(prompt.contains("message must be written in Chinese"))
        assertTrue(prompt.contains("level"))
        assertTrue(prompt.contains("file"))
        assertTrue(prompt.contains("line"))
        assertTrue(prompt.contains("message"))
        assertTrue(prompt.contains("diff --git a/UserService.java b/UserService.java"))
    }
}
