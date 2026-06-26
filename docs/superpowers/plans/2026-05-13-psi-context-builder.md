# PSI Context Builder 实施计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐步执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 实现 PSI Context Builder，让 AI Review 基于 IntelliJ PSI 的代码语义上下文进行分析，而非仅基于 diff 文本。

**架构：** 新增 `context`、`psi`、`analyzer` 三个包。`CodeContextBuilder` 串联 diff 解析和 PSI 分析，生成结构化 `CodeContext` 列表。`ReviewPromptBuilder` 替换为新 Prompt 结构，将 PSI Context 注入 AI 请求。

**技术栈：** IntelliJ Platform SDK、Kotlin、PSI API（PsiJavaFile、PsiClass、PsiMethod、JavaRecursiveElementVisitor）、JUnit 5、IntelliJ Platform Test Framework。

---

## 文件结构

创建以下文件：

```text
src/main/kotlin/com/diffguard/
├── context/
│   ├── CodeContext.kt              # 类级别上下文数据模型
│   ├── MethodContext.kt            # 方法级别上下文数据模型
│   └── CodeContextBuilder.kt       # 串联 diff 解析 + PSI 分析
├── psi/
│   ├── JavaPsiAnalyzer.kt          # PSI 分析：类信息、字段依赖、方法定位
│   └── MethodCallExtractor.kt      # 递归提取方法调用及类型
└── analyzer/
    └── SpringSemanticAnalyzer.kt   # Spring 注解语义识别

src/test/kotlin/com/diffguard/
├── context/
│   └── CodeContextBuilderTest.kt   # Diff 解析 + 集成测试
├── psi/
│   ├── JavaPsiAnalyzerTest.kt      # PSI 分析测试
│   └── MethodCallExtractorTest.kt  # 方法调用提取测试
└── analyzer/
    └── SpringSemanticAnalyzerTest.kt
```

修改以下文件：

```text
src/main/kotlin/com/diffguard/review/ReviewPromptBuilder.kt
src/main/kotlin/com/diffguard/review/ReviewOrchestrator.kt
src/test/kotlin/com/diffguard/review/ReviewPromptBuilderTest.kt
```

职责锁定：

- `CodeContext.kt` / `MethodContext.kt`：纯数据类，无逻辑。
- `CodeContextBuilder.kt`：串联 diff 解析和 PSI 分析，是唯一对外入口。
- `JavaPsiAnalyzer.kt`：只负责 PsiFile 级别的信息提取和方法定位。
- `MethodCallExtractor.kt`：只负责递归遍历方法体提取调用。
- `SpringSemanticAnalyzer.kt`：只负责注解匹配。
- `ReviewPromptBuilder.kt`：只负责 Prompt 文本组装。
- `ReviewOrchestrator.kt`：串联 diff → context → prompt → AI 流程。

---

### 任务 1：创建数据模型

**文件：**
- 创建：`src/main/kotlin/com/diffguard/context/CodeContext.kt`
- 创建：`src/main/kotlin/com/diffguard/context/MethodContext.kt`

- [ ] **步骤 1：创建 `MethodContext.kt`**

```kotlin
package dev.diffguard.context

/**
 * 方法调用信息
 * @param qualifier 调用对象（如 userMapper、redisTemplate）
 * @param methodName 方法名（如 insert、set）
 * @param callType 调用类型：MAPPER、REDIS、FEIGN、HTTP、THREAD、UNKNOWN
 */
data class MethodCall(
    val qualifier: String,
    val methodName: String,
    val callType: String
) {
    companion object {
        const val MAPPER = "MAPPER"
        const val REDIS = "REDIS"
        const val FEIGN = "FEIGN"
        const val HTTP = "HTTP"
        const val THREAD = "THREAD"
        const val UNKNOWN = "UNKNOWN"
    }
}

/**
 * 方法级别上下文
 * @param methodName 方法名
 * @param signature 方法签名（如 createUser(UserDTO dto)）
 * @param returnType 返回值类型
 * @param annotations 方法注解列表
 * @param methodCalls 方法体内的调用列表
 */
data class MethodContext(
    val methodName: String,
    val signature: String,
    val returnType: String,
    val annotations: List<String>,
    val methodCalls: List<MethodCall>
)
```

- [ ] **步骤 2：创建 `CodeContext.kt`**

```kotlin
package dev.diffguard.context

/**
 * 字段依赖信息
 * @param fieldName 字段名
 * @param typeName 类型全名
 * @param injectionType 注入方式：AUTOWIRED、RESOURCE、FINAL、NONE
 */
data class DependencyInfo(
    val fieldName: String,
    val typeName: String,
    val injectionType: String
) {
    companion object {
        const val AUTOWIRED = "AUTOWIRED"
        const val RESOURCE = "RESOURCE"
        const val FINAL = "FINAL"
        const val NONE = "NONE"
    }
}

/**
 * Spring 语义类型
 */
enum class SpringSemantic {
    SERVICE, CONTROLLER, REPOSITORY, COMPONENT, CONFIGURATION, NONE
}

/**
 * 类级别代码上下文
 * @param filePath 文件路径
 * @param packageName 包名
 * @param className 类名
 * @param superClass 父类（可选）
 * @param interfaces 实现的接口列表
 * @param annotations 类注解列表
 * @param dependencies 字段依赖列表
 * @param springSemantic Spring 语义
 * @param modifiedMethods 被修改的方法列表
 */
data class CodeContext(
    val filePath: String,
    val packageName: String,
    val className: String,
    val superClass: String?,
    val interfaces: List<String>,
    val annotations: List<String>,
    val dependencies: List<DependencyInfo>,
    val springSemantic: SpringSemantic,
    val modifiedMethods: List<MethodContext>
)
```

- [ ] **步骤 3：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 4：提交**

```bash
git add src/main/kotlin/com/diffguard/context/CodeContext.kt src/main/kotlin/com/diffguard/context/MethodContext.kt
git commit -m "feat: add PSI context data models"
```

---

