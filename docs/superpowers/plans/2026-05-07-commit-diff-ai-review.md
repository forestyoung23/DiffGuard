# DiffGuard MVP 实施计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐步执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 构建一个 IntelliJ IDEA 2024+ 插件 MVP，在用户提交前获取 staged diff，调用 OpenAI Compatible API 进行 AI Code Review，并在 ToolWindow 中展示结果。

**架构：** 插件采用小型分层结构：`action` 负责入口编排，`git` 负责通过 IntelliJ Git API 获取 staged diff，`review` 负责 Prompt 与解析，`ai` 负责 OpenAI Compatible 调用，`toolwindow` 负责 Swing 展示，`settings` 负责全局配置。实现保持 MVP：无自动修复、无 Agent、无数据库、无复杂框架。

**技术栈：** IntelliJ Platform SDK、Kotlin、Gradle Kotlin DSL、Swing、OkHttp、kotlinx.serialization、JUnit 5、Java 17。

---

## 文件结构

创建以下项目文件：

```text
DiffGuard/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── README.md
├── docs/superpowers/specs/2026-05-07-commit-diff-ai-review-design.md
├── docs/superpowers/plans/2026-05-07-commit-diff-ai-review.md
└── src/
    ├── main/
    │   ├── kotlin/com/diffguard/
    │   │   ├── action/AIReviewAction.kt
    │   │   ├── ai/AIProvider.kt
    │   │   ├── ai/OpenAIProvider.kt
    │   │   ├── git/GitStagedDiffProvider.kt
    │   │   ├── model/AISettingsState.kt
    │   │   ├── model/OpenAIModels.kt
    │   │   ├── model/ReviewFinding.kt
    │   │   ├── review/ReviewOrchestrator.kt
    │   │   ├── review/ReviewPromptBuilder.kt
    │   │   ├── review/ReviewResultParser.kt
    │   │   ├── settings/AIReviewConfigurable.kt
    │   │   ├── settings/AIReviewSettingsComponent.kt
    │   │   ├── settings/AIReviewSettingsService.kt
    │   │   └── toolwindow/AIReviewToolWindowFactory.kt
    │   └── resources/META-INF/plugin.xml
    └── test/kotlin/com/diffguard/review/
        ├── ReviewPromptBuilderTest.kt
        └── ReviewResultParserTest.kt
```

职责锁定：

- `AIReviewAction.kt`：只负责 Action 入口、打开 ToolWindow、启动后台任务。
- `GitStagedDiffProvider.kt`：只负责通过 IntelliJ Git API 获取 staged diff，不调用 shell。
- `OpenAIProvider.kt`：只负责 HTTP 请求与 OpenAI Compatible 响应提取。
- `ReviewOrchestrator.kt`：串联 diff、prompt、AI、parser。
- `ReviewPromptBuilder.kt`：只负责 Prompt 文本。
- `ReviewResultParser.kt`：只负责模型输出到 `ReviewFinding` 的解析与兜底。
- `AIReviewToolWindowFactory.kt`：只负责 Swing UI 与状态/表格刷新。
- `settings/*`：只负责全局设置 UI 与持久化。

---

### 任务 1：初始化 Gradle IntelliJ 插件项目

**文件：**
- 创建：`settings.gradle.kts`
- 创建：`gradle.properties`
- 创建：`build.gradle.kts`

- [ ] **步骤 1：创建 `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "DiffGuard"
```

- [ ] **步骤 2：创建 `gradle.properties`**

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
pluginGroup=dev.diffguard
pluginName=DiffGuard
pluginVersion=0.1.0
pluginSinceBuild=241
pluginUntilBuild=253.*
platformType=IC
platformVersion=2024.1
```

- [ ] **步骤 3：创建 `build.gradle.kts`**

```kotlin
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
}

