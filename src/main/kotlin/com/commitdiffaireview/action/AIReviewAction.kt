package com.commitdiffaireview.action

import com.commitdiffaireview.review.ReviewOrchestrator
import com.commitdiffaireview.review.ReviewOutcome
import com.commitdiffaireview.toolwindow.AIReviewToolWindowService
import com.commitdiffaireview.toolwindow.ReviewErrorPresenter
import com.commitdiffaireview.toolwindow.ReviewUiState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class AIReviewAction : AnAction("AI Review") {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errorPresenter = ReviewErrorPresenter()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindowService = project.service<AIReviewToolWindowService>()

        scope.launch(Dispatchers.Main) {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
            toolWindowService.showState(ReviewUiState.Reviewing("正在读取本次变更..."))

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ReviewOrchestrator(
                        project = project,
                        onStatus = { status ->
                            runBlocking {
                                withContext(Dispatchers.Main) {
                                    toolWindowService.showState(ReviewUiState.Reviewing(status))
                                }
                            }
                        }
                    ).reviewStagedDiff()
                }
            }

            result.fold(
                onSuccess = { outcome -> toolWindowService.showState(outcome.toUiState()) },
                onFailure = { error -> toolWindowService.showState(errorPresenter.present(error)) }
            )
        }
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

    private companion object {
        const val TOOL_WINDOW_ID = "AI Review"
    }
}
