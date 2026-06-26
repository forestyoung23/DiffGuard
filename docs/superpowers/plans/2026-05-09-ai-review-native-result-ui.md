# AI Review 原生结果页 UI 实现计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐步执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 将 AI Review 结果页从 `JEditorPane + HTML` 主展示层迁移为 IntelliJ IDEA 原生 Swing 组件面板。

**架构：** 新增 `AIReviewResultPanelRenderer`，把 status 和 `List<ReviewFinding>` 渲染为普通 Swing `JComponent`。`AIReviewToolWindowView` 只负责持有 `JBScrollPane`、替换当前内容并滚动到顶部，结构化 JSON 解析链路和 `ReviewFinding` 模型保持不变。

**技术栈：** Kotlin、IntelliJ Platform Swing (`JBScrollPane`, `JBLabel`, `JBTextArea`, `JBUI`, `UIUtil`)、JUnit 5、Gradle、Corretto 17。

---

## 执行约束

- 本计划只做展示层迁移，不修改 AI Provider、Prompt、Settings、JSON 返回格式、`ReviewFinding` 或 `ReviewResultParser`。
- 不增加源码跳转、复制、折叠、过滤、搜索等交互。
- 外部输入（`level`、`file`、`message`）必须作为普通文本展示，不放入 HTML label。
- 本仓库当前没有 `./gradlew`，测试命令统一使用系统 `gradle`。
- 本机执行测试时显式设置：
  ```bash
  JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home"
  ```
- 不创建 git commit，除非用户在执行阶段另行明确授权。

## 文件结构

- 创建：`src/main/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`
  - 负责把 status 和 findings 渲染为原生 Swing 组件。
  - 内部包含 summary card、badge、finding card、排序和统计逻辑。
- 创建：`src/test/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`
  - 验证 renderer 的可见文本、空结果、统计、排序、普通文本安全边界。
- 修改：`src/main/kotlin/com/diffguard/toolwindow/AIReviewToolWindowFactory.kt`
  - 移除 `JEditorPane` 和 `AIReviewResultHtmlRenderer` 主展示依赖。
  - 使用 `AIReviewResultPanelRenderer` + `JBScrollPane` + 内容容器替换展示内容。
- 修改：`src/test/kotlin/com/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`
  - 更新为原生组件结构测试。
  - 验证没有 `JBTable`，也没有 `JEditorPane` 作为主展示组件。
  - 验证每次 `showStatus` / `showFindings` 会替换旧内容。
- 删除：`src/main/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRenderer.kt`
  - 原 HTML renderer 不再被 ToolWindow 使用，删除避免保留废弃展示路径。
- 删除：`src/test/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRendererTest.kt`
  - 对应旧 HTML renderer 的测试随旧实现删除。

---

### 任务 1：新增原生 renderer 行为测试和实现

**文件：**
- 创建：`src/test/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`
- 创建：`src/main/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`

- [ ] **步骤 1：写失败测试**

创建 `src/test/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`：

