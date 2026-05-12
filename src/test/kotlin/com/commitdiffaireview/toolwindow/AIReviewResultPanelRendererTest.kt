package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class AIReviewResultPanelRendererTest {
    private val renderer = AIReviewResultPanelRenderer()

    @Test
    fun `renderStatus shows title and original status text without editor pane`() {
        val component = renderer.renderStatus("正在请求 <AI> & 等待")

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("AI Review"), visibleText)
        assertTrue(visibleText.contains("正在请求 <AI> & 等待"), visibleText)
        assertFalse(componentsIn(component).any { it is JEditorPane })
    }

    @Test
    fun `renderFindings shows empty result message`() {
        val component = renderer.renderFindings(emptyList())

        val visibleText = visibleTextIn(component)
        assertTrue(visibleText.contains("AI Review 完成"), visibleText)
        assertTrue(visibleText.contains("AI Review 不能替代测试和人工审查"), visibleText)
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
        assertTrue(visibleText.contains("AI Review 完成"), visibleText)
        assertTrue(visibleText.contains("发现 3 个问题，其中 1 个 HIGH 需要优先处理。"), visibleText)
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

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
