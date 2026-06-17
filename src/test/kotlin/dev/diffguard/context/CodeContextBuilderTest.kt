package dev.diffguard.context

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CodeContextBuilderTest {

    @Test
    fun `parse diff extracts Java file paths and line numbers`() {
        val diff = """
            diff --git a/src/main/java/com/demo/UserService.java b/src/main/java/com/demo/UserService.java
            index 1234567..abcdefg 100644
            --- a/src/main/java/com/demo/UserService.java
            +++ b/src/main/java/com/demo/UserService.java
            @@ -10,7 +10,9 @@ package com.demo;
                 @Service
                 import java.util.List;
                 public class UserService {
            -    private String name;
            +    private String name;
            +    private int age;
            +    private boolean active;
                 public void doSomething() {
                 }
                 // end of class
            diff --git a/src/main/java/com/demo/OrderService.java b/src/main/java/com/demo/OrderService.java
            index 1111111..2222222 100644
            --- a/src/main/java/com/demo/OrderService.java
            +++ b/src/main/java/com/demo/OrderService.java
            @@ -20,6 +20,8 @@ package com.demo;
                 String status = "init";
                 public void process() {
            +        validate();
            +        submit();
                 }
                 public void cleanup() {
                 }
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertEquals(2, result.size)
        assertTrue(result.containsKey("src/main/java/com/demo/UserService.java"))
        assertTrue(result.containsKey("src/main/java/com/demo/OrderService.java"))

        val userLines = result["src/main/java/com/demo/UserService.java"]!!
        assertTrue(userLines.contains(13)) // +    private String name;
        assertTrue(userLines.contains(14)) // +    private int age;
        assertTrue(userLines.contains(15)) // +    private boolean active;

        val orderLines = result["src/main/java/com/demo/OrderService.java"]!!
        assertTrue(orderLines.contains(22)) // +        validate();
        assertTrue(orderLines.contains(23)) // +        submit();
    }

    @Test
    fun `parse diff ignores non-Java files`() {
        val diff = """
            diff --git a/README.md b/README.md
            index 1234567..abcdefg 100644
            --- a/README.md
            +++ b/README.md
            @@ -1,3 +1,4 @@
             # Title
            +New line
             content
            diff --git a/src/main/java/com/demo/User.java b/src/main/java/com/demo/User.java
            index 1111111..2222222 100644
            --- a/src/main/java/com/demo/User.java
            +++ b/src/main/java/com/demo/User.java
            @@ -5,3 +5,4 @@
                 private String name;
            +    private int age;
             }
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("src/main/java/com/demo/User.java"))
    }

    @Test
    fun `parse diff returns empty map for blank diff`() {
        val result = DiffParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse diff handles multiple hunks in same file`() {
        val diff = """
            diff --git a/src/main/java/com/demo/Service.java b/src/main/java/com/demo/Service.java
            index 1234567..abcdefg 100644
            --- a/src/main/java/com/demo/Service.java
            +++ b/src/main/java/com/demo/Service.java
            @@ -10,3 +10,4 @@
                 private String a;
                 private String c;
            +    private String b;
             }
            @@ -20,3 +20,4 @@
                 public void method1() {
                 // setup
            +        call();
                 }
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertEquals(1, result.size)
        val lines = result["src/main/java/com/demo/Service.java"]!!
        assertTrue(lines.contains(12))
        assertTrue(lines.contains(22))
    }

    @Test
    fun `parse diff correctly calculates line numbers across hunks`() {
        val diff = """
            diff --git a/src/main/java/com/demo/A.java b/src/main/java/com/demo/A.java
            index 1234567..abcdefg 100644
            --- a/src/main/java/com/demo/A.java
            +++ b/src/main/java/com/demo/A.java
            @@ -1,5 +1,6 @@
             line1
            +added1
             line2
             line3
             line4
            +added2
             line5
        """.trimIndent()

        val result = DiffParser.parse(diff)
        val lines = result["src/main/java/com/demo/A.java"]!!
        // +added1 is at new line 2
        assertTrue(lines.contains(2))
        // +added2 is at new line 6
        assertTrue(lines.contains(6))
    }

    @Test
    fun `parse diff handles quoted Java file paths with spaces`() {
        val diff = """
            diff --git "a/src/main/java/com/demo/User Service.java" "b/src/main/java/com/demo/User Service.java"
            index 1234567..abcdefg 100644
            --- "a/src/main/java/com/demo/User Service.java"
            +++ "b/src/main/java/com/demo/User Service.java"
            @@ -3,3 +3,4 @@
              class UserService {
            +     private String name;
              }
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertEquals(setOf(4), result["src/main/java/com/demo/User Service.java"])
    }

    @Test
    fun `parse diff ignores deleted Java files for PSI context`() {
        val diff = """
            diff --git a/src/main/java/com/demo/Deleted.java b/src/main/java/com/demo/Deleted.java
            deleted file mode 100644
            index 1234567..0000000
            --- a/src/main/java/com/demo/Deleted.java
            +++ /dev/null
            @@ -1,3 +0,0 @@
            -class Deleted {
            -}
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse diff can limit Java files for PSI context`() {
        val diff = (1..3).joinToString("\n") { index ->
            """
                diff --git a/src/main/java/com/demo/File$index.java b/src/main/java/com/demo/File$index.java
                +++ b/src/main/java/com/demo/File$index.java
                @@ -1 +1 @@
                +class File$index {}
            """.trimIndent()
        }

        val result = DiffParser.parse(diff, maxJavaFiles = 2)

        assertEquals(
            listOf(
                "src/main/java/com/demo/File1.java",
                "src/main/java/com/demo/File2.java"
            ),
            result.keys.toList()
        )
    }
}