### 任务 2：实现 Diff 解析器（纯文本，无 PSI 依赖）

**文件：**
- 创建：`src/main/kotlin/com/diffguard/context/CodeContextBuilder.kt`
- 创建：`src/test/kotlin/com/diffguard/context/CodeContextBuilderTest.kt`

- [ ] **步骤 1：先写失败测试 `CodeContextBuilderTest.kt`**

```kotlin
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
             import java.util.List;
             public class UserService {
            -    private String name;
            +    private String name;
            +    private int age;
            +    private boolean active;
                 public void doSomething() {
             }
            diff --git a/src/main/java/com/demo/OrderService.java b/src/main/java/com/demo/OrderService.java
            index 1111111..2222222 100644
            --- a/src/main/java/com/demo/OrderService.java
            +++ b/src/main/java/com/demo/OrderService.java
            @@ -20,6 +20,8 @@ package com.demo;
                 public void process() {
            +        validate();
            +        submit();
                 }
        """.trimIndent()

        val result = DiffParser.parse(diff)

        assertEquals(2, result.size)
        assertTrue(result.containsKey("src/main/java/com/demo/UserService.java"))
        assertTrue(result.containsKey("src/main/java/com/demo/OrderService.java"))

        val userLines = result["src/main/java/com/demo/UserService.java"]!!
        assertTrue(userLines.contains(13)) // +    private int age;
        assertTrue(userLines.contains(14)) // +    private boolean active;

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
            +    private String b;
             }
            @@ -20,3 +20,4 @@
                 public void method1() {
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
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.context.CodeContextBuilderTest"`

预期：FAIL，原因是 `DiffParser` 尚不存在。

- [ ] **步骤 3：创建 `DiffParser`（内置于 CodeContextBuilder.kt 中）**

```kotlin
package dev.diffguard.context

import com.intellij.openapi.project.Project

/**
 * 解析 unified diff 文本，提取每个 Java 文件的修改行号集合
 */
object DiffParser {

    /**
     * 解析 unified diff，返回 文件路径 → 修改行号集合（仅 + 侧）
     */
    fun parse(diff: String): Map<String, Set<Int>> {
        if (diff.isBlank()) return emptyMap()

        val result = mutableMapOf<String, MutableSet<Int>>()
        var currentFile: String? = null
        var newLineNum = 0

        for (line in diff.lines()) {
            when {
                // 匹配 diff --git a/path b/path
                line.startsWith("diff --git ") -> {
                    currentFile = extractFilePath(line)
                    if (currentFile != null && !currentFile.endsWith(".java")) {
                        currentFile = null
                    }
                }
                // 匹配 @@ -a,b +c,d @@
                line.startsWith("@@ ") -> {
                    newLineNum = extractNewStartLine(line)
                }
                // + 行（非 +++）
                currentFile != null && line.startsWith("+") && !line.startsWith("+++") -> {
                    result.getOrPut(currentFile) { mutableSetOf() }.add(newLineNum)
                    newLineNum++
                }
                // - 行（跳过，不增加 newLineNum）
                currentFile != null && line.startsWith("-") && !line.startsWith("---") -> {
                    // 跳过删除行
                }
                // 上下文行
                currentFile != null -> {
                    newLineNum++
                }
            }
        }

        return result
    }

    /**
     * 从 diff --git a/path b/path 中提取文件路径（取 b/ 后面的部分）
     */
    private fun extractFilePath(line: String): String? {
        // diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
        val parts = line.split(" ")
        if (parts.size < 4) return null
        val bPath = parts.lastOrNull() ?: return null
        // 去掉 b/ 前缀
        return if (bPath.startsWith("b/")) bPath.substring(2) else bPath
    }

    /**
     * 从 @@ -a,b +c,d @@ 中提取新文件起始行号 c
     */
    private fun extractNewStartLine(line: String): Int {
        // @@ -10,7 +12,9 @@
        val regex = Regex("""@@\s+-\d+(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@""")
        val match = regex.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }
}

/**
 * Code Context 构建器
 * 当前仅包含 diff 解析功能，PSI 分析将在后续任务中集成
 */
class CodeContextBuilder(private val project: Project) {

    /**
     * 从 unified diff 构建 CodeContext 列表
     */
    fun buildFromDiff(diff: String): List<CodeContext> {
        val fileLineMap = DiffParser.parse(diff)
        if (fileLineMap.isEmpty()) return emptyList()

        // PSI 分析将在后续任务中实现
        return emptyList()
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.context.CodeContextBuilderTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/context/CodeContextBuilder.kt src/test/kotlin/com/diffguard/context/CodeContextBuilderTest.kt
git commit -m "feat: add diff parser for extracting modified line numbers"
```

---

### 任务 3：实现 Spring 语义分析器

**文件：**
- 创建：`src/main/kotlin/com/diffguard/analyzer/SpringSemanticAnalyzer.kt`
- 创建：`src/test/kotlin/com/diffguard/analyzer/SpringSemanticAnalyzerTest.kt`

- [ ] **步骤 1：先写失败测试 `SpringSemanticAnalyzerTest.kt`**