```kotlin
package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class AIReviewResultPanelRendererTest {
    private val renderer = AIReviewResultPanelRenderer()

    @Test
    fun `renders status as native plain text components`() {
        val component = renderer.renderStatus("正在请求 <AI> & 等待")

        val visibleText = visibleTextIn(component)

        assertTrue(visibleText.contains("AI Review"), visibleText)
        assertTrue(visibleText.contains("正在请求 <AI> & 等待"), visibleText)
        assertFalse(componentsIn(component).any { it is JEditorPane })
    }

    @Test
    fun `renders empty findings panel`() {
        val component = renderer.renderFindings(emptyList())

        val visibleText = visibleTextIn(component)

        assertTrue(visibleText.contains("AI Review 结果"), visibleText)
        assertTrue(visibleText.contains("未发现明显问题"), visibleText)
    }

    @Test
    fun `renders summary counts and finding content`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(level = "high", file = "UserService.kt", line = 42, message = "可能空指针"),
                ReviewFinding(level = "Medium", file = "OrderDao.kt", line = 18, message = "SQL 拼接风险"),
                ReviewFinding(level = "low", file = "README.md", line = null, message = "可读性建议")
            )
        )

        val visibleText = visibleTextIn(component)

        assertTrue(visibleText.contains("AI Review 结果"), visibleText)
        assertTrue(visibleText.contains("发现 3 个问题"), visibleText)
        assertTrue(visibleText.contains("HIGH 1"), visibleText)
        assertTrue(visibleText.contains("MEDIUM 1"), visibleText)
        assertTrue(visibleText.contains("LOW 1"), visibleText)
        assertTrue(visibleText.contains("UserService.kt:42"), visibleText)
        assertTrue(visibleText.contains("可能空指针"), visibleText)
        assertTrue(visibleText.contains("OrderDao.kt:18"), visibleText)
        assertTrue(visibleText.contains("SQL 拼接风险"), visibleText)
        assertTrue(visibleText.contains("README.md"), visibleText)
        assertTrue(visibleText.contains("可读性建议"), visibleText)
    }

    @Test
    fun `keeps external finding text as plain visible text`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(
                    level = "<html>HIGH</html>",
                    file = "User<Service>.kt",
                    line = 7,
                    message = "不要执行 <script>alert(\"x\")</script> & 保持安全"
                )
            )
        )

        val visibleText = visibleTextIn(component)

        assertTrue(visibleText.contains("<html>HIGH</html>"), visibleText)
        assertTrue(visibleText.contains("User<Service>.kt:7"), visibleText)
        assertTrue(visibleText.contains("不要执行 <script>alert(\"x\")</script> & 保持安全"), visibleText)
        assertFalse(componentsIn(component).any { it is JEditorPane })
    }

    @Test
    fun `orders findings by severity while keeping unknown levels last and stable`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(level = "LOW", file = "Low.kt", line = 1, message = "低风险"),
                ReviewFinding(level = "INFO", file = "Info.kt", line = 2, message = "未知等级 1"),
                ReviewFinding(level = "HIGH", file = "High.kt", line = 3, message = "高风险"),
                ReviewFinding(level = "medium", file = "Medium.kt", line = 4, message = "中风险"),
                ReviewFinding(level = "STYLE", file = "Style.kt", line = 5, message = "未知等级 2")
            )
        )

        val visibleText = visibleTextIn(component)
        val highIndex = visibleText.indexOf("High.kt:3")
        val mediumIndex = visibleText.indexOf("Medium.kt:4")
        val lowIndex = visibleText.indexOf("Low.kt:1")
        val infoIndex = visibleText.indexOf("Info.kt:2")
        val styleIndex = visibleText.indexOf("Style.kt:5")

        assertTrue(highIndex >= 0, visibleText)
        assertTrue(mediumIndex > highIndex, visibleText)
        assertTrue(lowIndex > mediumIndex, visibleText)
        assertTrue(infoIndex > lowIndex, visibleText)
        assertTrue(styleIndex > infoIndex, visibleText)
    }

    private fun visibleTextIn(component: Component): String =
        componentsIn(component).mapNotNull { child ->
            when (child) {
                is JLabel -> child.text
                is JTextComponent -> child.text
                else -> null
            }
        }.joinToString(separator = "\n")

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
```