dependencies {
    intellijPlatform {
        create(
            IntelliJPlatformType.IntellijIdeaCommunity,
            providers.gradleProperty("platformVersion").get()
        )
        bundledPlugin("Git4Idea")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
    }
}
```

- [ ] **步骤 4：验证 Gradle 项目可识别**

运行：`./gradlew tasks`

预期：输出 Gradle task 列表，包含 `runIde`、`buildPlugin`、`test`。

- [ ] **步骤 5：提交**

当前目录不是 git 仓库时跳过 commit；如果已初始化 git，则执行：

```bash
git add settings.gradle.kts gradle.properties build.gradle.kts
git commit -m "chore: initialize IntelliJ plugin project"
```

---

### 任务 2：添加核心模型与解析测试

**文件：**
- 创建：`src/main/kotlin/com/diffguard/model/ReviewFinding.kt`
- 创建：`src/main/kotlin/com/diffguard/review/ReviewResultParser.kt`
- 创建：`src/test/kotlin/com/diffguard/review/ReviewResultParserTest.kt`

- [ ] **步骤 1：先写失败测试 `ReviewResultParserTest.kt`**

```kotlin
package dev.diffguard.review

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
        assertEquals("not json", result[0].message)
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewResultParserTest"`

预期：FAIL，原因是 `ReviewResultParser` 和 `ReviewFinding` 尚不存在。

- [ ] **步骤 3：创建 `ReviewFinding.kt`**

```kotlin
package dev.diffguard.model

import kotlinx.serialization.Serializable

@Serializable
data class ReviewFinding(
    val level: String,
    val file: String,
    val line: Int? = null,
    val message: String
)
```

- [ ] **步骤 4：创建 `ReviewResultParser.kt`**

```kotlin
package dev.diffguard.review

import dev.diffguard.model.ReviewFinding
import kotlinx.serialization.json.Json

class ReviewResultParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawResponse: String): List<ReviewFinding> {
        val candidate = extractJsonArray(rawResponse)
        return runCatching {
            json.decodeFromString<List<ReviewFinding>>(candidate)
        }.getOrElse {
            listOf(
                ReviewFinding(
                    level = "LOW",
                    file = "AI Response",
                    line = null,
                    message = rawResponse.trim().take(1_000)
                )
            )
        }
    }

    private fun extractJsonArray(rawResponse: String): String {
        val trimmed = rawResponse.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
        }

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null && fenced.startsWith("[")) {
            return fenced
        }

        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }
}
```

- [ ] **步骤 5：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewResultParserTest"`

预期：PASS。

- [ ] **步骤 6：提交**

