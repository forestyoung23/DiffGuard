package com.commitdiffaireview.toolwindow

import com.commitdiffaireview.model.ReviewFinding
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class AIReviewToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = AIReviewToolWindowView()
        project.service<AIReviewToolWindowService>().view = view

        val content = toolWindow.contentManager.factory.createContent(view.component, "Results", false)
        toolWindow.contentManager.addContent(content)
    }
}

class AIReviewToolWindowView {
    private val tableModel = object : DefaultTableModel(arrayOf("Level", "File", "Line", "Message"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    private val table = JBTable(tableModel)
    val component: JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun showStatus(message: String) {
        tableModel.setRowCount(0)
        tableModel.addRow(arrayOf("INFO", "", "", message))
    }

    fun showFindings(findings: List<ReviewFinding>) {
        tableModel.setRowCount(0)
        findings.forEach { finding ->
            tableModel.addRow(
                arrayOf(
                    finding.level,
                    finding.file,
                    finding.line?.toString().orEmpty(),
                    finding.message
                )
            )
        }
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
