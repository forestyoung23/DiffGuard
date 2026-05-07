package com.commitdiffaireview.git

import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

class GitStagedDiffProvider(private val project: Project) {
    fun getStagedDiff(): String {
        val repositories = GitUtil.getRepositoryManager(project).repositories
        return repositories.joinToString(separator = "\n") { repository ->
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                addParameters("--cached", "--unified=3", "--no-color")
            }
            Git.getInstance().runCommand(handler).outputAsJoinedString
        }.trim()
    }
}
