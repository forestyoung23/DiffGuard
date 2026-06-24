package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Path
import kotlin.io.path.Path

internal data class ReviewFindingTarget(
    val path: Path,
    val lineIndex: Int
)

internal class ReviewFindingNavigator(
    private val basePath: Path,
    private val fileExists: (Path) -> Boolean,
    private val findProjectPaths: (String) -> List<Path> = { emptyList() },
    private val openTarget: (ReviewFindingTarget) -> Unit,
    private val reportMissingFile: (Path) -> Unit
) {
    fun navigate(finding: ReviewFinding) {
        val targetPath = resolveTargetPath(finding.file)
        if (targetPath == null) {
            reportMissingFile(resolvePath(finding.file))
            return
        }
        openTarget(ReviewFindingTarget(targetPath, lineIndex(finding.line)))
    }

    private fun resolveTargetPath(file: String): Path? {
        val exactMatch = resolvePathCandidates(file).firstOrNull(fileExists)
        if (exactMatch != null) return exactMatch

        val suffix = Path(stripGitDiffPrefix(file))
        val fileName = suffix.fileName?.toString() ?: return null
        return findProjectPaths(fileName)
            .map { it.toAbsolutePath().normalize() }
            .filter { fileExists(it) }
            .filter { it.endsWith(suffix) }
            .distinct()
            .sortedBy { it.toString() }
            .firstOrNull()
    }

    private fun resolvePathCandidates(file: String): List<Path> =
        listOf(file, stripGitDiffPrefix(file))
            .distinct()
            .map(::resolvePath)

    private fun stripGitDiffPrefix(file: String): String =
        if ((file.startsWith("a/") || file.startsWith("b/")) && file.length > 2) {
            file.drop(2)
        } else {
            file
        }

    private fun resolvePath(file: String): Path {
        val path = Path(file)
        return if (path.isAbsolute) {
            path.normalize()
        } else {
            basePath.resolve(path).toAbsolutePath().normalize()
        }
    }

    private fun lineIndex(line: Int?): Int =
        if (line != null && line > 0) line - 1 else 0

    companion object {
        fun forProject(project: Project): ReviewFindingNavigator {
            val basePath = project.basePath?.let { Path(it) } ?: Path(System.getProperty("user.dir"))
            return ReviewFindingNavigator(
                basePath = basePath,
                fileExists = { LocalFileSystem.getInstance().findFileByNioFile(it) != null },
                findProjectPaths = { fileName ->
                    FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
                        .map { Path(it.path) }
                },
                openTarget = { target ->
                    LocalFileSystem.getInstance().findFileByNioFile(target.path)?.let { virtualFile ->
                        OpenFileDescriptor(project, virtualFile, target.lineIndex, 0).navigate(true)
                    }
                },
                reportMissingFile = { path ->
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification(
                            "DiffGuard 无法定位文件",
                            "未找到 ${path.fileName}，请确认文件仍在当前项目中。",
                            NotificationType.WARNING
                        )
                        .notify(project)
                }
            )
        }

        private const val NOTIFICATION_GROUP_ID = "DiffGuard"
    }
}