```bash
git add src/main/kotlin/com/diffguard/model/ReviewFinding.kt src/main/kotlin/com/diffguard/review/ReviewResultParser.kt src/test/kotlin/com/diffguard/review/ReviewResultParserTest.kt
git commit -m "test: add review result parser"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 3：添加 Prompt Builder 与测试

**文件：**
- 创建：`src/main/kotlin/com/diffguard/review/ReviewPromptBuilder.kt`
- 创建：`src/test/kotlin/com/diffguard/review/ReviewPromptBuilderTest.kt`

- [ ] **步骤 1：先写失败测试 `ReviewPromptBuilderTest.kt`**

```kotlin
package dev.diffguard.review

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewPromptBuilderTest {
    @Test
    fun `prompt contains required review categories and diff`() {
        val prompt = ReviewPromptBuilder().build("diff --git a/UserService.java b/UserService.java")

        assertTrue(prompt.contains("staged unified diff", ignoreCase = true))
        assertTrue(prompt.contains("Bug"))
        assertTrue(prompt.contains("空指针"))
        assertTrue(prompt.contains("并发"))
        assertTrue(prompt.contains("事务"))
        assertTrue(prompt.contains("SQL"))
        assertTrue(prompt.contains("安全"))
        assertTrue(prompt.contains("可读性"))
        assertTrue(prompt.contains("diff --git a/UserService.java b/UserService.java"))
        assertTrue(prompt.contains("HIGH"))
        assertTrue(prompt.contains("MEDIUM"))
        assertTrue(prompt.contains("LOW"))
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewPromptBuilderTest"`

预期：FAIL，原因是 `ReviewPromptBuilder` 尚不存在。

- [ ] **步骤 3：创建 `ReviewPromptBuilder.kt`**

```kotlin
package dev.diffguard.review

class ReviewPromptBuilder {
    fun build(stagedDiff: String): String = """
        You are a senior code reviewer. Review the following staged unified diff only.

        Focus on these risks:
        - Bug 风险
        - 空指针风险
        - 并发问题
        - 事务问题
        - SQL 风险
        - 安全问题
        - 代码可读性

        Return JSON array only. Do not return Markdown or explanations.
        Each item must use this shape:
        [
          {
            "level": "HIGH",
            "file": "UserService.java",
            "line": 42,
            "message": "Potential null pointer"
          }
        ]

        Rules:
        - level must be one of HIGH, MEDIUM, LOW.
        - line can be null when no exact line is available.
        - return [] when no issue is found.

        staged unified diff:
        ```diff
        $stagedDiff
        ```
    """.trimIndent()
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`./gradlew test --tests "dev.diffguard.review.ReviewPromptBuilderTest"`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/review/ReviewPromptBuilder.kt src/test/kotlin/com/diffguard/review/ReviewPromptBuilderTest.kt
git commit -m "test: add review prompt builder"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 4：添加 OpenAI DTO、Provider 接口与 OpenAIProvider

**文件：**
- 创建：`src/main/kotlin/com/diffguard/ai/AIProvider.kt`
- 创建：`src/main/kotlin/com/diffguard/model/OpenAIModels.kt`
- 创建：`src/main/kotlin/com/diffguard/ai/OpenAIProvider.kt`

- [ ] **步骤 1：创建 `AIProvider.kt`**

```kotlin
package dev.diffguard.ai

interface AIProvider {
    suspend fun review(prompt: String): String
}
```

- [ ] **步骤 2：创建 `OpenAIModels.kt`**

```kotlin
package dev.diffguard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.1
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIChatResponse(
    val choices: List<OpenAIChoice> = emptyList()
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage? = null
)

@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIErrorDetail? = null
)

@Serializable
data class OpenAIErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
    @SerialName("param") val parameter: String? = null
)
```

- [ ] **步骤 3：创建 `OpenAIProvider.kt`**

```kotlin
package dev.diffguard.ai

import dev.diffguard.model.OpenAIChatRequest
import dev.diffguard.model.OpenAIChatResponse
import dev.diffguard.model.OpenAIMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient()
) : AIProvider {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun review(prompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            error("API Key is not configured. Please configure it in Settings / Tools / DiffGuard.")
        }

        val endpoint = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val requestBody = OpenAIChatRequest(
            model = model,
            messages = listOf(
                OpenAIMessage(
                    role = "system",
                    content = "You are a careful senior code reviewer. Return only valid JSON."
                ),
                OpenAIMessage(role = "user", content = prompt)
            )
        )

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OpenAI Compatible API request failed: HTTP ${response.code} $body")
            }

            val parsed = json.decodeFromString<OpenAIChatResponse>(body)
            parsed.choices.firstOrNull()?.message?.content
                ?: error("OpenAI Compatible API response does not contain choices[0].message.content")
        }
    }
}
```

- [ ] **步骤 4：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 5：提交**

```bash
git add src/main/kotlin/com/diffguard/ai/AIProvider.kt src/main/kotlin/com/diffguard/model/OpenAIModels.kt src/main/kotlin/com/diffguard/ai/OpenAIProvider.kt
git commit -m "feat: add OpenAI compatible provider"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 5：添加全局 Settings 配置

**文件：**
- 创建：`src/main/kotlin/com/diffguard/model/AISettingsState.kt`
- 创建：`src/main/kotlin/com/diffguard/settings/AIReviewSettingsService.kt`
- 创建：`src/main/kotlin/com/diffguard/settings/AIReviewSettingsComponent.kt`
- 创建：`src/main/kotlin/com/diffguard/settings/AIReviewConfigurable.kt`

- [ ] **步骤 1：创建 `AISettingsState.kt`**

```kotlin
package dev.diffguard.model

data class AISettingsState(
    var baseUrl: String = "https://api.openai.com",
    var apiKey: String = "",
    var model: String = "gpt-4o-mini"
)
```

