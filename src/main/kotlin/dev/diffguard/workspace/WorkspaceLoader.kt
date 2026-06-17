package dev.diffguard.workspace

import com.intellij.openapi.project.Project
import git4idea.GitUtil
import java.nio.file.Files
import java.nio.file.Path

private const val WORKSPACE_DIR = ".ai-review"
private const val RULES_FILE = "rules.md"
private const val ARCHITECTURE_FILE = "architecture.md"
private const val REVIEW_FILE = "review.md"
private const val IGNORE_FILE = "ignore.md"

/**
 * 从当前项目的 Git 根目录发现并加载 .ai-review workspace。
 */
class WorkspaceLoader(private val project: Project) {
    fun load(): WorkspaceLoadResult = loadFromRoot(findRepositoryRoot(project))

    private fun findRepositoryRoot(project: Project): Path? {
        val projectPath = project.basePath?.let { Path.of(it) }
        val repositories = GitUtil.getRepositoryManager(project).repositories
        if (repositories.isEmpty()) return projectPath

        // 多 Git root 时优先使用包含 project.basePath 的仓库；否则使用 IntelliJ Git API 的第一个仓库。
        val preferred = projectPath?.let { base ->
            repositories.firstOrNull { repository ->
                base.startsWith(Path.of(repository.root.path))
            }
        }
        return Path.of((preferred ?: repositories.first()).root.path)
    }

    companion object {
        fun loadFromRoot(repositoryRoot: Path?): WorkspaceLoadResult {
            if (repositoryRoot == null) return WorkspaceLoadResult.empty()

            val workspaceDir = repositoryRoot.resolve(WORKSPACE_DIR)
            if (!Files.isDirectory(workspaceDir)) {
                return WorkspaceLoadResult.empty()
            }

            val rulesPath = workspaceDir.resolve(RULES_FILE)
            val architecturePath = workspaceDir.resolve(ARCHITECTURE_FILE)
            val reviewPath = workspaceDir.resolve(REVIEW_FILE)
            val ignorePath = workspaceDir.resolve(IGNORE_FILE)

            return WorkspaceLoadResult(
                context = WorkspaceContext(
                    rules = readMarkdown(rulesPath),
                    architecture = readMarkdown(architecturePath),
                    reviewFocus = readMarkdown(reviewPath),
                    ignorePatterns = readIgnorePatterns(ignorePath)
                ),
                status = WorkspaceStatus(
                    workspaceFound = true,
                    workspacePath = workspaceDir.toAbsolutePath().normalize().toString(),
                    rulesLoaded = Files.isRegularFile(rulesPath),
                    architectureLoaded = Files.isRegularFile(architecturePath),
                    reviewLoaded = Files.isRegularFile(reviewPath),
                    ignoreLoaded = Files.isRegularFile(ignorePath)
                )
            )
        }

        private fun readMarkdown(path: Path): String? {
            if (!Files.isRegularFile(path)) return null
            return Files.readString(path).trim().takeIf { it.isNotBlank() }
        }

        private fun readIgnorePatterns(path: Path): List<String> {
            if (!Files.isRegularFile(path)) return emptyList()
            return Files.readAllLines(path)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        }
    }
}