- [ ] **步骤 2：运行测试确认红灯**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.AIReviewResultPanelRendererTest"
```

预期：FAIL，原因是 `Unresolved reference 'AIReviewResultPanelRenderer'`。

- [ ] **步骤 3：写最小原生 renderer 实现**

创建 `src/main/kotlin/com/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`：

```kotlin
package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AIReviewResultPanelRenderer {
    fun renderStatus(message: String): JComponent = rootPanel().apply {
        add(statusCard(message))
        add(Box.createVerticalGlue())
    }

    fun renderFindings(findings: List<ReviewFinding>): JComponent = rootPanel().apply {
        if (findings.isEmpty()) {
            add(emptyCard())
        } else {
            add(summaryCard(findings))
            add(Box.createVerticalStrut(10))
            orderedFindings(findings).forEachIndexed { index, finding ->
                if (index > 0) {
                    add(Box.createVerticalStrut(8))
                }
                add(findingCard(finding))
            }
        }
        add(Box.createVerticalGlue())
    }

    private fun rootPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(14)
        background = UIUtil.getPanelBackground()
    }

    private fun statusCard(message: String): JComponent = cardPanel().apply {
        add(titleLabel("AI Review"))
        add(Box.createVerticalStrut(6))
        add(wrappingText(message))
        fillHorizontal()
    }

    private fun emptyCard(): JComponent = cardPanel().apply {
        add(titleLabel("AI Review 结果"))
        add(Box.createVerticalStrut(6))
        add(wrappingText("未发现明显问题。"))
        fillHorizontal()
    }

    private fun summaryCard(findings: List<ReviewFinding>): JComponent = cardPanel().apply {
        add(titleLabel("AI Review 结果"))
        add(Box.createVerticalStrut(6))
        add(wrappingText("发现 ${findings.size} 个问题"))
        add(Box.createVerticalStrut(8))
        add(
            badgeRow(
                listOf(
                    severityBadge("HIGH ${findings.countLevel("HIGH")}", "HIGH"),
                    severityBadge("MEDIUM ${findings.countLevel("MEDIUM")}", "MEDIUM"),
                    severityBadge("LOW ${findings.countLevel("LOW")}", "LOW")
                )
            )
        )
        fillHorizontal()
    }

    private fun findingCard(finding: ReviewFinding): JComponent = cardPanel().apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, severityColor(finding.level)),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIUtil.getBoundsColor()),
                JBUI.Borders.empty(10, 12)
            )
        )

        add(
            badgeRow(
                listOf(
                    severityBadge(finding.level.ifBlank { "UNKNOWN" }, finding.level),
                    locationText(locationText(finding))
                )
            )
        )
        add(Box.createVerticalStrut(6))
        add(wrappingText(finding.message))
        fillHorizontal()
    }

    private fun cardPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getBoundsColor()),
            JBUI.Borders.empty(12, 14)
        )
        background = UIUtil.getPanelBackground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun titleLabel(text: String): JBLabel = JBLabel(text).apply {
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun wrappingText(text: String): JBTextArea = JBTextArea(text).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = JBUI.Borders.empty()
        foreground = UIUtil.getLabelForeground()
        font = UIUtil.getLabelFont()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun locationText(text: String): JBTextArea = wrappingText(text).apply {
        lineWrap = false
        wrapStyleWord = false
        foreground = JBColor.GRAY
        font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
    }

    private fun severityBadge(text: String, level: String): JBTextArea = JBTextArea(text).apply {
        isEditable = false
        isFocusable = false
        lineWrap = false
        wrapStyleWord = false
        isOpaque = true
        foreground = severityColor(level)
        background = badgeBackground(level)
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D - 1f)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(severityColor(level)),
            JBUI.Borders.empty(2, 7)
        )
        maximumSize = preferredSize
    }

    private fun badgeRow(components: List<JComponent>): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        components.forEach { add(it) }
    }

    private fun JComponent.fillHorizontal() {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun locationText(finding: ReviewFinding): String =
        if (finding.line != null) {
            "${finding.file}:${finding.line}"
        } else {
            finding.file
        }

    private fun List<ReviewFinding>.countLevel(level: String): Int =
        count { normalizedLevel(it.level) == level }

    private fun orderedFindings(findings: List<ReviewFinding>): List<ReviewFinding> =
        findings.withIndex()
            .sortedWith(
                compareBy<IndexedValue<ReviewFinding>> { levelRank(it.value.level) }
                    .thenBy { it.index }
            )
            .map { it.value }

    private fun levelRank(level: String): Int = when (normalizedLevel(level)) {
        "HIGH" -> 0
        "MEDIUM" -> 1
        "LOW" -> 2
        else -> 3
    }

    private fun normalizedLevel(level: String): String = level.uppercase(Locale.ROOT)

    private fun severityColor(level: String): JBColor = when (normalizedLevel(level)) {
        "HIGH" -> JBColor(Color(0xB00020), Color(0xFF9B9B))
        "MEDIUM" -> JBColor(Color(0xB26A00), Color(0xF0C36A))
        "LOW" -> JBColor(Color(0x1E40AF), Color(0x8AB4F8))
        else -> JBColor.GRAY
    }

    private fun badgeBackground(level: String): JBColor = when (normalizedLevel(level)) {
        "HIGH" -> JBColor(Color(0xFDECEC), Color(0x3A2A2A))
        "MEDIUM" -> JBColor(Color(0xFFF4D6), Color(0x3A3220))
        "LOW" -> JBColor(Color(0xE7F0FF), Color(0x243247))
        else -> JBColor(UIUtil.getPanelBackground(), UIUtil.getPanelBackground())
    }
}
```

- [ ] **步骤 4：运行 renderer 测试确认绿灯**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.AIReviewResultPanelRendererTest"
```

预期：PASS，`AIReviewResultPanelRendererTest` 全部通过。

---

### 任务 2：改造 ToolWindow View 为原生结果容器

**文件：**
- 修改：`src/test/kotlin/com/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`
- 修改：`src/main/kotlin/com/diffguard/toolwindow/AIReviewToolWindowFactory.kt`

- [ ] **步骤 1：写失败测试替换旧 View 断言**

用以下内容替换 `src/test/kotlin/com/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`：

