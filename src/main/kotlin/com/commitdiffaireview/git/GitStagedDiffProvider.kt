package com.commitdiffaireview.git

import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

internal val STAGED_DIFF_PARAMETERS = listOf("--cached", "--unified=3", "--no-color")
internal val UNSTAGED_DIFF_NAME_ONLY_PARAMETERS = listOf("--name-only", "--no-color")

class GitStagedDiffProvider(private val project: Project) {
    fun getStagedDiff(): String {
        val repositories = GitUtil.getRepositoryManager(project).repositories
        return repositories.joinToString(separator = "\n") { repository ->
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                addParameters(*STAGED_DIFF_PARAMETERS.toTypedArray())
            }
            Git.getInstance().runCommand(handler).outputAsJoinedString
        }.trim()
    }

    fun getUnstagedChangedPaths(): Set<String> {
        val repositories = GitUtil.getRepositoryManager(project).repositories
        return repositories
            .flatMap { repository ->
                val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                    addParameters(*UNSTAGED_DIFF_NAME_ONLY_PARAMETERS.toTypedArray())
                }
                Git.getInstance().runCommand(handler).output
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
