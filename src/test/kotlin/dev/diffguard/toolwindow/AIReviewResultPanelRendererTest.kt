package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.text.JTextComponent

class AIReviewResultPanelRendererTest {
    private val renderer = AIReviewResultPanelRenderer()

    @Test
    fun `renderStatus shows title and original status text without editor pane`() {
        val component = renderer.renderStatus("正在请求 <AI> & 等待")

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("DiffGuard 进行中"), visibleText)
        assertTrue(visibleText.contains("正在请求 <AI> & 等待"), visibleText)
        assertTrue(visibleText.contains("读取变更 -> 分析上下文 -> 请求 AI -> 解析结果"), visibleText)
        assertFalse(visibleText.contains("下一步"), visibleText)
        assertFalse(componentsIn(component).any { it is JEditorPane })
    }

    @Test
    fun `renderStatus does not duplicate IDE progress controls inside the result panel`() {
        val component = renderer.renderStatus("正在请求 AI")

        assertFalse(componentsIn(component).any { it is javax.swing.JProgressBar })
        assertFalse(componentsIn(component).any { it.name?.startsWith("review-progress-step-") == true })
    }

    @Test
    fun `severity filter buttons use flat segmented styling instead of default button chrome`() {
        val component = renderer.renderFindings(
            listOf(ReviewFinding(level = "MEDIUM", file = "A.kt", line = 1, message = "中风险"))
        )

        val buttons = componentsIn(component).filterIsInstance<JButton>()
        assertEquals(listOf("All", "High", "Medium", "Low"), buttons.map { it.text })
        assertTrue(buttons.all { it.ui is BasicButtonUI })
        assertTrue(buttons.none { it.isBorderPainted })
        assertTrue(buttons.none { it.isFocusPainted })
    }

    @Test
    fun `renderFindings shows empty result message`() {
        val component = renderer.renderFindings(emptyList())

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("DiffGuard 完成"), visibleText)
        assertTrue(visibleText.contains("DiffGuard 不能替代测试和人工审查"), visibleText)
    }

    @Test
    fun `renderFindings shows summary counts and finding content`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(level = "high", file = "UserService.kt", line = 42, message = "可能空指针"),
                ReviewFinding(level = "Medium", file = "OrderDao.kt", line = 18, message = "SQL 拼接风险"),
                ReviewFinding(level = "low", file = "README.md", line = null, message = "可读性建议")
            )
        )

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("DiffGuard 完成"), visibleText)
        assertTrue(visibleText.contains("发现 3 个问题，其中 1 个 HIGH 需要优先处理。"), visibleText)
        assertTrue(visibleText.contains("HIGH 1"), visibleText)
        assertTrue(visibleText.contains("MEDIUM 1"), visibleText)
        assertTrue(visibleText.contains("LOW 1"), visibleText)
        assertFalse(visibleText.contains("HIGH 建议提交前修复，MEDIUM 建议检查，LOW 可按需处理。"), visibleText)
        assertTrue(visibleText.contains("UserService.kt:42"), visibleText)
        assertTrue(visibleText.contains("可能空指针"), visibleText)
        assertTrue(visibleText.contains("OrderDao.kt:18"), visibleText)
        assertTrue(visibleText.contains("SQL 拼接风险"), visibleText)
        assertTrue(visibleText.contains("README.md"), visibleText)
        assertTrue(visibleText.contains("可读性建议"), visibleText)
    }

    @Test
    fun `renderFindings shows metadata with severity filter buttons`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(
                    level = "HIGH",
                    file = "UserService.kt",
                    line = 42,
                    category = "bug",
                    confidence = "high",
                    evidence = "dto may be null",
                    message = "可能空指针"
                ),
                ReviewFinding(level = "LOW", file = "README.md", line = null, message = "可读性建议")
            )
        )

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("bug"), visibleText)
        assertTrue(visibleText.contains("confidence: high"), visibleText)
        assertTrue(visibleText.contains("dto may be null"), visibleText)
        assertTrue(visibleText.contains("UserService.kt:42"), visibleText)
        assertTrue(visibleText.contains("README.md"), visibleText)
        assertTrue(buttonTextsIn(component).contains("All"))
        assertFalse(buttonTextsIn(component).contains("HIGH"))
        assertTrue(buttonTextsIn(component).contains("High"))
        assertTrue(buttonTextsIn(component).contains("Medium"))
        assertTrue(buttonTextsIn(component).contains("Low"))
        assertFalse(buttonTextsIn(component).contains("复制摘要"))
        assertFalse(buttonTextsIn(component).contains("重新审查"))
    }

    @Test
    fun `severity filter buttons update visible findings`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(level = "HIGH", file = "High.kt", line = 1, message = "高风险"),
                ReviewFinding(level = "MEDIUM", file = "Medium.kt", line = 2, message = "中风险"),
                ReviewFinding(level = "LOW", file = "Low.kt", line = 3, message = "低风险")
            )
        )

        clickButton(component, "Medium")

        val visibleText = visibleTextIn(component)
        assertFalse(visibleText.contains("High.kt:1"), visibleText)
        assertTrue(visibleText.contains("Medium.kt:2"), visibleText)
        assertFalse(visibleText.contains("Low.kt:3"), visibleText)
        assertTrue(visibleText.contains("Showing Medium"), visibleText)
    }

    @Test
    fun `finding location uses no-wrap text so long paths do not collapse into a narrow column`() {
        val longLocation = "src/main/kotlin/dev/diffguard/review/ReviewPromptBuilder.kt:59"
        val component = renderer.renderFindings(
            listOf(ReviewFinding(level = "MEDIUM", file = longLocation.substringBeforeLast(":"), line = 59, message = "中风险"))
        )

        val location = componentsIn(component)
            .filterIsInstance<JTextArea>()
            .single { it.text == longLocation }

        assertFalse(location.lineWrap)
    }

    @Test
    fun `renderFindings keeps external finding text as plain visible text without editor pane`() {
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

        val labelText = labelTextsIn(component).joinToString(separator = "\n")
        listOf("<html>HIGH</html>", "User<Service>.kt", "<script>").forEach { externalFragment ->
            assertFalse(
                labelText.contains(externalFragment),
                "JLabel.text must not contain external input fragment: $externalFragment\n$labelText"
            )
        }
    }

    @Test
    fun `renderFindings orders findings by severity and keeps unknown order stable`() {
        val component = renderer.renderFindings(
            listOf(
                ReviewFinding(level = "LOW", file = "Low.kt", line = 1, message = "低风险"),
                ReviewFinding(level = "INFO", file = "Info.kt", line = 2, message = "提示"),
                ReviewFinding(level = "HIGH", file = "High.kt", line = 3, message = "高风险"),
                ReviewFinding(level = "medium", file = "Medium.kt", line = 4, message = "中风险"),
                ReviewFinding(level = "STYLE", file = "Style.kt", line = 5, message = "风格建议")
            )
        )

        val locationText = componentsIn(component)
            .filterIsInstance<JTextComponent>()
            .map { it.text }
            .filter { it.endsWith(".kt:1") || it.endsWith(".kt:2") || it.endsWith(".kt:3") || it.endsWith(".kt:4") || it.endsWith(".kt:5") }

        assertEquals(
            listOf("High.kt:3", "Medium.kt:4", "Low.kt:1", "Info.kt:2", "Style.kt:5"),
            locationText
        )
    }

    @Test
    fun `clicking finding location invokes selection callback`() {
        val finding = ReviewFinding(level = "HIGH", file = "UserService.kt", line = 42, message = "可能空指针")
        var selectedFinding: ReviewFinding? = null
        val component = AIReviewResultPanelRenderer(onFindingSelected = { selectedFinding = it })
            .renderFindings(listOf(finding))

        clickTextComponent(component, "UserService.kt:42")

        assertEquals(finding, selectedFinding)
    }

    private fun visibleTextIn(component: Component): String = componentsIn(component)
        .flatMap { current ->
            when (current) {
                is JLabel -> listOfNotNull(current.text)
                is JTextComponent -> listOf(current.text)
                else -> emptyList()
            }
        }
        .joinToString(separator = "\n")

    private fun labelTextsIn(component: Component): List<String> = componentsIn(component)
        .filterIsInstance<JLabel>()
        .mapNotNull { it.text }

    private fun clickTextComponent(component: Component, text: String) {
        val textComponent = componentsIn(component)
            .filterIsInstance<JTextComponent>()
            .single { it.text == text }
        val event = MouseEvent(
            textComponent,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            1,
            1,
            1,
            false
        )
        textComponent.mouseListeners.forEach { it.mouseClicked(event) }
    }

    private fun clickButton(component: Component, text: String) {
        componentsIn(component)
            .filterIsInstance<JButton>()
            .single { it.text == text }
            .doClick()
    }

    private fun buttonTextsIn(component: Component): List<String> =
        componentsIn(component)
            .filterIsInstance<JButton>()
            .map { it.text }

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