```kotlin
package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class AIReviewToolWindowViewTest {
    @Test
    fun `uses native scroll pane instead of table or html editor pane`() {
        val view = AIReviewToolWindowView()
        val components = componentsIn(view.component)

        assertEquals(1, components.filterIsInstance<JBScrollPane>().size)
        assertFalse(components.any { it is JBTable })
        assertFalse(components.any { it is JEditorPane })
    }

    @Test
    fun `showStatus replaces previous content with status panel`() {
        val view = AIReviewToolWindowView()
        view.showFindings(
            listOf(
                ReviewFinding(level = "HIGH", file = "Old.kt", line = 1, message = "旧问题")
            )
        )

        view.showStatus("正在请求 DiffGuard")

        val visibleText = visibleTextIn(view.component)
        assertTrue(visibleText.contains("AI Review"), visibleText)
        assertTrue(visibleText.contains("正在请求 DiffGuard"), visibleText)
        assertFalse(visibleText.contains("Old.kt:1"), visibleText)
        assertFalse(visibleText.contains("旧问题"), visibleText)
    }

    @Test
    fun `showFindings replaces previous status with review report`() {
        val view = AIReviewToolWindowView()
        view.showStatus("正在请求 DiffGuard")

        view.showFindings(
            listOf(
                ReviewFinding(
                    level = "HIGH",
                    file = "UserService.kt",
                    line = 42,
                    message = "可能空指针"
                )
            )
        )

        val visibleText = visibleTextIn(view.component)
        assertTrue(visibleText.contains("AI Review 结果"), visibleText)
        assertTrue(visibleText.contains("发现 1 个问题"), visibleText)
        assertTrue(visibleText.contains("HIGH 1"), visibleText)
        assertTrue(visibleText.contains("UserService.kt:42"), visibleText)
        assertTrue(visibleText.contains("可能空指针"), visibleText)
        assertFalse(visibleText.contains("正在请求 DiffGuard"), visibleText)
    }

    @Test
    fun `showFindings scrolls content back to top`() {
        val view = AIReviewToolWindowView()
        val scrollPane = scrollPaneIn(view)
        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum

        view.showFindings(
            listOf(
                ReviewFinding(level = "LOW", file = "Parser.kt", line = 12, message = "建议拆分长消息")
            )
        )

        assertEquals(scrollPane.verticalScrollBar.minimum, scrollPane.verticalScrollBar.value)
        assertEquals(0, scrollPane.viewport.viewPosition.y)
    }

    private fun scrollPaneIn(view: AIReviewToolWindowView): JBScrollPane =
        componentsIn(view.component).filterIsInstance<JBScrollPane>().single()

    private fun visibleTextIn(component: Component): String =
        componentsIn(component).mapNotNull { child ->
            when (child) {
                is JLabel -> child.text
                is JTextComponent -> child.text
                else -> null
            }
        }.joinToString(separator = "\n")

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
```

- [ ] **步骤 2：运行 View 测试确认红灯**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.AIReviewToolWindowViewTest"
```

预期：FAIL，当前实现仍包含 `JEditorPane`，且 `showStatus` / `showFindings` 通过 HTML 文档展示。

- [ ] **步骤 3：改造 ToolWindow View 实现**

用以下内容替换 `src/main/kotlin/com/diffguard/toolwindow/AIReviewToolWindowFactory.kt`：

```kotlin
package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel

class AIReviewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = AIReviewToolWindowView()
        project.service<AIReviewToolWindowService>().view = view

        val content = toolWindow.contentManager.factory.createContent(view.component, "Results", false)
        toolWindow.contentManager.addContent(content)
    }
}

class AIReviewToolWindowView {
    private val renderer = AIReviewResultPanelRenderer()
    private val contentPanel = JPanel(BorderLayout()).apply {
        background = UIUtil.getPanelBackground()
    }
    private val scrollPane = JBScrollPane(contentPanel).apply {
        border = JBUI.Borders.empty()
        viewport.background = UIUtil.getPanelBackground()
    }

    val component: JPanel = JPanel(BorderLayout()).apply {
        background = UIUtil.getPanelBackground()
        add(scrollPane, BorderLayout.CENTER)
    }

    fun showStatus(message: String) {
        showContent(renderer.renderStatus(message))
    }

    fun showFindings(findings: List<ReviewFinding>) {
        showContent(renderer.renderFindings(findings))
    }

    private fun showContent(content: JComponent) {
        contentPanel.removeAll()
        contentPanel.add(content, BorderLayout.NORTH)
        contentPanel.revalidate()
        contentPanel.repaint()
        scrollToTop()
    }

    private fun scrollToTop() {
        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.minimum
        scrollPane.horizontalScrollBar.value = scrollPane.horizontalScrollBar.minimum
        scrollPane.viewport.viewPosition = Point(0, 0)
    }
}

@Service(Service.Level.PROJECT)
class AIReviewToolWindowService {
    var view: AIReviewToolWindowView? = null

