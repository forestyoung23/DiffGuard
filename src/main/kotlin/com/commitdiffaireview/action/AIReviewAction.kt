package com.commitdiffaireview.action

import com.commitdiffaireview.review.ReviewOrchestrator
import com.commitdiffaireview.toolwindow.AIReviewToolWindowService
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
import kotlinx.coroutines.withContext

class AIReviewAction : AnAction("AI Review") {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindowService = project.service<AIReviewToolWindowService>()

        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
        toolWindowService.showStatus("正在获取 staged diff 并准备 AI Review...")

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ReviewOrchestrator(
                        project = project,
                        onStatus = { status ->
                            launch(Dispatchers.Main) {
                                toolWindowService.showStatus(status)
                            }
                        }
                    ).reviewStagedDiff()
                }
            }

            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = toolWindowService::showFindings,
                    onFailure = { error -> toolWindowService.showStatus(error.message ?: "AI Review 失败。") }
                )
            }
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private companion object {
        const val TOOL_WINDOW_ID = "AI Review"
    }
}
