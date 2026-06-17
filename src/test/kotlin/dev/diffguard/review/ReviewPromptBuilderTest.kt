package dev.diffguard.review

import dev.diffguard.context.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ReviewPromptBuilderTest {

    private val builder = ReviewPromptBuilder()

    @Test
    fun `prompt contains diff and review categories`() {
        val prompt = builder.build("diff --git a/Foo.java", emptyList())

        assertTrue(prompt.contains("staged unified diff", ignoreCase = true))
        assertTrue(prompt.contains("Bug"))
        assertTrue(prompt.contains("null pointer"))
        assertTrue(prompt.contains("concurrency"))
        assertTrue(prompt.contains("transaction"))
        assertTrue(prompt.contains("SQL"))
        assertTrue(prompt.contains("security"))
        assertTrue(prompt.contains("readability"))
        assertTrue(prompt.contains("diff --git a/Foo.java"))
    }

    @Test
    fun `prompt contains code context when provided`() {
        val contexts = listOf(
            CodeContext(
                filePath = "src/main/java/com/demo/UserService.java",
                packageName = "com.demo",
                className = "UserService",
                superClass = null,
                interfaces = emptyList(),
                annotations = listOf("@Service"),
                dependencies = listOf(
                    DependencyInfo("userMapper", "UserMapper", DependencyInfo.AUTOWIRED)
                ),
                springSemantic = SpringSemantic.SERVICE,
                modifiedMethods = listOf(
                    MethodContext(
                        methodName = "createUser",
                        signature = "createUser(UserDTO dto)",
                        returnType = "String",
                        annotations = listOf("@Transactional"),
                        methodCalls = listOf(
                            MethodCall("userMapper", "insert", MethodCall.MAPPER)
                        )
                    )
                )
            )
        )

        val prompt = builder.build("diff content", contexts)

        assertTrue(prompt.contains("Code Context"))
        assertTrue(prompt.contains("UserService"))
        assertTrue(prompt.contains("com.demo"))
        assertTrue(prompt.contains("@Service"))
        assertTrue(prompt.contains("SERVICE"))
        assertTrue(prompt.contains("UserMapper"))
        assertTrue(prompt.contains("AUTOWIRED"))
        assertTrue(prompt.contains("createUser"))
        assertTrue(prompt.contains("@Transactional"))
        assertTrue(prompt.contains("userMapper.insert"))
        assertTrue(prompt.contains("MAPPER"))
    }

    @Test
    fun `prompt does not contain context section when contexts empty`() {
        val prompt = builder.build("diff content", emptyList())

        assertFalse(prompt.contains("## Code Context"))
    }

    @Test
    fun `prompt contains multiple files context`() {
        val contexts = listOf(
            CodeContext(
                filePath = "src/UserService.java",
                packageName = "com.demo",
                className = "UserService",
                superClass = null,
                interfaces = emptyList(),
                annotations = emptyList(),
                dependencies = emptyList(),
                springSemantic = SpringSemantic.NONE,
                modifiedMethods = emptyList()
            ),
            CodeContext(
                filePath = "src/OrderController.java",
                packageName = "com.demo",
                className = "OrderController",
                superClass = null,
                interfaces = emptyList(),
                annotations = listOf("@RestController"),
                dependencies = emptyList(),
                springSemantic = SpringSemantic.CONTROLLER,
                modifiedMethods = emptyList()
            )
        )

        val prompt = builder.build("diff", contexts)

        assertTrue(prompt.contains("UserService"))
        assertTrue(prompt.contains("OrderController"))
    }

    @Test
    fun `prompt truncates oversized diff with explicit notice`() {
        val prompt = ReviewPromptBuilder(PromptBudget(maxDiffChars = 20, maxContextChars = 100))
            .build("diff --git a/Foo.java b/Foo.java\n" + "x".repeat(100), emptyList())

        assertTrue(prompt.contains("diff --git a/Foo.ja"))
        assertFalse(prompt.contains("x".repeat(80)))
        assertTrue(prompt.contains("[Truncated: original"))
    }

    @Test
    fun `prompt truncates oversized code context separately from diff`() {
        val contexts = listOf(
            CodeContext(
                filePath = "src/UserService.java",
                packageName = "com.demo",
                className = "UserService",
                superClass = null,
                interfaces = List(50) { "Interface$it" },
                annotations = emptyList(),
                dependencies = emptyList(),
                springSemantic = SpringSemantic.NONE,
                modifiedMethods = emptyList()
            )
        )

        val prompt = ReviewPromptBuilder(PromptBudget(maxDiffChars = 100, maxContextChars = 40))
            .build("diff", contexts)

        assertTrue(prompt.contains("## Code Context"))
        assertTrue(prompt.contains("[Truncated: original"))
        assertTrue(prompt.contains("```diff\ndiff\n```"))
    }
}
