package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
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
import java.util.concurrent.atomic.AtomicBoolean
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

    init {
        showState(ReviewUiState.Ready)
    }

    fun showState(state: ReviewUiState) {
        showContent(renderer.render(state))
    }

    fun showStatus(message: String) {
        showState(ReviewUiState.Reviewing(message))
    }

    fun showFindings(findings: List<ReviewFinding>) {
        showState(ReviewUiState.Completed(findings))
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
    private val reviewRunning = AtomicBoolean(false)

    fun tryStartReview(): Boolean = reviewRunning.compareAndSet(false, true)

    fun finishReview() {
        reviewRunning.set(false)
    }

    fun showState(state: ReviewUiState) {
        view?.showState(state)
    }

    fun showStatus(message: String) {
        view?.showStatus(message)
    }

    fun showFindings(findings: List<ReviewFinding>) {
        view?.showFindings(findings)
    }
}