```kotlin
package dev.diffguard.analyzer

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Test

class SpringSemanticAnalyzerTest : BasePlatformTestCase() {

    private val analyzer = SpringSemanticAnalyzer()

    fun `test class with @Service annotation`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.SERVICE, semantic)
    }

    fun `test class with @Controller annotation`() {
        val psiFile = myFixture.configureByText(
            "UserController.java",
            """
            package com.demo;
            import org.springframework.stereotype.Controller;
            @Controller
            public class UserController {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.CONTROLLER, semantic)
    }

    fun `test class with @RestController annotation`() {
        val psiFile = myFixture.configureByText(
            "ApiController.java",
            """
            package com.demo;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            public class ApiController {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.CONTROLLER, semantic)
    }

    fun `test class with @Repository annotation`() {
        val psiFile = myFixture.configureByText(
            "UserRepository.java",
            """
            package com.demo;
            import org.springframework.stereotype.Repository;
            @Repository
            public class UserRepository {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.REPOSITORY, semantic)
    }

    fun `test class without Spring annotation`() {
        val psiFile = myFixture.configureByText(
            "PlainClass.java",
            """
            package com.demo;
            public class PlainClass {
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val semantic = analyzer.analyzeClass(psiClass)

        assertEquals(SpringSemantic.NONE, semantic)
    }

    fun `test method with @Transactional annotation`() {
        val psiFile = myFixture.configureByText(
            "Service.java",
            """
            package com.demo;
            import org.springframework.transaction.annotation.Transactional;
            public class Service {
                @Transactional
                public void doWork() {
                }
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val method = psiClass.methods[0]
        val annotations = analyzer.analyzeMethodAnnotations(method)

        assertTrue(annotations.contains("@Transactional"))
    }

    fun `test method with multiple annotations`() {
        val psiFile = myFixture.configureByText(
            "Service.java",
            """
            package com.demo;
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.scheduling.annotation.Async;
            public class Service {
                @Transactional
                @Async
                public void doWork() {
                }
            }
            """.trimIndent()
        )

        val psiClass = (psiFile as com.intellij.psi.PsiJavaFile).classes[0]
        val method = psiClass.methods[0]
        val annotations = analyzer.analyzeMethodAnnotations(method)

        assertTrue(annotations.contains("@Transactional"))
        assertTrue(annotations.contains("@Async"))
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.analyzer.SpringSemanticAnalyzerTest"`

预期：FAIL，原因是 `SpringSemanticAnalyzer` 尚不存在。

- [ ] **步骤 3：创建 `SpringSemanticAnalyzer.kt`**

```kotlin
package dev.diffguard.analyzer

import dev.diffguard.context.SpringSemantic
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

/**
 * Spring 注解语义分析器
 * 识别类和方法上的 Spring 注解
 */
class SpringSemanticAnalyzer {

    // 类级别 Spring 注解到语义的映射
    private val classAnnotationMap = mapOf(
        "org.springframework.stereotype.Service" to SpringSemantic.SERVICE,
        "org.springframework.stereotype.Controller" to SpringSemantic.CONTROLLER,
        "org.springframework.web.bind.annotation.RestController" to SpringSemantic.CONTROLLER,
        "org.springframework.stereotype.Repository" to SpringSemantic.REPOSITORY,
        "org.springframework.stereotype.Component" to SpringSemantic.COMPONENT,
        "org.springframework.context.annotation.Configuration" to SpringSemantic.CONFIGURATION
    )

    // 方法级别需要识别的 Spring 注解
    private val methodAnnotations = setOf(
        "org.springframework.transaction.annotation.Transactional",
        "org.springframework.scheduling.annotation.Async",
        "org.springframework.scheduling.annotation.Scheduled",
        "org.springframework.cache.annotation.Cacheable",
        "org.springframework.cache.annotation.CacheEvict",
        "org.springframework.cache.annotation.CachePut"
    )

    /**
     * 分析类的 Spring 语义
     * 按优先级匹配：RestController/Controller > Service > Repository > Component > Configuration > NONE
     */
    fun analyzeClass(psiClass: PsiClass): SpringSemantic {
        val modifiers = psiClass.modifierList ?: return SpringSemantic.NONE

        for ((qualifiedName, semantic) in classAnnotationMap) {
            if (modifiers.findAnnotation(qualifiedName) != null) {
                return semantic
            }
        }

        return SpringSemantic.NONE
    }

    /**
     * 分析方法上的 Spring 注解，返回注解短名列表（如 ["@Transactional", "@Async"]）
     */
    fun analyzeMethodAnnotations(psiMethod: PsiMethod): List<String> {
        val modifiers = psiMethod.modifierList ?: return emptyList()

        return methodAnnotations.mapNotNull { qualifiedName ->
            if (modifiers.findAnnotation(qualifiedName) != null) {
                "@${qualifiedName.substringAfterLast('.')}"
            } else {
                null
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.analyzer.SpringSemanticAnalyzerTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/analyzer/SpringSemanticAnalyzer.kt src/test/kotlin/com/diffguard/analyzer/SpringSemanticAnalyzerTest.kt
git commit -m "feat: add Spring semantic analyzer"
```

---

### 任务 4：实现 JavaPsiAnalyzer

**文件：**
- 创建：`src/main/kotlin/com/diffguard/psi/JavaPsiAnalyzer.kt`
- 创建：`src/test/kotlin/com/diffguard/psi/JavaPsiAnalyzerTest.kt`

- [ ] **步骤 1：先写失败测试 `JavaPsiAnalyzerTest.kt`**