    fun showStatus(message: String) {
        view?.showStatus(message)
    }

    fun showFindings(findings: List<ReviewFinding>) {
        view?.showFindings(findings)
    }
}
```

- [ ] **步骤 4：运行 View 测试确认绿灯**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.AIReviewToolWindowViewTest"
```

预期：PASS，`AIReviewToolWindowViewTest` 全部通过。

- [ ] **步骤 5：运行 renderer + view 组合测试**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.AIReviewResultPanelRendererTest" --tests "dev.diffguard.toolwindow.AIReviewToolWindowViewTest"
```

预期：PASS，两组 ToolWindow UI 测试全部通过。

---

### 任务 3：删除旧 HTML renderer 展示路径

**文件：**
- 删除：`src/main/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRenderer.kt`
- 删除：`src/test/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRendererTest.kt`

- [ ] **步骤 1：确认旧 renderer 只剩自身和测试引用**

运行：

```bash
git -C "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" grep -n "AIReviewResultHtmlRenderer"
```

预期：只看到以下两个文件的引用：

```text
src/main/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRenderer.kt
src/test/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRendererTest.kt
```

- [ ] **步骤 2：删除旧 HTML renderer 和测试**

运行：

```bash
rm "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui/src/main/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRenderer.kt" "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui/src/test/kotlin/com/diffguard/toolwindow/AIReviewResultHtmlRendererTest.kt"
```

预期：两个旧 HTML renderer 文件被删除。

- [ ] **步骤 3：运行 ToolWindow 包测试确认没有遗留引用**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test --tests "dev.diffguard.toolwindow.*"
```

预期：PASS，`toolwindow` 包测试全部通过。

---

### 任务 4：全量验证和手动 UI 验收

**文件：**
- Verify only: no source files should be edited in this task unless verification reveals a failure.

- [ ] **步骤 1：运行完整测试套件**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" test
```

预期：PASS。若出现既有 IntelliJ bundled plugin descriptor 警告但测试 exit code 为 0，可记录为非阻塞环境警告，不在本任务修复。

- [ ] **步骤 2：构建插件包**

运行：

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle -p "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" buildPlugin
```

预期：PASS，插件包构建成功。

- [ ] **步骤 3：检查迁移后代码不再依赖 `JEditorPane` 展示结果**

运行：

```bash
git -C "/Users/forest/Work/Ai/DiffGuard/.worktrees/ai-review-markdown-result-ui" grep -n "JEditorPane\|AIReviewResultHtmlRenderer" -- src/main src/test
```

预期：没有 `AIReviewResultHtmlRenderer` 引用；`JEditorPane` 只允许出现在测试断言“组件树中不存在 JEditorPane”的 import / type check 中，不允许出现在生产代码中。

- [ ] **步骤 4：手动 UI 验收**

在 IDE sandbox 中触发一次 AI Review，验收以下内容：

```text
1. ToolWindow 结果区背景跟随 IDE 面板背景，不再出现大面积网页纯白底。
2. status 状态显示为原生面板文本。
3. 空结果显示 “未发现明显问题”。
4. 非空结果显示 “AI Review 结果”、问题总数和 HIGH / MEDIUM / LOW 统计 badge。
5. finding 以纵向卡片展示，卡片之间有间距。
6. 文件位置使用等宽字体，message 自动换行。
7. HIGH / MEDIUM / LOW 颜色低饱和，暗色主题下可读。
8. 连续运行两次 review 后，新结果替换旧结果，不追加旧内容。
9. 没有源码跳转、复制、折叠、过滤、搜索等本轮非目标交互。
```

若无法在当前环境打开 IDE sandbox，需要在最终汇报中明确说明“自动化测试和 buildPlugin 已验证，手动 UI 验收未执行”。

---

## 自查清单

- [ ] `AIReviewResultPanelRenderer` 输出 Swing `JComponent`，不是 HTML 字符串。
- [ ] `AIReviewToolWindowView` 不再创建 `JEditorPane`。
- [ ] `JBTable` 没有重新引入。
- [ ] status、empty、summary、finding card 都由原生组件组成。
- [ ] HIGH / MEDIUM / LOW 统计忽略大小写。
- [ ] 排序保持 HIGH → MEDIUM → LOW → unknown，unknown 内部保持输入顺序。
- [ ] 外部输入作为普通文本展示，不进入 HTML label。
- [ ] 每次 `showStatus` / `showFindings` 都替换旧内容。
- [ ] 每次展示后滚动到顶部。
- [ ] 完整 `test` 和 `buildPlugin` 通过，或明确记录阻塞失败。
