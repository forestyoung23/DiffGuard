package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AIReviewResultPanelRenderer(
    private val onFindingSelected: ((ReviewFinding) -> Unit)? = null
) {
    fun render(state: ReviewUiState): JComponent = when (state) {
        ReviewUiState.Ready -> renderInfoState(
            title = "DiffGuard",
            detail = "提交前审查当前代码变更，帮助发现潜在 bug、安全风险和可读性问题。",
            nextStep = "配置 API Key 后，在 Commit/VCS 菜单或 Changes View 中点击 Review with DiffGuard。"
        )
        is ReviewUiState.NeedsConfiguration -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.NoChanges -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.Reviewing -> renderInfoState(
            title = "DiffGuard 进行中",
            detail = state.message,
            nextStep = "请稍候，Review 完成后会自动显示结果。"
        )
        is ReviewUiState.Completed -> renderFindings(state.findings)
        is ReviewUiState.Failed -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.ParseFallback -> renderParseFallback(state)
    }

    fun renderStatus(message: String): JComponent = render(ReviewUiState.Reviewing(message))

    fun renderFindings(findings: List<ReviewFinding>): JComponent = rootPanel().apply {
        if (findings.isEmpty()) {
            add(
                surfacePanel().apply {
                    add(titleLabel("DiffGuard 完成"))
                    add(Box.createVerticalStrut(6))
                    add(bodyText("DiffGuard 不能替代测试和人工审查，但当前 diff 没有发现明显风险。"))
                }
            )
            return@apply
        }

        add(summaryCard(findings))
        add(Box.createVerticalStrut(10))
        orderedFindings(findings).forEachIndexed { index, finding ->
            if (index > 0) {
                add(Box.createVerticalStrut(10))
            }
            add(findingCard(finding))
        }
    }

    private fun renderInfoState(title: String, detail: String, nextStep: String): JComponent = rootPanel().apply {
        add(
            surfacePanel().apply {
                add(titleLabel(title))
                add(Box.createVerticalStrut(6))
                add(bodyText(detail))
                add(Box.createVerticalStrut(12))
                add(metaLabel("下一步"))
                add(Box.createVerticalStrut(5))
                add(bodyText(nextStep))
            }
        )
    }

    private fun renderParseFallback(state: ReviewUiState.ParseFallback): JComponent = rootPanel().apply {
        add(
            surfacePanel().apply {
                add(titleLabel(state.title))
                add(Box.createVerticalStrut(6))
                add(bodyText(state.detail))
                add(Box.createVerticalStrut(12))
                add(metaLabel("原始返回预览"))
                add(Box.createVerticalStrut(5))
                add(bodyText(state.rawResponsePreview))
                add(Box.createVerticalStrut(12))
                add(metaLabel("下一步"))
                add(Box.createVerticalStrut(5))
                add(bodyText(state.nextStep))
            }
        )
    }

    private fun rootPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(10, 12, 16, 12)
    }

    private fun surfacePanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getListBackground()
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(10, 12)
        )
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun summaryCard(findings: List<ReviewFinding>): JPanel = surfacePanel().apply {
        add(titleLabel("DiffGuard 完成"))
        add(Box.createVerticalStrut(5))
        add(bodyText(summaryText(findings)))
        add(Box.createVerticalStrut(10))
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(severityBadge("HIGH ${findings.countLevel("HIGH")}", "HIGH"))
                add(severityBadge("MEDIUM ${findings.countLevel("MEDIUM")}", "MEDIUM"))
                add(severityBadge("LOW ${findings.countLevel("LOW")}", "LOW"))
            }
        )
        add(Box.createVerticalStrut(8))
        add(hintText("HIGH 建议提交前修复，MEDIUM 建议检查，LOW 可按需处理。"))
    }

    private fun findingCard(finding: ReviewFinding): JPanel = surfacePanel().apply {
        add(
            JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(severityBadge(finding.level, finding.level), BorderLayout.WEST)
                add(locationLabel(locationText(finding)), BorderLayout.CENTER)
            }
        )
        add(Box.createVerticalStrut(9))
        metadataText(finding)?.let {
            add(hintText(it))
            add(Box.createVerticalStrut(6))
        }
        add(bodyText(finding.message))
        enableFindingClick(this, finding)
    }

    private fun titleLabel(text: String): JBLabel = JBLabel(text).apply {
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D + 1f)
        foreground = UIUtil.getLabelForeground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun metaLabel(text: String): JBLabel = JBLabel(text).apply {
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D - 1f)
        foreground = UIUtil.getContextHelpForeground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun locationLabel(text: String): JBTextArea = plainText(text).apply {
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getContextHelpForeground()
        lineWrap = true
        wrapStyleWord = false
    }

    private fun bodyText(text: String): JBTextArea = plainText(text).apply {
        lineWrap = true
        wrapStyleWord = true
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun hintText(text: String): JBTextArea = bodyText(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
    }

    private fun severityBadge(text: String, level: String): JBTextArea = plainText(text).apply {
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D - 1f)
        foreground = severityForeground(level)
        background = severityBackground(level)
        isOpaque = true
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(severityBorder(level), 1),
            JBUI.Borders.empty(2, 7)
        )
        isFocusable = false
    }

    private fun plainText(text: String): JBTextArea = JBTextArea(text).apply {
        isEditable = false
        isFocusable = true
        isOpaque = false
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty()
    }

    private fun locationText(finding: ReviewFinding): String = if (finding.line != null) {
        "${finding.file}:${finding.line}"
    } else {
        finding.file
    }

    private fun enableFindingClick(component: Component, finding: ReviewFinding) {
        val callback = onFindingSelected ?: return
        if (component is JComponent) {
            component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            component.toolTipText = "Open ${locationText(finding)}"
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    callback(finding)
                }
            })
        }
        if (component is Container) {
            component.components.forEach { enableFindingClick(it, finding) }
        }
    }

    private fun summaryText(findings: List<ReviewFinding>): String {
        val highCount = findings.countLevel("HIGH")
        return if (highCount > 0) {
            "发现 ${findings.size} 个问题，其中 $highCount 个 HIGH 需要优先处理。"
        } else {
            "发现 ${findings.size} 个问题，建议按严重级别逐项检查。"
        }
    }

    private fun metadataText(finding: ReviewFinding): String? {
        val parts = listOfNotNull(
            finding.category?.takeIf { it.isNotBlank() },
            finding.confidence?.takeIf { it.isNotBlank() }?.let { "confidence: $it" },
            finding.evidence?.takeIf { it.isNotBlank() }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }

    private fun List<ReviewFinding>.countLevel(level: String): Int =
        count { it.level.equals(level, ignoreCase = true) }

    private fun orderedFindings(findings: List<ReviewFinding>): List<ReviewFinding> =
        findings.withIndex()
            .sortedWith(
                compareBy<IndexedValue<ReviewFinding>> { levelRank(it.value.level) }
                    .thenBy { it.index }
            )
            .map { it.value }

    private fun levelRank(level: String): Int = when (level.uppercase()) {
        "HIGH" -> 0
        "MEDIUM" -> 1
        "LOW" -> 2
        else -> 3
    }

    private fun severityForeground(level: String): Color = when (level.uppercase()) {
        "HIGH" -> JBColor(Color(0x8A1F1F), Color(0xF2B8B5))
        "MEDIUM" -> JBColor(Color(0x7A4A00), Color(0xF4C983))
        "LOW" -> JBColor(Color(0x24507A), Color(0xAFC7E8))
        else -> JBColor(Color(0x4B5563), Color(0xB8BEC8))
    }

    private fun severityBackground(level: String): Color = when (level.uppercase()) {
        "HIGH" -> JBColor(Color(0xFFF1F1), Color(0x3A2628))
        "MEDIUM" -> JBColor(Color(0xFFF6E3), Color(0x3A3020))
        "LOW" -> JBColor(Color(0xEEF5FF), Color(0x243246))
        else -> JBColor(Color(0xF3F4F6), Color(0x313336))
    }

    private fun severityBorder(level: String): Color = when (level.uppercase()) {
        "HIGH" -> JBColor(Color(0xE6B8B8), Color(0x6B3E42))
        "MEDIUM" -> JBColor(Color(0xE5C58A), Color(0x6A5430))
        "LOW" -> JBColor(Color(0xB9D3F0), Color(0x3E5775))
        else -> JBColor(Color(0xD1D5DB), Color(0x4B4D52))
    }
}
