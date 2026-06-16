package com.commitdiffaireview.workspace

/**
 * AI Review Workspace 中可注入 Prompt 的项目级上下文。
 *
 * 这些字段只承载 Markdown/文本内容，不表达规则 AST，也不执行任何脚本。
 */
data class WorkspaceContext(
    val rules: String?,
    val architecture: String?,
    val reviewFocus: String?,
    val ignorePatterns: List<String>
)

/**
 * ToolWindow 使用的加载状态，避免 UI 通过内容为空来猜测文件是否存在。
 */
data class WorkspaceStatus(
    val workspaceFound: Boolean,
    val workspacePath: String?,
    val rulesLoaded: Boolean,
    val architectureLoaded: Boolean,
    val reviewLoaded: Boolean,
    val ignoreLoaded: Boolean
) {
    companion object {
        fun absent(): WorkspaceStatus = WorkspaceStatus(
            workspaceFound = false,
            workspacePath = null,
            rulesLoaded = false,
            architectureLoaded = false,
            reviewLoaded = false,
            ignoreLoaded = false
        )
    }
}

/**
 * WorkspaceLoader 的完整结果：Prompt 使用 context，ToolWindow 使用 status。
 */
data class WorkspaceLoadResult(
    val context: WorkspaceContext,
    val status: WorkspaceStatus
) {
    companion object {
        fun empty(): WorkspaceLoadResult = WorkspaceLoadResult(
            context = WorkspaceContext(
                rules = null,
                architecture = null,
                reviewFocus = null,
                ignorePatterns = emptyList()
            ),
            status = WorkspaceStatus.absent()
        )
    }
}
