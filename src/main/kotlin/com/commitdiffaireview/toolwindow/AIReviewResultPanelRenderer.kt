package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AIReviewResultPanelRenderer {
    fun render(state: ReviewUiState): JComponent = when (state) {
        ReviewUiState.Ready -> renderInfoState(
            title = "AI Review",
            detail = "提交前审查当前代码变更，帮助发现潜在 bug、安全风险和可读性问题。",
            nextStep = "配置 API Key 后，在 Commit/VCS 菜单或 Changes View 中点击 AI Review。"
        )
        is ReviewUiState.NeedsConfiguration -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.NoChanges -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.Reviewing -> renderInfoState(
            title = "AI Review 进行中",
            detail = state.message,
            nextStep = "请保持当前窗口打开，非流式模型可能需要等待一段时间。"
        )
        is ReviewUiState.Completed -> renderFindings(state.findings)
        is ReviewUiState.Failed -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.ParseFallback -> renderParseFallback(state)
    }

    fun renderStatus(message: String): JComponent = render(ReviewUiState.Reviewing(message))

    fun renderFindings(findings: List<ReviewFinding>): JComponent = rootPanel().apply {
        if (findings.isEmpty()) {
            add(
                cardPanel().apply {
                    add(titleLabel("AI Review 完成"))
                    add(Box.createVerticalStrut(6))
                    add(bodyText("AI Review 不能替代测试和人工审查，但当前 diff 没有发现明显风险。"))
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
            cardPanel().apply {
                add(titleLabel(title))
                add(Box.createVerticalStrut(6))
                add(bodyText(detail))
                add(Box.createVerticalStrut(10))
                add(sectionLabel("下一步"))
                add(Box.createVerticalStrut(4))
                add(bodyText(nextStep))
            }
        )
    }

    private fun renderParseFallback(state: ReviewUiState.ParseFallback): JComponent = rootPanel().apply {
        add(
            cardPanel().apply {
                add(titleLabel(state.title))
                add(Box.createVerticalStrut(6))
                add(bodyText(state.detail))
                add(Box.createVerticalStrut(10))
                add(sectionLabel("原始返回预览"))
                add(Box.createVerticalStrut(4))
                add(bodyText(state.rawResponsePreview))
                add(Box.createVerticalStrut(10))
                add(sectionLabel("下一步"))
                add(Box.createVerticalStrut(4))
                add(bodyText(state.nextStep))
            }
        )
    }

    private fun rootPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(14, 14)
    }

    private fun cardPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(12, 14)
        )
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun summaryCard(findings: List<ReviewFinding>): JPanel = cardPanel().apply {
        add(titleLabel("AI Review 完成"))
        add(Box.createVerticalStrut(6))
        add(bodyText(summaryText(findings)))
        add(Box.createVerticalStrut(8))
        add(bodyText("HIGH：建议提交前修复。MEDIUM：建议检查。LOW：可按需处理。"))
        add(Box.createVerticalStrut(10))
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                background = UIUtil.getPanelBackground()
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(severityBadge("HIGH ${findings.countLevel("HIGH")}", "HIGH"))
                add(severityBadge("MEDIUM ${findings.countLevel("MEDIUM")}", "MEDIUM"))
                add(severityBadge("LOW ${findings.countLevel("LOW")}", "LOW"))
            }
        )
    }

    private fun findingCard(finding: ReviewFinding): JPanel = cardPanel().apply {
        add(
            JPanel(BorderLayout(8, 0)).apply {
                background = UIUtil.getPanelBackground()
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(severityBadge(finding.level, finding.level), BorderLayout.WEST)
                add(locationLabel(locationText(finding)), BorderLayout.CENTER)
            }
        )
        add(Box.createVerticalStrut(8))
        add(bodyText(finding.message))
    }

    private fun titleLabel(text: String): JBLabel = JBLabel(text).apply {
        font = font.deriveFont(Font.BOLD)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun sectionLabel(text: String): JBLabel = JBLabel(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        foreground = UIUtil.getContextHelpForeground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun locationLabel(text: String): JBTextArea = plainText(text).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        foreground = UIUtil.getContextHelpForeground()
        isOpaque = false
    }

    private fun bodyText(text: String): JBTextArea = plainText(text).apply {
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun severityBadge(text: String, level: String): JBTextArea = plainText(text).apply {
        font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        foreground = severityForeground(level)
        background = severityBackground(level)
        isOpaque = true
        border = JBUI.Borders.empty(3, 8)
    }

    private fun plainText(text: String): JBTextArea = JBTextArea(text).apply {
        isEditable = false
        isFocusable = true
        border = JBUI.Borders.empty()
    }

    private fun locationText(finding: ReviewFinding): String = if (finding.line != null) {
        "${finding.file}:${finding.line}"
    } else {
        finding.file
    }

    private fun summaryText(findings: List<ReviewFinding>): String {
        val highCount = findings.countLevel("HIGH")
        return if (highCount > 0) {
            "发现 ${findings.size} 个问题，其中 $highCount 个 HIGH 需要优先处理。"
        } else {
            "发现 ${findings.size} 个问题，建议按严重级别逐项检查。"
        }
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
        "HIGH" -> JBColor(Color(0xF6D4D4), Color(0x4A2A2A))
        "MEDIUM" -> JBColor(Color(0xF8E3B8), Color(0x4A3820))
        "LOW" -> JBColor(Color(0xD7E6F7), Color(0x24384F))
        else -> JBColor(Color(0xE5E7EB), Color(0x3A3D42))
    }
}
