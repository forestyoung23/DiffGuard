package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class ReviewUiStateRendererTest {
    private val renderer = AIReviewResultPanelRenderer()

    @Test
    fun `renders ready state with usage guidance`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.Ready))

        assertTrue(text.contains("AI Review"), text)
        assertTrue(text.contains("提交前审查当前代码变更"), text)
        assertTrue(text.contains("配置 API Key"), text)
    }

    @Test
    fun `renders needs configuration state with settings path`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.NeedsConfiguration()))

        assertTrue(text.contains("需要先配置 API Key"), text)
        assertTrue(text.contains("Settings / Tools / CommitDiffAIReview"), text)
    }

    @Test
    fun `renders no changes state as non-error guidance`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.NoChanges()))

        assertTrue(text.contains("没有可 Review 的变更"), text)
        assertTrue(text.contains("当前 diff 为空"), text)
    }

    @Test
    fun `renders reviewing state with progress message`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.Reviewing("正在读取本次变更...")))

        assertTrue(text.contains("AI Review 进行中"), text)
        assertTrue(text.contains("正在读取本次变更..."), text)
    }

    @Test
    fun `renders failed state with next step`() {
        val text = visibleTextIn(
            renderer.render(
                ReviewUiState.Failed(
                    title = "AI Review 请求失败",
                    detail = "HTTP 401 unauthorized",
                    nextStep = "请检查 API Key 是否正确。"
                )
            )
        )

        assertTrue(text.contains("AI Review 请求失败"), text)
        assertTrue(text.contains("HTTP 401 unauthorized"), text)
        assertTrue(text.contains("请检查 API Key 是否正确。"), text)
    }

    @Test
    fun `renders parse fallback state with raw response preview`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.ParseFallback(rawResponsePreview = "not json")))

        assertTrue(text.contains("AI 返回内容无法解析"), text)
        assertTrue(text.contains("not json"), text)
        assertTrue(text.contains("可以重试"), text)
    }

    @Test
    fun `renders completed state summary with severity guidance`() {
        val text = visibleTextIn(
            renderer.render(
                ReviewUiState.Completed(
                    listOf(
                        ReviewFinding("HIGH", "A.kt", 1, "高风险"),
                        ReviewFinding("MEDIUM", "B.kt", 2, "中风险")
                    )
                )
            )
        )

        assertTrue(text.contains("AI Review 完成"), text)
        assertTrue(text.contains("发现 2 个问题，其中 1 个 HIGH 需要优先处理。"), text)
        assertTrue(text.contains("HIGH：建议提交前修复"), text)
        assertTrue(text.contains("MEDIUM：建议检查"), text)
        assertTrue(text.contains("LOW：可按需处理"), text)
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

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