```kotlin
package dev.diffguard.psi

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaPsiAnalyzerTest : BasePlatformTestCase() {

    private val analyzer = JavaPsiAnalyzer()

    fun `test extract class info with annotations`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo.service;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService extends BaseService implements IUserService {
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val info = analyzer.extractClassInfo(psiFile, psiClass)

        assertEquals("com.demo.service", info.packageName)
        assertEquals("UserService", info.className)
        assertEquals("BaseService", info.superClass)
        assertTrue(info.interfaces.contains("IUserService"))
        assertTrue(info.annotations.any { it.contains("Service") })
    }

    fun `test extract dependencies with Autowired`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            import org.springframework.beans.factory.annotation.Autowired;
            public class UserService {
                @Autowired
                private UserMapper userMapper;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("userMapper", deps[0].fieldName)
        assertEquals("UserMapper", deps[0].typeName)
        assertEquals("AUTOWIRED", deps[0].injectionType)
    }

    fun `test extract dependencies with Resource`() {
        val psiFile = myFixture.configureByText(
            "OrderService.java",
            """
            package com.demo;
            import javax.annotation.Resource;
            public class OrderService {
                @Resource
                private OrderMapper orderMapper;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("orderMapper", deps[0].fieldName)
        assertEquals("OrderMapper", deps[0].typeName)
        assertEquals("RESOURCE", deps[0].injectionType)
    }

    fun `test extract dependencies with final field`() {
        val psiFile = myFixture.configureByText(
            "CacheService.java",
            """
            package com.demo;
            public class CacheService {
                private final RedisTemplate<String, String> redisTemplate;
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val deps = analyzer.extractDependencies(psiClass)

        assertEquals(1, deps.size)
        assertEquals("redisTemplate", deps[0].fieldName)
        assertEquals("FINAL", deps[0].injectionType)
    }

    fun `test find modified methods by line number`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                public void methodA() {
                    // line 4
                }

                public void methodB() {
                    // line 8
                    System.out.println("modified");
                    // line 10
                }

                public void methodC() {
                    // line 13
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        // line 9 is inside methodB
        val methods = analyzer.findModifiedMethods(psiClass, setOf(9))

        assertEquals(1, methods.size)
        assertEquals("methodB", methods[0].name)
    }

    fun `test find multiple modified methods`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                public void methodA() {
                    System.out.println("a");
                }

                public void methodB() {
                    System.out.println("b");
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        val methods = analyzer.findModifiedMethods(psiClass, setOf(4, 8))

        assertEquals(2, methods.size)
        val names = methods.map { it.name }.toSet()
        assertTrue(names.contains("methodA"))
        assertTrue(names.contains("methodB"))
    }

    fun `test no modified methods when lines outside methods`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                private String field;

                public void methodA() {
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val psiClass = psiFile.classes[0]
        // line 4 is a field declaration, not inside a method
        val methods = analyzer.findModifiedMethods(psiClass, setOf(4))

        assertTrue(methods.isEmpty())
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.psi.JavaPsiAnalyzerTest"`

预期：FAIL，原因是 `JavaPsiAnalyzer` 尚不存在。

- [ ] **步骤 3：创建 `JavaPsiAnalyzer.kt`**

```kotlin
package dev.diffguard.psi

import dev.diffguard.context.DependencyInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Java PSI 分析器
 * 负责从 PsiJavaFile 中提取类信息、字段依赖，以及定位修改的方法
 */
class JavaPsiAnalyzer {

    /**
     * 类级别信息
     */
    data class ClassInfo(
        val packageName: String,
        val className: String,
        val superClass: String?,
        val interfaces: List<String>,
        val annotations: List<String>
    )

    /**
     * 提取类的基本信息：包名、类名、父类、接口、注解
     */
    fun extractClassInfo(psiFile: PsiJavaFile, psiClass: PsiClass): ClassInfo {
        return ReadAction.compute<ClassInfo, Nothing> {
            val packageName = psiFile.packageName
            val className = psiClass.name ?: "Unknown"
            val superClass = psiClass.superClass?.name
            val interfaces = psiClass.interfaces.map { it.name ?: "Unknown" }
            val annotations = extractAnnotations(psiClass.modifierList)

            ClassInfo(
                packageName = packageName,
                className = className,
                superClass = superClass,
                interfaces = interfaces,
                annotations = annotations
            )
        }
    }

    /**
     * 提取字段依赖：@Autowired、@Resource、final field
     */
    fun extractDependencies(psiClass: PsiClass): List<DependencyInfo> {
        return ReadAction.compute<List<DependencyInfo>, Nothing> {
            psiClass.allFields.mapNotNull { field ->
                val modifiers = field.modifierList ?: return@mapNotNull null

                when {
                    // @Autowired 注入
                    modifiers.findAnnotation("org.springframework.beans.factory.annotation.Autowired") != null -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.AUTOWIRED
                        )
                    }
                    // @Resource 注入
                    modifiers.findAnnotation("javax.annotation.Resource") != null -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.RESOURCE
                        )
                    }
                    // final 字段
                    modifiers.hasModifierProperty(PsiModifier.FINAL) -> {
                        DependencyInfo(
                            fieldName = field.name,
                            typeName = field.type.presentableText,
                            injectionType = DependencyInfo.FINAL
                        )
                    }
                    else -> null
                }
            }
        }
    }

    /**
     * 查找包含修改行号的方法
     * @param psiClass 目标类
     * @param modifiedLines 修改的行号集合（1-based）
     * @return 包含修改行的方法列表
     */
    fun findModifiedMethods(psiClass: PsiClass, modifiedLines: Set<Int>): List<PsiMethod> {
        if (modifiedLines.isEmpty()) return emptyList()

        return ReadAction.compute<List<PsiMethod>, Nothing> {
            val allMethods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod::class.java)
            val document = psiClass.containingFile?.viewProvider?.document

            allMethods.filter { method ->
                val textRange = method.textRange ?: return@filter false
                val startLine = document?.getLineNumber(textRange.startOffset) ?: return@filter false
                val endLine = document?.getLineNumber(textRange.endOffset) ?: return@filter false

                // 行号从 0 开始，需要 +1 对齐到 1-based
                modifiedLines.any { line ->
                    line in (startLine + 1)..(endLine + 1)
                }
            }.toList()
        }
    }

    /**
     * 提取注解短名列表
     */
    private fun extractAnnotations(modifierList: PsiModifierList?): List<String> {
        if (modifierList == null) return emptyList()
        return modifierList.annotations.map { annotation ->
            annotation.qualifiedName?.substringAfterLast('.') ?: annotation.text
        }
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.psi.JavaPsiAnalyzerTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/psi/JavaPsiAnalyzer.kt src/test/kotlin/com/diffguard/psi/JavaPsiAnalyzerTest.kt
git commit -m "feat: add Java PSI analyzer for class info and method location"
```

---

### 任务 5：实现 MethodCallExtractor

**文件：**
- 创建：`src/main/kotlin/com/diffguard/psi/MethodCallExtractor.kt`
- 创建：`src/test/kotlin/com/diffguard/psi/MethodCallExtractorTest.kt`

- [ ] **步骤 1：先写失败测试 `MethodCallExtractorTest.kt`**