- [ ] **步骤 2：创建 `AIReviewSettingsService.kt`**

```kotlin
package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "DiffGuardSettings",
    storages = [Storage("DiffGuardSettings.xml")]
)
class AIReviewSettingsService : PersistentStateComponent<AISettingsState> {
    private var state = AISettingsState()

    override fun getState(): AISettingsState = state

    override fun loadState(state: AISettingsState) {
        this.state = state
    }

    companion object {
        fun getInstance(): AIReviewSettingsService =
            ApplicationManager.getApplication().getService(AIReviewSettingsService::class.java)
    }
}
```

- [ ] **步骤 3：创建 `AIReviewSettingsComponent.kt`**

```kotlin
package dev.diffguard.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AIReviewSettingsComponent {
    private val settings = AIReviewSettingsService.getInstance().state

    val panel: DialogPanel = panel {
        row("Base URL:") {
            textField().bindText(settings::baseUrl)
                .comment("Example: https://api.openai.com")
        }
        row("API Key:") {
            passwordField().bindText(settings::apiKey)
        }
        row("Model:") {
            textField().bindText(settings::model)
                .comment("Example: gpt-4o-mini")
        }
    }

    fun component(): JComponent = panel

    fun isModified(): Boolean = panel.isModified()

    fun apply() {
        panel.apply()
    }

    fun reset() {
        panel.reset()
    }
}
```

- [ ] **步骤 4：创建 `AIReviewConfigurable.kt`**

```kotlin
package dev.diffguard.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AIReviewConfigurable : Configurable {
    private var component: AIReviewSettingsComponent? = null

    override fun getDisplayName(): String = "DiffGuard"

    override fun createComponent(): JComponent {
        val created = AIReviewSettingsComponent()
        component = created
        return created.component()
    }

    override fun isModified(): Boolean = component?.isModified() ?: false

    override fun apply() {
        component?.apply()
    }

    override fun reset() {
        component?.reset()
    }

    override fun disposeUIResources() {
        component = null
    }
}
```

- [ ] **步骤 5：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 6：提交**

