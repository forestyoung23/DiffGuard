package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Font
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class ReviewUiStateRendererTest {
    private val renderer = AIReviewResultPanelRenderer()

    @Test
    fun `renders ready state with usage guidance`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.Ready))

        assertTrue(text.contains("DiffGuard"), text)
        assertTrue(text.contains("提交前审查当前代码变更"), text)
        assertTrue(text.contains("配置 API Key"), text)
    }

    @Test
    fun `renders needs configuration state with settings path`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.NeedsConfiguration()))

        assertTrue(text.contains("需要先配置 API Key"), text)
        assertTrue(text.contains("Settings / Tools / DiffGuard"), text)
    }

    @Test
    fun `renders no changes state as non-error guidance`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.NoChanges()))

        assertTrue(text.contains("没有可 Review 的变更"), text)
        assertTrue(text.contains("当前 Review 范围为空"), text)
        assertTrue(text.contains("请先勾选需要审查的文件"), text)
    }

    @Test
    fun `renders reviewing state with progress message`() {
        val text = visibleTextIn(renderer.render(ReviewUiState.Reviewing("正在请求 AI，非流式模型可能需要等待一段时间...")))

        assertTrue(text.contains("DiffGuard 进行中"), text)
        assertTrue(text.contains("正在请求 AI，非流式模型可能需要等待一段时间..."), text)
        assertTrue(text.contains("读取变更 -> 分析上下文 -> 请求 AI -> 解析结果"), text)
        assertTrue(!text.contains("请稍候，Review 完成后会自动显示结果。"), text)
        assertTrue(!text.contains("请保持当前窗口打开，非流式模型可能需要等待一段时间。"), text)
    }

    @Test
    fun `renders failed state with next step`() {
        val text = visibleTextIn(
            renderer.render(
                ReviewUiState.Failed(
                    title = "DiffGuard 请求失败",
                    detail = "HTTP 401 unauthorized",
                    nextStep = "请检查 API Key 是否正确。"
                )
            )
        )

        assertTrue(text.contains("DiffGuard 请求失败"), text)
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

        assertTrue(text.contains("DiffGuard 完成"), text)
        assertTrue(text.contains("发现 2 个问题，其中 1 个 HIGH 需要优先处理。"), text)
        assertTrue(!text.contains("HIGH 建议提交前修复，MEDIUM 建议检查，LOW 可按需处理。"), text)
    }

    @Test
    fun `renders review text with ide label font instead of monospace log font`() {
        val component = renderer.render(
            ReviewUiState.Completed(
                listOf(ReviewFinding("LOW", "A.kt", 1, "低风险"))
            )
        )

        val textFonts = componentsIn(component)
            .filterIsInstance<JTextComponent>()
            .map { it.font.family }

        assertTrue(textFonts.isNotEmpty(), "Expected text components in rendered review result")
        assertTrue(textFonts.none { it == Font.MONOSPACED }, textFonts.joinToString())
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