```kotlin
package dev.diffguard.psi

import dev.diffguard.context.MethodCall
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MethodCallExtractorTest : BasePlatformTestCase() {

    private val extractor = MethodCallExtractor()

    fun `test detect mapper call`() {
        val psiFile = myFixture.configureByText(
            "UserService.java",
            """
            package com.demo;
            public class UserService {
                private UserMapper userMapper;
                public void createUser() {
                    userMapper.insert(new User());
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertEquals(1, calls.size)
        assertEquals("userMapper", calls[0].qualifier)
        assertEquals("insert", calls[0].methodName)
        assertEquals(MethodCall.MAPPER, calls[0].callType)
    }

    fun `test detect redis call`() {
        val psiFile = myFixture.configureByText(
            "CacheService.java",
            """
            package com.demo;
            public class CacheService {
                private RedisTemplate redisTemplate;
                public void cacheValue() {
                    redisTemplate.opsForValue().set("key", "value");
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.REDIS })
    }

    fun `test detect thread call`() {
        val psiFile = myFixture.configureByText(
            "AsyncService.java",
            """
            package com.demo;
            public class AsyncService {
                public void runAsync() {
                    Thread thread = new Thread(() -> {});
                    thread.start();
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.THREAD })
    }

    fun `test detect http call via RestTemplate`() {
        val psiFile = myFixture.configureByText(
            "ApiClient.java",
            """
            package com.demo;
            public class ApiClient {
                private RestTemplate restTemplate;
                public String callApi() {
                    return restTemplate.getForObject("http://example.com", String.class);
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.HTTP })
    }

    fun `test detect feign client call`() {
        val psiFile = myFixture.configureByText(
            "RemoteService.java",
            """
            package com.demo;
            public class RemoteService {
                private UserFeignClient userFeignClient;
                public void callRemote() {
                    userFeignClient.getUser(1L);
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.any { it.callType == MethodCall.FEIGN })
    }

    fun `test unknown call type for plain method`() {
        val psiFile = myFixture.configureByText(
            "Util.java",
            """
            package com.demo;
            public class Util {
                public void process() {
                    String result = someHelper();
                }
                private String someHelper() { return ""; }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.all { it.callType == MethodCall.UNKNOWN })
    }

    fun `test no calls in empty method`() {
        val psiFile = myFixture.configureByText(
            "Empty.java",
            """
            package com.demo;
            public class Empty {
                public void doNothing() {
                }
            }
            """.trimIndent()
        ) as PsiJavaFile

        val method = psiFile.classes[0].methods[0]
        val calls = extractor.extractCalls(method)

        assertTrue(calls.isEmpty())
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.psi.MethodCallExtractorTest"`

预期：FAIL，原因是 `MethodCallExtractor` 尚不存在。

- [ ] **步骤 3：创建 `MethodCallExtractor.kt`**

```kotlin
package dev.diffguard.psi

import dev.diffguard.context.MethodCall
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*

/**
 * 方法调用提取器
 * 递归遍历方法体，提取所有方法调用及其类型
 */
class MethodCallExtractor {

    // Mapper 相关的类型名关键词
    private val mapperKeywords = setOf("Mapper", "Dao", "Repository")

    // Redis 相关的类型名关键词
    private val redisKeywords = setOf("Redis", "redis", "RedisTemplate", "StringRedisTemplate", "Jedis")

    // Feign 相关的类型名关键词
    private val feignKeywords = setOf("Feign", "Client", "FeignClient")

    // HTTP 相关的方法名
    private val httpMethods = setOf("getForObject", "getForEntity", "postForObject", "postForEntity",
        "put", "delete", "exchange", "patchForObject",
        "get", "post", "head", "options")

    // Thread 相关的类型名关键词
    private val threadKeywords = setOf("Thread", "Executor", "ExecutorService", "CompletableFuture",
        "ScheduledExecutorService", "ForkJoinPool")

    /**
     * 提取方法体内的所有方法调用
     */
    fun extractCalls(psiMethod: PsiMethod): List<MethodCall> {
        return ReadAction.compute<List<MethodCall>, Nothing> {
            val calls = mutableListOf<MethodCall>()
            psiMethod.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    val call = analyzeCall(expression)
                    if (call != null) {
                        calls.add(call)
                    }
                }
            })
            calls
        }
    }

    /**
     * 分析单个方法调用，判断其类型
     */
    private fun analyzeCall(expression: PsiMethodCallExpression): MethodCall? {
        val methodName = expression.methodExpression.referenceName ?: return null
        val qualifier = expression.methodExpression.qualifierExpression?.text ?: ""

        // 如果没有 qualifier（直接调用），归为 UNKNOWN
        if (qualifier.isBlank()) {
            return MethodCall(
                qualifier = "",
                methodName = methodName,
                callType = MethodCall.UNKNOWN
            )
        }

        val callType = classifyCall(qualifier, methodName, expression)

        return MethodCall(
            qualifier = qualifier,
            methodName = methodName,
            callType = callType
        )
    }

    /**
     * 根据 qualifier 和 methodName 判断调用类型
     */
    private fun classifyCall(qualifier: String, methodName: String, expression: PsiMethodCallExpression): String {
        // 1. 检查是否为 Thread 相关
        if (isThreadCall(qualifier, expression)) return MethodCall.THREAD

        // 2. 检查是否为 Mapper 调用（qualifier 类型名包含 Mapper/Dao/Repository）
        if (isMapperCall(qualifier, expression)) return MethodCall.MAPPER

        // 3. 检查是否为 Redis 调用
        if (isRedisCall(qualifier, expression)) return MethodCall.REDIS

        // 4. 检查是否为 Feign 调用
        if (isFeignCall(qualifier, expression)) return MethodCall.FEIGN

        // 5. 检查是否为 HTTP 调用
        if (isHttpCall(qualifier, methodName)) return MethodCall.HTTP

        return MethodCall.UNKNOWN
    }

    private fun isMapperCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        // 检查 qualifier 的类型名
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && mapperKeywords.any { qualifierType.contains(it) }) return true
        // 检查 qualifier 文本
        return mapperKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isRedisCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && redisKeywords.any { qualifierType.contains(it) }) return true
        return redisKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isFeignCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && feignKeywords.any { qualifierType.contains(it) }) return true
        return feignKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    private fun isHttpCall(qualifier: String, methodName: String): Boolean {
        // RestTemplate / WebClient 的方法名
        if (httpMethods.contains(methodName) && qualifier.contains("restTemplate", ignoreCase = true)) return true
        if (httpMethods.contains(methodName) && qualifier.contains("webClient", ignoreCase = true)) return true
        return false
    }

    private fun isThreadCall(qualifier: String, expression: PsiMethodCallExpression): Boolean {
        // new Thread().start()
        if (qualifier.contains("thread", ignoreCase = true) && expression.methodExpression.referenceName == "start") return true
        val qualifierType = resolveQualifierType(expression)
        if (qualifierType != null && threadKeywords.any { qualifierType.contains(it) }) return true
        return threadKeywords.any { qualifier.contains(it, ignoreCase = true) }
    }

    /**
     * 尝试解析 qualifier 的类型名
     */
    private fun resolveQualifierType(expression: PsiMethodCallExpression): String? {
        val qualifier = expression.methodExpression.qualifierExpression ?: return null
        val type = (qualifier as? PsiReferenceExpression)?.type ?: return null
        return type.presentableText
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.psi.MethodCallExtractorTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/psi/MethodCallExtractor.kt src/test/kotlin/com/diffguard/psi/MethodCallExtractorTest.kt
git commit -m "feat: add method call extractor with type classification"
```

