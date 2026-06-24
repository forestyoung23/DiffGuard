package dev.diffguard.toolwindow

import dev.diffguard.model.ReviewFinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReviewFindingNavigatorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `opens relative finding path at zero based line`() {
        val file = Files.createDirectories(tempDir.resolve("src/main/kotlin/dev/diffguard"))
            .resolve("UserService.kt")
        Files.writeString(file, "line 1\nline 2\n")
        val openedTargets = mutableListOf<ReviewFindingTarget>()
        val navigator = ReviewFindingNavigator(
            basePath = tempDir,
            fileExists = { Files.exists(it) },
            findProjectPaths = ::findProjectPaths,
            openTarget = { openedTargets.add(it) },
            reportMissingFile = {}
        )

        navigator.navigate(
            ReviewFinding(
                level = "HIGH",
                file = "src/main/kotlin/dev/diffguard/UserService.kt",
                line = 42,
                message = "可能空指针"
            )
        )

        assertEquals(
            listOf(ReviewFindingTarget(file.toAbsolutePath().normalize(), 41)),
            openedTargets
        )
    }

    @Test
    fun `opens git diff b prefixed finding path`() {
        val file = Files.createDirectories(tempDir.resolve("src/main/kotlin/dev/diffguard"))
            .resolve("UserService.kt")
        Files.writeString(file, "line 1\nline 2\n")
        val openedTargets = mutableListOf<ReviewFindingTarget>()
        val navigator = ReviewFindingNavigator(
            basePath = tempDir,
            fileExists = { Files.exists(it) },
            findProjectPaths = ::findProjectPaths,
            openTarget = { openedTargets.add(it) },
            reportMissingFile = {}
        )

        navigator.navigate(
            ReviewFinding(
                level = "HIGH",
                file = "b/src/main/kotlin/dev/diffguard/UserService.kt",
                line = 2,
                message = "可能空指针"
            )
        )

        assertEquals(
            listOf(ReviewFindingTarget(file.toAbsolutePath().normalize(), 1)),
            openedTargets
        )
    }

    @Test
    fun `opens nested project file when finding contains only file name`() {
        val file = Files.createDirectories(tempDir.resolve("iby-device-batch/src/main/java/com/inboyu/device/batch/jobhandler/device"))
            .resolve("AccessPersonNumJobHandle.java")
        Files.writeString(file, "line 1\nline 2\n")
        val openedTargets = mutableListOf<ReviewFindingTarget>()
        val navigator = ReviewFindingNavigator(
            basePath = tempDir,
            fileExists = { Files.exists(it) },
            findProjectPaths = ::findProjectPaths,
            openTarget = { openedTargets.add(it) },
            reportMissingFile = {}
        )

        navigator.navigate(
            ReviewFinding(
                level = "LOW",
                file = "AccessPersonNumJobHandle.java",
                line = 50,
                message = "日志级别不准确"
            )
        )

        assertEquals(
            listOf(ReviewFindingTarget(file.toAbsolutePath().normalize(), 49)),
            openedTargets
        )
    }

    @Test
    fun `uses first line when finding line is missing or invalid`() {
        val file = Files.writeString(tempDir.resolve("README.md"), "# DiffGuard\n")
        val openedTargets = mutableListOf<ReviewFindingTarget>()
        val navigator = ReviewFindingNavigator(
            basePath = tempDir,
            fileExists = { Files.exists(it) },
            openTarget = { openedTargets.add(it) },
            reportMissingFile = {}
        )

        navigator.navigate(ReviewFinding(level = "LOW", file = "README.md", line = null, message = "说明"))
        navigator.navigate(ReviewFinding(level = "LOW", file = "README.md", line = 0, message = "说明"))

        assertEquals(
            listOf(
                ReviewFindingTarget(file.toAbsolutePath().normalize(), 0),
                ReviewFindingTarget(file.toAbsolutePath().normalize(), 0)
            ),
            openedTargets
        )
    }

    @Test
    fun `reports missing resolved file without opening`() {
        val openedTargets = mutableListOf<ReviewFindingTarget>()
        val missingFiles = mutableListOf<Path>()
        val navigator = ReviewFindingNavigator(
            basePath = tempDir,
            fileExists = { Files.exists(it) },
            openTarget = { openedTargets.add(it) },
            reportMissingFile = { missingFiles.add(it) }
        )

        navigator.navigate(ReviewFinding(level = "MEDIUM", file = "Missing.kt", line = 7, message = "缺失"))

        assertEquals(emptyList<ReviewFindingTarget>(), openedTargets)
        assertEquals(listOf(tempDir.resolve("Missing.kt").toAbsolutePath().normalize()), missingFiles)
    }

    private fun findProjectPaths(fileName: String): List<Path> =
        Files.walk(tempDir).use { paths ->
            paths.filter { it.fileName?.toString() == fileName }.toList()
        }
}
