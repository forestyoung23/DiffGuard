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
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicButtonUI

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
        is ReviewUiState.Reviewing -> renderReviewingState(state.message)
        is ReviewUiState.Completed -> renderFindings(state.findings)
        is ReviewUiState.Failed -> renderInfoState(state.title, state.detail, state.nextStep)
        is ReviewUiState.ParseFallback -> renderParseFallback(state)
    }

    fun renderStatus(message: String): JComponent = render(ReviewUiState.Reviewing(message))

    fun renderFindings(findings: List<ReviewFinding>): JComponent {
        val root = rootPanel()
        if (findings.isEmpty()) {
            root.add(
                surfacePanel().apply {
                    add(titleLabel("DiffGuard 完成"))
                    add(Box.createVerticalStrut(6))
                    add(bodyText("DiffGuard 不能替代测试和人工审查，但当前 diff 没有发现明显风险。"))
                }
            )
            return root
        }

        var selectedLevel: String? = null
        fun rebuild() {
            root.removeAll()
            root.add(filterToolbar(findings, selectedLevel) { nextLevel ->
                selectedLevel = nextLevel
                rebuild()
            })
            root.add(Box.createVerticalStrut(8))
            root.add(summaryCard(findings, selectedLevel))
            root.add(Box.createVerticalStrut(8))

            val visibleFindings = orderedFindings(findings).filterByLevel(selectedLevel)
            visibleFindings.forEachIndexed { index, finding ->
                if (index > 0) {
                    root.add(Box.createVerticalStrut(6))
                }
                root.add(findingCard(finding))
            }
            if (visibleFindings.isEmpty()) {
                root.add(
                    compactSurfacePanel(BorderLayout()).apply {
                        add(bodyText("当前筛选没有问题。"), BorderLayout.CENTER)
                    }
                )
            }
            root.revalidate()
            root.repaint()
        }

        rebuild()
        return root
    }

    private fun List<ReviewFinding>.filterByLevel(level: String?): List<ReviewFinding> =
        if (level == null) {
            this
        } else {
            filter { it.level.equals(level, ignoreCase = true) }
        }

    private fun filterToolbar(
        findings: List<ReviewFinding>,
        selectedLevel: String?,
        onFilterSelected: (String?) -> Unit
    ): JPanel = JPanel(BorderLayout(10, 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(
            hintText(if (selectedLevel == null) "${findings.size} findings" else "Showing ${selectedLevel.displayLevel()}"),
            BorderLayout.WEST
        )
        add(filterSegment(selectedLevel, onFilterSelected), BorderLayout.EAST)
    }

    private fun filterSegment(selectedLevel: String?, onFilterSelected: (String?) -> Unit): JPanel =
        JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.customLine(JBColor.border(), 1)
                    add(filterButton("All", selectedLevel == null) { onFilterSelected(null) })
                    add(filterButton("High", selectedLevel.equals("HIGH", ignoreCase = true)) { onFilterSelected("HIGH") })
                    add(filterButton("Medium", selectedLevel.equals("MEDIUM", ignoreCase = true)) { onFilterSelected("MEDIUM") })
                    add(filterButton("Low", selectedLevel.equals("LOW", ignoreCase = true)) { onFilterSelected("LOW") })
                }
            )
        }

    private fun filterButton(text: String, selected: Boolean, onClick: () -> Unit): JButton =
        JButton(text).apply {
            isFocusable = false
            isOpaque = true
            isBorderPainted = false
            isContentAreaFilled = true
            isFocusPainted = false
            horizontalAlignment = SwingConstants.CENTER
            ui = BasicButtonUI()
            margin = Insets(0, 0, 0, 0)
            preferredSize = Dimension(62, 28)
            font = UIUtil.getLabelFont()
            foreground = if (selected) {
                JBColor(Color(0x1F3F63), Color(0xD5E8FF))
            } else {
                UIUtil.getContextHelpForeground()
            }
            background = if (selected) {
                JBColor(Color(0xE8F2FF), Color(0x3B4A5C))
            } else {
                UIUtil.getPanelBackground()
            }
            addActionListener { onClick() }
        }

    private fun String.displayLevel(): String =
        lowercase().replaceFirstChar { it.uppercase() }

    private fun renderReviewingState(message: String): JComponent = rootPanel().apply {
        add(
            compactSurfacePanel(BorderLayout(10, 0)).apply {
                add(statusDot(), BorderLayout.WEST)
                add(
                    JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                        add(titleLabel("DiffGuard 进行中"))
                        add(Box.createVerticalStrut(3))
                        add(bodyText(message))
                        add(Box.createVerticalStrut(5))
                        add(hintText(REVIEW_PROGRESS_TEXT))
                    },
                    BorderLayout.CENTER
                )
            }
        )
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
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun surfacePanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getListBackground()
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(10, 12)
        )
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun compactSurfacePanel(layoutManager: java.awt.LayoutManager): JPanel = JPanel(layoutManager).apply {
        background = UIUtil.getListBackground()
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8, 10)
        )
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun summaryCard(findings: List<ReviewFinding>, selectedLevel: String?): JPanel = compactSurfacePanel(BorderLayout(12, 0)).apply {
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(titleLabel("DiffGuard 完成"))
                add(Box.createVerticalStrut(3))
                add(bodyText(summaryText(findings.filterByLevel(selectedLevel))))
            },
            BorderLayout.CENTER
        )
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                add(severityBadge("HIGH ${findings.countLevel("HIGH")}", "HIGH"))
                add(severityBadge("MEDIUM ${findings.countLevel("MEDIUM")}", "MEDIUM"))
                add(severityBadge("LOW ${findings.countLevel("LOW")}", "LOW"))
            },
            BorderLayout.EAST
        )
    }

    private fun findingCard(finding: ReviewFinding): JPanel = compactSurfacePanel(BorderLayout(8, 0)).apply {
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(
                    JPanel(BorderLayout(8, 0)).apply {
                        isOpaque = false
                        alignmentX = Component.LEFT_ALIGNMENT
                        add(severityBadge(finding.level, finding.level), BorderLayout.WEST)
                        add(locationLabel(locationText(finding)), BorderLayout.CENTER)
                    }
                )
                metadataText(finding)?.let {
                    add(Box.createVerticalStrut(5))
                    add(hintText(it))
                }
                add(Box.createVerticalStrut(6))
                add(bodyText(finding.message))
            },
            BorderLayout.CENTER
        )
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
        lineWrap = false
        wrapStyleWord = false
    }

    private fun bodyText(text: String): JBTextArea = plainText(text).apply {
        lineWrap = true
        wrapStyleWord = true
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
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

    private fun statusDot(): JBLabel = JBLabel("\u25CF").apply {
        foreground = JBColor(Color(0x4C86C6), Color(0x7AA7D8))
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D)
        border = JBUI.Borders.emptyTop(2)
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

    private companion object {
        const val REVIEW_PROGRESS_TEXT = "读取变更 -> 分析上下文 -> 请求 AI -> 解析结果"
    }
}