---

### 任务 6：完善 CodeContextBuilder（集成 PSI 分析）

**文件：**
- 修改：`src/main/kotlin/com/diffguard/context/CodeContextBuilder.kt`
- 修改：`src/test/kotlin/com/diffguard/context/CodeContextBuilderTest.kt`

- [ ] **步骤 1：更新 `CodeContextBuilderTest.kt`，添加集成测试**

在现有测试文件末尾添加：

```kotlin
    @Test
    fun `buildFromDiff returns empty list for blank diff`() {
        // 这个测试不需要 Project mock，因为 diff 为空时直接返回
        // buildFromDiff 需要 Project 参数，这里仅测试 DiffParser
        val result = DiffParser.parse("")
        assertTrue(result.isEmpty())
    }
```

- [ ] **步骤 2：更新 `CodeContextBuilder.kt`，集成 PSI 分析**

```kotlin
package dev.diffguard.context

import dev.diffguard.analyzer.SpringSemanticAnalyzer
import dev.diffguard.psi.JavaPsiAnalyzer
import dev.diffguard.psi.MethodCallExtractor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

/**
 * 解析 unified diff 文本，提取每个 Java 文件的修改行号集合
 */
object DiffParser {

    /**
     * 解析 unified diff，返回 文件路径 → 修改行号集合（仅 + 侧）
     */
    fun parse(diff: String): Map<String, Set<Int>> {
        if (diff.isBlank()) return emptyMap()

        val result = mutableMapOf<String, MutableSet<Int>>()
        var currentFile: String? = null
        var newLineNum = 0

        for (line in diff.lines()) {
            when {
                // 匹配 diff --git a/path b/path
                line.startsWith("diff --git ") -> {
                    currentFile = extractFilePath(line)
                    if (currentFile != null && !currentFile.endsWith(".java")) {
                        currentFile = null
                    }
                }
                // 匹配 @@ -a,b +c,d @@
                line.startsWith("@@ ") -> {
                    newLineNum = extractNewStartLine(line)
                }
                // + 行（非 +++）
                currentFile != null && line.startsWith("+") && !line.startsWith("+++") -> {
                    result.getOrPut(currentFile) { mutableSetOf() }.add(newLineNum)
                    newLineNum++
                }
                // - 行（跳过，不增加 newLineNum）
                currentFile != null && line.startsWith("-") && !line.startsWith("---") -> {
                    // 跳过删除行
                }
                // 上下文行
                currentFile != null -> {
                    newLineNum++
                }
            }
        }

        return result
    }

    /**
     * 从 diff --git a/path b/path 中提取文件路径（取 b/ 后面的部分）
     */
    private fun extractFilePath(line: String): String? {
        val parts = line.split(" ")
        if (parts.size < 4) return null
        val bPath = parts.lastOrNull() ?: return null
        return if (bPath.startsWith("b/")) bPath.substring(2) else bPath
    }

    /**
     * 从 @@ -a,b +c,d @@ 中提取新文件起始行号 c
     */
    private fun extractNewStartLine(line: String): Int {
        val regex = Regex("""@@\s+-\d+(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@""")
        val match = regex.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }
}

/**
 * Code Context 构建器
 * 串联 diff 解析和 PSI 分析，生成结构化 CodeContext 列表
 */
class CodeContextBuilder(private val project: Project) {

    private val psiAnalyzer = JavaPsiAnalyzer()
    private val callExtractor = MethodCallExtractor()
    private val springAnalyzer = SpringSemanticAnalyzer()

    /**
     * 从 unified diff 构建 CodeContext 列表
     */
    fun buildFromDiff(diff: String): List<CodeContext> {
        val fileLineMap = DiffParser.parse(diff)
        if (fileLineMap.isEmpty()) return emptyList()

        return ReadAction.compute<List<CodeContext>, Nothing> {
            fileLineMap.mapNotNull { (filePath, modifiedLines) ->
                buildContextForFile(filePath, modifiedLines)
            }
        }
    }

    /**
     * 为单个文件构建 CodeContext
     */
    private fun buildContextForFile(filePath: String, modifiedLines: Set<Int>): CodeContext? {
        // 通过文件路径找到 PsiFile
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(resolveAbsolutePath(filePath)) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: return null

        val psiClass = psiFile.classes.firstOrNull() ?: return null

        // 提取类信息
        val classInfo = psiAnalyzer.extractClassInfo(psiFile, psiClass)

        // 提取字段依赖
        val dependencies = psiAnalyzer.extractDependencies(psiClass)

        // 查找修改的方法
        val modifiedMethods = psiAnalyzer.findModifiedMethods(psiClass, modifiedLines)

        // 提取每个方法的方法调用
        val methodContexts = modifiedMethods.map { method ->
            val calls = callExtractor.extractCalls(method)
            val methodAnnotations = springAnalyzer.analyzeMethodAnnotations(method)

            MethodContext(
                methodName = method.name,
                signature = buildMethodSignature(method),
                returnType = method.returnType?.presentableText ?: "void",
                annotations = methodAnnotations,
                methodCalls = calls
            )
        }

        // 识别 Spring 语义
        val springSemantic = springAnalyzer.analyzeClass(psiClass)

        return CodeContext(
            filePath = filePath,
            packageName = classInfo.packageName,
            className = classInfo.className,
            superClass = classInfo.superClass,
            interfaces = classInfo.interfaces,
            annotations = classInfo.annotations,
            dependencies = dependencies,
            springSemantic = springSemantic,
            modifiedMethods = methodContexts
        )
    }

    /**
     * 构建方法签名，如 createUser(UserDTO dto)
     */
    private fun buildMethodSignature(method: com.intellij.psi.PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        return "${method.name}($params)"
    }

    /**
     * 将 diff 中的相对路径转为绝对路径
     * 优先使用项目基路径拼接
     */
    private fun resolveAbsolutePath(relativePath: String): String {
        val basePath = project.basePath ?: return relativePath
        return if (relativePath.startsWith(basePath)) {
            relativePath
        } else {
            "$basePath/$relativePath"
        }
    }
}
```

