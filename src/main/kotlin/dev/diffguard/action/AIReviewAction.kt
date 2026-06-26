package dev.diffguard.action

import dev.diffguard.ai.ReviewCancellationToken
import dev.diffguard.git.SelectedChangesDiffProvider
import dev.diffguard.review.ReviewOrchestrator
import dev.diffguard.review.ReviewOutcome
import dev.diffguard.toolwindow.AIReviewToolWindowService
import dev.diffguard.toolwindow.ReviewErrorPresenter
import dev.diffguard.toolwindow.ReviewUiState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.wm.ToolWindowManager

class AIReviewAction : AnAction("Review with DiffGuard") {
    private val errorPresenter = ReviewErrorPresenter()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindowService = project.service<AIReviewToolWindowService>()

        val reviewChanges = ReviewChangesResolver.resolve(event)
        if (reviewChanges.isEmpty()) {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
            toolWindowService.showState(ReviewUiState.NoChanges())
            return
        }

        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
        startReview(project, toolWindowService, reviewChanges)
    }

    private fun startReview(
        project: Project,
        toolWindowService: AIReviewToolWindowService,
        reviewChanges: List<Change>
    ) {
        if (!toolWindowService.tryStartReview()) {
            toolWindowService.showState(ReviewUiState.Reviewing("DiffGuard 已在进行中，请等待当前任务完成。"))
            return
        }

        toolWindowService.showState(ReviewUiState.Reviewing("正在读取本次变更..."))
        ReviewTask(project, toolWindowService, reviewChanges).queue()
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
        private val toolWindowService: AIReviewToolWindowService,
        private val selectedChanges: List<Change>
    ) : Task.Backgroundable(taskProject, "DiffGuard staged changes", true) {
        private var outcome: ReviewOutcome? = null
        private lateinit var cancellationToken: ReviewCancellationToken

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            cancellationToken = ReviewCancellationToken { indicator.isCanceled }

            val diffProvider = SelectedChangesDiffProvider(taskProject, selectedChanges)
            outcome = ReviewOrchestrator(
                project = taskProject,
                onStatus = { status ->
                    cancellationToken.throwIfCancellationRequested()
                    indicator.text = status
                    showReviewingStatus(taskProject, toolWindowService, status)
                },
                cancellationToken = cancellationToken,
                customDiffProvider = { diffProvider.getDiff() }
            ).reviewStagedDiff()
        }

        override fun onSuccess() {
            outcome?.let { toolWindowService.showState(it.toUiState()) }
        }

        override fun onThrowable(error: Throwable) {
            toolWindowService.showState(errorPresenter.present(error))
        }

        override fun onCancel() {
            if (::cancellationToken.isInitialized) {
                cancellationToken.cancel()
            }
            toolWindowService.showState(
                ReviewUiState.Failed(
                    title = "DiffGuard 已取消",
                    detail = "当前审查任务已取消。",
                    nextStep = "如需重新审查，请再次点击 Review with DiffGuard。"
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
        const val TOOL_WINDOW_ID = "DiffGuard"
    }
}