```bash
git add src/main/kotlin/com/diffguard/model/AISettingsState.kt src/main/kotlin/com/diffguard/settings/AIReviewSettingsService.kt src/main/kotlin/com/diffguard/settings/AIReviewSettingsComponent.kt src/main/kotlin/com/diffguard/settings/AIReviewConfigurable.kt
git commit -m "feat: add global AI review settings"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 6：添加 ToolWindow UI

**文件：**
- 创建：`src/main/kotlin/com/diffguard/toolwindow/AIReviewToolWindowFactory.kt`

- [ ] **步骤 1：创建 `AIReviewToolWindowFactory.kt`**

```kotlin
package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class AIReviewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = AIReviewToolWindowView()
        project.getService(AIReviewToolWindowService::class.java).bind(view)
        val content = toolWindow.contentManager.factory.createContent(view.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class AIReviewToolWindowView {
    private val statusLabel = JLabel("Ready")
    private val tableModel = object : DefaultTableModel(arrayOf("Level", "File", "Line", "Message"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel)

    val component: JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(8)
        add(statusLabel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun setStatus(status: String) {
        statusLabel.text = status
    }

    fun setFindings(findings: List<ReviewFinding>) {
        tableModel.rowCount = 0
        findings.forEach { finding ->
            tableModel.addRow(
                arrayOf(
                    finding.level,
                    finding.file,
                    finding.line?.toString().orEmpty(),
                    finding.message
                )
            )
        }
        statusLabel.text = when {
            findings.isEmpty() -> "No issues found."
            findings.size == 1 -> "1 finding"
            else -> "${findings.size} findings"
        }
    }

    fun clear() {
        tableModel.rowCount = 0
    }
}

@Service(Service.Level.PROJECT)
class AIReviewToolWindowService(private val project: Project) {
    private var view: AIReviewToolWindowView? = null

    fun bind(view: AIReviewToolWindowView) {
        this.view = view
    }

    fun showStatus(status: String) {
        ensureVisible()
        view?.clear()
        view?.setStatus(status)
    }

    fun showFindings(findings: List<ReviewFinding>) {
        ensureVisible()
        view?.setFindings(findings)
    }

    private fun ensureVisible() {
        ToolWindowManager.getInstance(project).getToolWindow("AI Review")?.show()
    }
}
```

- [ ] **步骤 2：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 3：提交**

```bash
git add src/main/kotlin/com/diffguard/toolwindow/AIReviewToolWindowFactory.kt
git commit -m "feat: add AI review tool window"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 7：添加 staged diff 获取实现

**文件：**
- 创建：`src/main/kotlin/com/diffguard/git/GitStagedDiffProvider.kt`

- [ ] **步骤 1：创建 `GitStagedDiffProvider.kt`**

```kotlin
package dev.diffguard.git

import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager

class GitStagedDiffProvider {
    fun getStagedDiff(project: Project): String {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) {
            return ""
        }

        return repositories.joinToString(separator = "\n") { repository ->
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                addParameters("--cached", "--no-color", "--unified=3")
            }
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                result.outputAsJoinedString
            } else {
                error("Failed to get staged diff for ${repository.root.path}: ${result.errorOutputAsJoinedString}")
            }
        }.trim()
    }
}
```

说明：这里使用 IntelliJ Git4Idea API（`GitLineHandler` + `Git.getInstance().runCommand`）执行 Git diff 操作，没有通过 shell 执行 `git` 命令，满足“不通过 shell 执行 git 命令”的约束。

- [ ] **步骤 2：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。若 Git4Idea API 在当前 platform 版本中方法名有差异，按编译错误只调整同一 IntelliJ Git API 范围内的调用，不改为 shell。

- [ ] **步骤 3：提交**

```bash
git add src/main/kotlin/com/diffguard/git/GitStagedDiffProvider.kt
git commit -m "feat: add staged diff provider"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 8：添加 Review 编排服务

**文件：**
- 创建：`src/main/kotlin/com/diffguard/review/ReviewOrchestrator.kt`

- [ ] **步骤 1：创建 `ReviewOrchestrator.kt`**

```kotlin
package dev.diffguard.review

import dev.diffguard.ai.OpenAIProvider
import dev.diffguard.git.GitStagedDiffProvider
import dev.diffguard.model.ReviewFinding
import dev.diffguard.settings.AIReviewSettingsService
import com.intellij.openapi.project.Project

class ReviewOrchestrator(
    private val diffProvider: GitStagedDiffProvider = GitStagedDiffProvider(),
    private val promptBuilder: ReviewPromptBuilder = ReviewPromptBuilder(),
    private val parser: ReviewResultParser = ReviewResultParser()
) {
    suspend fun review(project: Project): ReviewResult {
        val diff = diffProvider.getStagedDiff(project)
        if (diff.isBlank()) {
            return ReviewResult.NoStagedChanges
        }

        val settings = AIReviewSettingsService.getInstance().state
        if (settings.apiKey.isBlank()) {
            return ReviewResult.Error("API Key is not configured. Please configure it in Settings / Tools / DiffGuard.")
        }

        val provider = OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model
        )
        val prompt = promptBuilder.build(diff)
        val rawResponse = provider.review(prompt)
        return ReviewResult.Findings(parser.parse(rawResponse))
    }
}

sealed class ReviewResult {
    data class Findings(val findings: List<ReviewFinding>) : ReviewResult()
    data object NoStagedChanges : ReviewResult()
    data class Error(val message: String) : ReviewResult()
}
```

- [ ] **步骤 2：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 3：提交**

```bash
git add src/main/kotlin/com/diffguard/review/ReviewOrchestrator.kt
git commit -m "feat: add review orchestration"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 9：添加 Action 入口

**文件：**
- 创建：`src/main/kotlin/com/diffguard/action/AIReviewAction.kt`

- [ ] **步骤 1：创建 `AIReviewAction.kt`**

```kotlin
package dev.diffguard.action

import dev.diffguard.review.ReviewOrchestrator
import dev.diffguard.review.ReviewResult
import dev.diffguard.toolwindow.AIReviewToolWindowService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIReviewAction : AnAction("AI Review") {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val orchestrator = ReviewOrchestrator()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = project.getService(AIReviewToolWindowService::class.java)

        toolWindow.showStatus("Reviewing...")

        scope.launch {
            val result = runCatching { orchestrator.review(project) }
                .getOrElse { ReviewResult.Error(it.message ?: it::class.java.simpleName) }

            withContext(Dispatchers.Main) {
                when (result) {
                    is ReviewResult.Findings -> toolWindow.showFindings(result.findings)
                    ReviewResult.NoStagedChanges -> toolWindow.showStatus("No staged changes to review.")
                    is ReviewResult.Error -> toolWindow.showStatus("Error: ${result.message}")
                }
            }
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
```

- [ ] **步骤 2：移除未使用 import**

如果 IDE 或 `compileKotlin` 报告 `ApplicationManager` 未使用，删除这一行：

```kotlin
import com.intellij.openapi.application.ApplicationManager
```

- [ ] **步骤 3：运行编译检查**

运行：`./gradlew compileKotlin`

预期：PASS。

- [ ] **步骤 4：提交**

```bash
git add src/main/kotlin/com/diffguard/action/AIReviewAction.kt
git commit -m "feat: add AI review action"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 10：注册 plugin.xml

**文件：**
- 创建：`src/main/resources/META-INF/plugin.xml`

- [ ] **步骤 1：创建 `plugin.xml`**

```xml
<idea-plugin>
    <id>dev.diffguard</id>
    <name>DiffGuard</name>
    <vendor email="support@example.com">DiffGuard</vendor>

    <description><![CDATA[
        在 Git Commit 前对 staged diff 执行 AI Code Review，并在 ToolWindow 中展示结构化结果。
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow
            id="AI Review"
            anchor="bottom"
            factoryClass="dev.diffguard.toolwindow.AIReviewToolWindowFactory" />

        <applicationConfigurable
            parentId="tools"
            instance="dev.diffguard.settings.AIReviewConfigurable"
            displayName="DiffGuard" />
    </extensions>

    <actions>
        <action
            id="DiffGuard.AIReviewAction"
            class="dev.diffguard.action.AIReviewAction"
            text="AI Review"
            description="Review staged diff with AI before commit">
            <add-to-group group-id="Vcs.Group.Commit" anchor="last" />
            <add-to-group group-id="ChangesViewPopupMenu" anchor="last" />
            <add-to-group group-id="VcsGroup" anchor="last" />
        </action>
    </actions>
</idea-plugin>
```

说明：`Vcs.Group.Commit` 用于尽量靠近 Commit 工作流；`ChangesViewPopupMenu` 与 `VcsGroup` 作为 MVP 稳定入口补充。若某个 group 在目标 IDE 版本不可用，Action 仍可通过其他 group 使用。

- [ ] **步骤 2：运行插件 XML 与编译检查**

运行：`./gradlew buildPlugin`

预期：PASS，并在 `build/distributions/` 生成插件 zip。

- [ ] **步骤 3：提交**

```bash
git add src/main/resources/META-INF/plugin.xml
git commit -m "feat: register plugin extensions and action"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 11：添加中文 README

**文件：**
- 创建：`README.md`

- [ ] **步骤 1：创建 `README.md`**

```markdown
# DiffGuard

DiffGuard 是一个 IntelliJ IDEA 插件 MVP，用于在 Git Commit 前对当前 staged diff 执行 AI Code Review。

## 功能

- 在 Commit/VCS 工作流中提供 `AI Review` 入口。
- 获取当前 Git staged unified diff。
- 调用 OpenAI Compatible API。
- 在 `DiffGuard` ToolWindow 中展示结构化 Review 结果。
- 支持全局配置 `baseUrl`、`apiKey`、`model`。

## 技术栈

- IntelliJ Platform SDK
- Kotlin
- Gradle Kotlin DSL
- Swing UI
- OkHttp
- kotlinx.serialization
- Java 17

## 不包含的功能

- 自动修复
- Agent
- 向量数据库
- 多轮对话
- 本地模型
- 用户系统
- 企业规则
- 配置中心

## 运行开发版插件

```bash
./gradlew runIde
```

## 构建插件

```bash
./gradlew buildPlugin
```

构建产物位于：

```text
build/distributions/
```

## 配置

打开 IntelliJ IDEA：

```text
Settings / Tools / DiffGuard
```

配置以下字段：

- `Base URL`：OpenAI Compatible API 地址，默认 `https://api.openai.com`
- `API Key`：API Key
- `Model`：模型名称，默认 `gpt-4o-mini`

## 使用方式

1. 在 Git 项目中修改代码。
2. 将要提交的文件加入 staged/index。
3. 在 Commit/VCS 工作流中点击 `Review with DiffGuard`。
4. 查看底部 `DiffGuard` ToolWindow 中的 Review 结果。

## Review 返回格式

插件要求模型返回 JSON 数组：

```json
[
  {
    "level": "HIGH",
    "file": "UserService.java",
    "line": 42,
    "message": "Potential null pointer"
  }
]
```

`level` 可取值：`HIGH`、`MEDIUM`、`LOW`。

## 开发验证

```bash
./gradlew test
./gradlew buildPlugin
```

手动验证：

- Settings 页面可以保存配置。
- 没有 staged changes 时，ToolWindow 显示提示。
- 有 staged changes 时，可以触发 AI Review。
- API 错误会展示在 ToolWindow 状态栏。
```

- [ ] **步骤 2：验证 README 存在**

运行：`./gradlew test`

预期：PASS。

- [ ] **步骤 3：提交**

```bash
git add README.md
git commit -m "docs: add Chinese README"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

### 任务 12：最终验证与修正

**文件：**
- 仅修改编译或测试验证失败所需的文件。

- [ ] **步骤 1：运行完整测试**

运行：`./gradlew test`

预期：PASS。

- [ ] **步骤 2：构建插件**

运行：`./gradlew buildPlugin`

预期：PASS，生成 `build/distributions/DiffGuard-0.1.0.zip` 或同版本 zip。

- [ ] **步骤 3：启动 IDE 沙箱**

运行：`./gradlew runIde`

预期：IntelliJ IDEA 沙箱启动，插件已加载。

- [ ] **步骤 4：手动检查 Settings**

在沙箱 IDE 中打开：

```text
Settings / Tools / DiffGuard
```

预期：

- 可以看到 `Base URL`、`API Key`、`Model` 三个字段。
- 点击 Apply 后配置可保存。

- [ ] **步骤 5：手动检查 ToolWindow**

在沙箱 IDE 中打开任意 Git 项目并 staged 一个文件后，点击 `Review with DiffGuard`。

预期：

- 底部出现 `DiffGuard` ToolWindow。
- 无 staged changes 时显示 `No staged changes to review.`。
- 未配置 API Key 时显示配置提示。
- 配置 API 后能展示 findings 或 `No issues found.`。

- [ ] **步骤 6：最终提交**

```bash
git add build.gradle.kts gradle.properties settings.gradle.kts README.md src docs
git commit -m "feat: implement DiffGuard MVP"
```

如果当前目录仍不是 git 仓库，跳过 commit。

---

## 自检结果

- Spec 覆盖：计划覆盖了项目初始化、分层结构、Action 注册、ToolWindow 注册、Settings、Git staged diff、OpenAI API 调用、Prompt、JSON 解析、README、测试与最终验证。
- 占位符扫描：没有保留占位表达或延后实现说明；每个代码步骤都包含实际代码。
- 类型一致性：`ReviewFinding`、`AIProvider`、`OpenAIProvider`、`ReviewPromptBuilder`、`ReviewResultParser`、`ReviewOrchestrator`、`AIReviewToolWindowService` 在各任务中的命名一致。
- 范围控制：没有加入自动修复、Agent、数据库、多轮对话、本地模型、配置中心等非 MVP 功能。