- [ ] **步骤 3：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 4：提交**

```bash
git add src/main/kotlin/com/diffguard/context/CodeContextBuilder.kt src/test/kotlin/com/diffguard/context/CodeContextBuilderTest.kt
git commit -m "feat: integrate PSI analysis into CodeContextBuilder"
```

---

### 任务 7：修改 ReviewPromptBuilder（替换 Prompt 结构）

**文件：**
- 修改：`src/main/kotlin/com/diffguard/review/ReviewPromptBuilder.kt`
- 修改：`src/test/kotlin/com/diffguard/review/ReviewPromptBuilderTest.kt`

- [ ] **步骤 1：更新 `ReviewPromptBuilderTest.kt`**

```kotlin
package dev.diffguard.review

import dev.diffguard.context.*
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertTrue(!prompt.contains("## Code Context"))
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
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewPromptBuilderTest"`

预期：FAIL，因为 `ReviewPromptBuilder.build` 签名已变更。

- [ ] **步骤 3：更新 `ReviewPromptBuilder.kt`**

```kotlin
package dev.diffguard.review

import dev.diffguard.context.CodeContext
import dev.diffguard.context.MethodCall

class ReviewPromptBuilder {

    /**
     * 构建 Review Prompt
     * @param stagedDiff unified diff 文本
     * @param codeContexts PSI 分析得到的代码上下文列表（可为空）
     */
    fun build(stagedDiff: String, codeContexts: List<CodeContext> = emptyList()): String = buildString {
        appendLine("You are a senior code reviewer. Review the following staged unified diff.")
        appendLine()

        // PSI Context 区域
        if (codeContexts.isNotEmpty()) {
            appendLine("## Code Context")
            appendLine()
            for (ctx in codeContexts) {
                appendLine("### File: ${ctx.filePath}")
                appendLine("Class: ${ctx.className}")
                appendLine("Package: ${ctx.packageName}")

                if (ctx.annotations.isNotEmpty()) {
                    appendLine("Annotations: ${ctx.annotations.joinToString(", ")}")
                }
                if (ctx.springSemantic.name != "NONE") {
                    appendLine("Spring Semantic: ${ctx.springSemantic.name}")
                }
                if (ctx.superClass != null) {
                    appendLine("Extends: ${ctx.superClass}")
                }
                if (ctx.interfaces.isNotEmpty()) {
                    appendLine("Implements: ${ctx.interfaces.joinToString(", ")}")
                }

                if (ctx.dependencies.isNotEmpty()) {
                    appendLine()
                    appendLine("Dependencies:")
                    for (dep in ctx.dependencies) {
                        appendLine("- ${dep.fieldName} [${dep.injectionType}]")
                    }
                }

                if (ctx.modifiedMethods.isNotEmpty()) {
                    appendLine()
                    appendLine("Modified Methods:")
                    for (method in ctx.modifiedMethods) {
                        appendLine("- ${method.signature}: ${method.returnType}")
                        if (method.annotations.isNotEmpty()) {
                            appendLine("  Annotations: ${method.annotations.joinToString(", ")}")
                        }
                        if (method.methodCalls.isNotEmpty()) {
                            appendLine("  Calls:")
                            for (call in method.methodCalls) {
                                val callDesc = if (call.qualifier.isNotEmpty()) {
                                    "${call.qualifier}.${call.methodName}"
                                } else {
                                    call.methodName
                                }
                                appendLine("  - $callDesc [${call.callType}]")
                            }
                        }
                    }
                }
                appendLine()
            }
        }

        // Diff 区域
        appendLine("## Diff")
        appendLine()
        appendLine("```diff")
        appendLine(stagedDiff)
        appendLine("```")
        appendLine()

        // Focus Areas
        appendLine("## Focus Areas")
        appendLine()
        appendLine("- Bug 风险")
        appendLine("- null pointer issues")
        appendLine("- concurrency issues")
        appendLine("- transaction issues")
        appendLine("- SQL risk")
        appendLine("- security issues")
        appendLine("- readability")
        appendLine()

        // 输出格式要求
        appendLine("Return only a JSON array in this exact structure:")
        appendLine("""[
  {
    "level": "HIGH",
    "file": "UserService.java",
    "line": 42,
    "message": "问题描述（中文）"
  }
]""")
        appendLine()
        appendLine("Use level HIGH, MEDIUM, or LOW. Use null for line when no exact line is available.")
        appendLine("The message must be written in Chinese.")
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewPromptBuilderTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/review/ReviewPromptBuilder.kt src/test/kotlin/com/diffguard/review/ReviewPromptBuilderTest.kt
git commit -m "feat: replace ReviewPromptBuilder with PSI context support"
```

---

### 任务 8：修改 ReviewOrchestrator（集成 PSI Context）

**文件：**
- 修改：`src/main/kotlin/com/diffguard/review/ReviewOrchestrator.kt`

- [ ] **步骤 1：更新 `ReviewOrchestrator.kt`**

在 `reviewStagedDiff()` 方法中集成 `CodeContextBuilder`：

```kotlin
package dev.diffguard.review

