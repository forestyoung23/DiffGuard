package com.commitdiffaireview.git

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
}
