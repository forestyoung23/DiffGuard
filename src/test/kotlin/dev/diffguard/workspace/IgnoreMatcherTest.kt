package dev.diffguard.workspace

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IgnoreMatcherTest {
    @Test
    fun `matches directories files and glob patterns`() {
        val matcher = IgnoreMatcher(
            listOf(
                "generated/",
                "legacy/",
                "*.generated.java",
                "src/main/resources/application-local.yml"
            )
        )

        assertTrue(matcher.isIgnored("generated/User.java"))
        assertTrue(matcher.isIgnored("src/main/generated/User.java"))
        assertTrue(matcher.isIgnored("legacy/OldService.java"))
        assertTrue(matcher.isIgnored("src/legacy/OldService.java"))
        assertTrue(matcher.isIgnored("src/main/java/User.generated.java"))
        assertTrue(matcher.isIgnored("src/main/resources/application-local.yml"))
        assertFalse(matcher.isIgnored("src/main/java/UserService.java"))
    }

    @Test
    fun `filters ignored file sections from unified diff`() {
        val matcher = IgnoreMatcher(listOf("generated/", "*.generated.java"))
        val diff = """
            diff --git a/generated/User.java b/generated/User.java
            +++ b/generated/User.java
            @@ -1,1 +1,1 @@
            +ignored
            diff --git a/src/main/java/App.java b/src/main/java/App.java
            +++ b/src/main/java/App.java
            @@ -1,1 +1,1 @@
            +kept
            diff --git a/src/main/java/Mapper.generated.java b/src/main/java/Mapper.generated.java
            +++ b/src/main/java/Mapper.generated.java
            @@ -1,1 +1,1 @@
            +ignored generated file
        """.trimIndent()

        val filtered = matcher.filterDiff(diff)

        assertFalse(filtered.contains("generated/User.java"), filtered)
        assertFalse(filtered.contains("Mapper.generated.java"), filtered)
        assertTrue(filtered.contains("src/main/java/App.java"), filtered)
        assertTrue(filtered.contains("+kept"), filtered)
    }
}
