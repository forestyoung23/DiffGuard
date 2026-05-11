package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
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
                ReviewFinding(
                    level = "LOW",
                    file = "Old.kt",
                    line = 1,
                    message = "旧问题"
                )
            )
        )

        view.showStatus("正在请求 AI Review")

        val visibleText = visibleTextIn(view)
        assertTrue(visibleText.contains("AI Review"), visibleText)
        assertTrue(visibleText.contains("正在请求 AI Review"), visibleText)
        assertFalse(visibleText.contains("Old.kt:1"), visibleText)
        assertFalse(visibleText.contains("旧问题"), visibleText)
    }

    @Test
    fun `showFindings replaces previous status with review report`() {
        val view = AIReviewToolWindowView()
        view.showStatus("正在请求旧状态")

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

        val visibleText = visibleTextIn(view)
        assertTrue(visibleText.contains("AI Review 结果"), visibleText)
        assertTrue(visibleText.contains("发现 1 个问题"), visibleText)
        assertTrue(visibleText.contains("HIGH 1"), visibleText)
        assertTrue(visibleText.contains("UserService.kt:42"), visibleText)
        assertTrue(visibleText.contains("可能空指针"), visibleText)
        assertFalse(visibleText.contains("正在请求旧状态"), visibleText)
    }

    @Test
    fun `showFindings scrolls content back to top`() {
        val view = AIReviewToolWindowView()
        val scrollPane = scrollPaneIn(view)
        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum

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

        assertEquals(scrollPane.verticalScrollBar.minimum, scrollPane.verticalScrollBar.value)
        assertEquals(0, scrollPane.viewport.viewPosition.y)
    }

    private fun visibleTextIn(view: AIReviewToolWindowView): String =
        componentsIn(view.component)
            .mapNotNull { component ->
                when (component) {
                    is JLabel -> component.text
                    is JTextComponent -> component.text
                    else -> null
                }
            }
            .joinToString("\n")

    private fun scrollPaneIn(view: AIReviewToolWindowView): JBScrollPane =
        componentsIn(view.component).filterIsInstance<JBScrollPane>().single()

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
