package dev.diffguard.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitStagedDiffProviderTest {
    @Test
    fun `uses cached diff parameters so unstaged changes are excluded`() {
        assertEquals("--cached", STAGED_DIFF_PARAMETERS.first())
        assertTrue(STAGED_DIFF_PARAMETERS.contains("--unified=3"))
        assertTrue(STAGED_DIFF_PARAMETERS.contains("--no-color"))
    }

    @Test
    fun `uses name-only work tree diff parameters for unstaged change detection`() {
        assertEquals("--name-only", UNSTAGED_DIFF_NAME_ONLY_PARAMETERS.first())
        assertTrue(UNSTAGED_DIFF_NAME_ONLY_PARAMETERS.contains("--no-color"))
        assertTrue(!UNSTAGED_DIFF_NAME_ONLY_PARAMETERS.contains("--cached"))
    }

    @Test
    fun `filters staged paths by unstaged paths only for a single repository`() {
        val stagedPaths = setOf("src/App.java", "src/OnlyStaged.java")
        val unstagedPathsByRoot = mapOf(
            "/repo" to setOf("src/App.java")
        )

        val result = stagedPaths.filterStagedSafePaths(unstagedPathsByRoot)

        assertEquals(setOf("src/OnlyStaged.java"), result)
    }

    @Test
    fun `keeps staged paths for multiple repositories to avoid cross-root false matches`() {
        val stagedPaths = setOf("src/App.java", "src/OnlyStaged.java")
        val unstagedPathsByRoot = mapOf(
            "/repo-a" to setOf("src/App.java"),
            "/repo-b" to setOf("src/App.java")
        )

        val result = stagedPaths.filterStagedSafePaths(unstagedPathsByRoot)

        assertEquals(stagedPaths, result)
    }
}
