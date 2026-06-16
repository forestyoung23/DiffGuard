package com.commitdiffaireview.workspace

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loads workspace markdown files from ai review directory`() {
        val workspaceDir = Files.createDirectories(tempDir.resolve(".ai-review"))
        Files.writeString(workspaceDir.resolve("rules.md"), "# Rules\n\n- Redis Key 必须设置 TTL\n")
        Files.writeString(workspaceDir.resolve("architecture.md"), "# Architecture\n\nDDD architecture\n")
        Files.writeString(workspaceDir.resolve("review.md"), "# Review Focus\n\n- Transaction\n")
        Files.writeString(workspaceDir.resolve("ignore.md"), "generated/\n\n# comment\n*.generated.java\n")

        val result = WorkspaceLoader.loadFromRoot(tempDir)

        assertEquals("# Rules\n\n- Redis Key 必须设置 TTL", result.context.rules)
        assertEquals("# Architecture\n\nDDD architecture", result.context.architecture)
        assertEquals("# Review Focus\n\n- Transaction", result.context.reviewFocus)
        assertEquals(listOf("generated/", "*.generated.java"), result.context.ignorePatterns)
        assertTrue(result.status.workspaceFound)
        assertTrue(result.status.rulesLoaded)
        assertTrue(result.status.architectureLoaded)
        assertTrue(result.status.reviewLoaded)
        assertTrue(result.status.ignoreLoaded)
    }

    @Test
    fun `returns empty context when workspace directory is missing`() {
        val result = WorkspaceLoader.loadFromRoot(tempDir)

        assertNull(result.context.rules)
        assertNull(result.context.architecture)
        assertNull(result.context.reviewFocus)
        assertEquals(emptyList<String>(), result.context.ignorePatterns)
        assertFalse(result.status.workspaceFound)
        assertFalse(result.status.rulesLoaded)
        assertFalse(result.status.architectureLoaded)
        assertFalse(result.status.reviewLoaded)
        assertFalse(result.status.ignoreLoaded)
    }

    @Test
    fun `marks individual files missing while loading existing workspace files`() {
        val workspaceDir = Files.createDirectories(tempDir.resolve(".ai-review"))
        Files.writeString(workspaceDir.resolve("rules.md"), "- 禁止 select *\n")

        val result = WorkspaceLoader.loadFromRoot(tempDir)

        assertEquals("- 禁止 select *", result.context.rules)
        assertNull(result.context.architecture)
        assertNull(result.context.reviewFocus)
        assertTrue(result.status.workspaceFound)
        assertTrue(result.status.rulesLoaded)
        assertFalse(result.status.architectureLoaded)
        assertFalse(result.status.reviewLoaded)
        assertFalse(result.status.ignoreLoaded)
    }
}
