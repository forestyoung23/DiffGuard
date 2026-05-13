package com.commitdiffaireview.action

import com.commitdiffaireview.review.ReviewOrchestrator
import com.commitdiffaireview.review.ReviewOutcome
import com.commitdiffaireview.toolwindow.AIReviewToolWindowService
import com.commitdiffaireview.toolwindow.ReviewErrorPresenter
import com.commitdiffaireview.toolwindow.ReviewUiState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class AIReviewAction : AnAction("AI Review") {
    private val errorPresenter = ReviewErrorPresenter()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindowService = project.service<AIReviewToolWindowService>()

        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
        if (!toolWindowService.tryStartReview()) {
            toolWindowService.showState(ReviewUiState.Reviewing("AI Review 已在进行中，请等待当前任务完成。"))
            return
        }

        toolWindowService.showState(ReviewUiState.Reviewing("正在读取本次变更..."))
        ReviewTask(project, toolWindowService).queue()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun ReviewOutcome.toUiState(): ReviewUiState = when (this) {
        ReviewOutcome.NoChanges -> ReviewUiState.NoChanges()
        ReviewOutcome.NeedsConfiguration -> ReviewUiState.NeedsConfiguration()
        is ReviewOutcome.Completed -> ReviewUiState.Completed(findings)
        is ReviewOutcome.ParseFallback -> ReviewUiState.ParseFallback(rawResponsePreview = rawResponsePreview)
    }

    private inner class ReviewTask(
        private val taskProject: Project,
        private val toolWindowService: AIReviewToolWindowService
    ) : Task.Backgroundable(taskProject, "AI Review staged changes", false) {
        private var outcome: ReviewOutcome? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            outcome = ReviewOrchestrator(
                project = taskProject,
                onStatus = { status ->
                    indicator.text = status
                    showReviewingStatus(taskProject, toolWindowService, status)
                }
            ).reviewStagedDiff()
        }

        override fun onSuccess() {
            outcome?.let { toolWindowService.showState(it.toUiState()) }
        }

        override fun onThrowable(error: Throwable) {
            toolWindowService.showState(errorPresenter.present(error))
        }

        override fun onCancel() {
            toolWindowService.showState(
                ReviewUiState.Failed(
                    title = "AI Review 已取消",
                    detail = "当前审查任务已取消。",
                    nextStep = "如需重新审查，请再次点击 AI Review。"
                )
            )
        }

        override fun onFinished() {
            toolWindowService.finishReview()
        }
    }

    private fun showReviewingStatus(
        project: Project,
        toolWindowService: AIReviewToolWindowService,
        status: String
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                toolWindowService.showState(ReviewUiState.Reviewing(status))
            }
        }
    }

    private companion object {
        const val TOOL_WINDOW_ID = "AI Review"
    }
}
