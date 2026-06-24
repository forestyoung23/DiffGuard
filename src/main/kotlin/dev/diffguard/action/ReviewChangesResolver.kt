package dev.diffguard.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change

internal object ReviewChangesResolver {
    fun resolve(event: AnActionEvent): List<Change> = resolve(
        commitIncludedChanges = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedChanges(),
        contextChanges = event.getData(VcsDataKeys.CHANGES),
        selectedChanges = event.getData(VcsDataKeys.SELECTED_CHANGES)
    )

    internal fun resolve(
        commitIncludedChanges: List<Change>?,
        contextChanges: Array<Change>?,
        selectedChanges: Array<Change>?
    ): List<Change> {
        if (commitIncludedChanges != null) {
            return commitIncludedChanges
        }

        return contextChanges?.toList()?.takeIf { it.isNotEmpty() }
            ?: selectedChanges?.toList()?.takeIf { it.isNotEmpty() }
            ?: emptyList()
    }
}