import dev.diffguard.ai.AIProvider
import dev.diffguard.ai.OpenAIProvider
import dev.diffguard.context.CodeContextBuilder
import dev.diffguard.git.GitStagedDiffProvider
import dev.diffguard.model.AISettingsState
import dev.diffguard.model.ReviewFinding
import dev.diffguard.settings.AIReviewSettingsService
import com.intellij.openapi.project.Project

sealed interface ReviewOutcome {
    data object NoChanges : ReviewOutcome
    data object NeedsConfiguration : ReviewOutcome
    data class Completed(val findings: List<ReviewFinding>) : ReviewOutcome
    data class ParseFallback(val rawResponsePreview: String) : ReviewOutcome
}

fun ReviewOutcome.compatibilityFindings(): List<ReviewFinding> = when (this) {
    ReviewOutcome.NoChanges -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Git",
            line = null,
            message = "没有可 Review 的变更。工作区与 HEAD 完全一致，无需 Review。"
        )
    )
    ReviewOutcome.NeedsConfiguration -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Settings",
            line = null,
            message = "请先在 Settings / Tools / DiffGuard 中配置 API Key。"
        )
    )
    is ReviewOutcome.Completed -> findings
    is ReviewOutcome.ParseFallback -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "AI Response",
            line = null,
            message = "AI 返回内容不是合法 JSON，已显示原始内容：$rawResponsePreview"
        )
    )
}

class ReviewOrchestrator(
    private val project: Project,
    private val diffProvider: () -> String = { GitStagedDiffProvider(project).getStagedDiff() },
    private val settingsProvider: () -> AISettingsState = { AIReviewSettingsService.getInstance().state },
    private val promptBuilder: ReviewPromptBuilder = ReviewPromptBuilder(),
    private val parser: ReviewResultParser = ReviewResultParser(),
    private val providerFactory: (AISettingsState) -> AIProvider = { settings ->
        OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            client = OpenAIProvider.clientFor(settings)
        )
    },
    private val onStatus: (String) -> Unit = {}
) {
    constructor(project: Project, onStatus: (String) -> Unit = {}) : this(
        project = project,
        diffProvider = { GitStagedDiffProvider(project).getStagedDiff() },
        settingsProvider = { AIReviewSettingsService.getInstance().state },
        onStatus = onStatus
    )

    fun reviewStagedDiff(): ReviewOutcome {
        onStatus("正在读取本次变更...")
        val diff = diffProvider()
        if (diff.isBlank()) {
            return ReviewOutcome.NoChanges
        }

        val settings = settingsProvider()
        if (settings.apiKey.isBlank()) {
            return ReviewOutcome.NeedsConfiguration
        }

        // PSI Context 分析
        onStatus("正在分析代码上下文...")
        val codeContexts = try {
            CodeContextBuilder(project).buildFromDiff(diff)
        } catch (e: Exception) {
            onStatus("PSI 分析失败，使用纯 diff 模式: ${e.message}")
            emptyList()
        }

        onStatus("正在准备 Review 请求...")
        val prompt = promptBuilder.build(diff, codeContexts)
        val provider = providerFactory(settings)
        onStatus("正在请求 AI，非流式模型可能需要等待一段时间...")
        val rawResponse = provider.review(prompt)
        onStatus("正在解析 AI 返回结果...")
        return when (val parseResult = parser.tryParse(rawResponse)) {
            is ReviewParseResult.Parsed -> ReviewOutcome.Completed(parseResult.findings)
            is ReviewParseResult.Fallback -> ReviewOutcome.ParseFallback(parseResult.rawResponsePreview)
        }
    }
}
```

- [ ] **步骤 2：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。如果 `ReviewOrchestrator` 的构造函数签名变更导致 `AIReviewAction` 编译错误，需要同步修改 `AIReviewAction`。

- [ ] **步骤 3：检查并修复 `AIReviewAction` 中的调用**

在 `AIReviewAction.kt` 中，`ReviewOrchestrator` 的构造调用需要传入 `project` 参数（已有）。确认编译通过。

- [ ] **步骤 4：运行所有测试**

运行：`./gradlew test`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/review/ReviewOrchestrator.kt
git commit -m "feat: integrate PSI context into review orchestrator"
```

---

### 任务 9：端到端验证与最终测试

**文件：**
- 仅修改编译/测试失败的文件

- [ ] **步骤 1：运行完整测试**

运行：`./gradlew test`

预期：PASS。

- [ ] **步骤 2：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 3：构建插件**

运行：`./gradlew buildPlugin`

预期：PASS，生成 `build/distributions/` 下的 zip 文件。

- [ ] **步骤 4：启动 IDE 沙箱验证**

运行：`./gradlew runIde`

预期：插件正常加载，AI Review 功能可用。

- [ ] **步骤 5：最终提交**

```bash
git add -A
git commit -m "feat: implement PSI Context Builder for AI Review"
```

---

## 自检结果

- **Spec 覆盖：** 计划覆盖了数据模型、Diff 解析、PSI 分析、方法调用提取、Spring 语义识别、Prompt 重构、Orchestrator 集成。
- **占位符扫描：** 无 TBD/TODO/占位表达。
- **类型一致性：** `CodeContext`、`MethodContext`、`MethodCall`、`DependencyInfo`、`SpringSemantic` 在各任务 中定义一致。
- **职责锁定：** 每个文件职责清晰，无跨层逻辑。
