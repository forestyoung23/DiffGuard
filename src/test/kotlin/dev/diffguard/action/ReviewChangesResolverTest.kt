package dev.diffguard.action

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.Charset

class ReviewChangesResolverTest {
    private var changeIndex = 0

    @Test
    fun `uses commit included changes when commit ui is available`() {
        val includedChange = change()
        val selectedChange = change()

        val result = ReviewChangesResolver.resolve(
            commitIncludedChanges = listOf(includedChange),
            contextChanges = arrayOf(selectedChange),
            selectedChanges = null
        )

        assertEquals(1, result.size)
        assertSame(includedChange, result.single())
    }

    @Test
    fun `does not fall back to selected changes when commit included changes is empty`() {
        val selectedChange = change()

        val result = ReviewChangesResolver.resolve(
            commitIncludedChanges = emptyList(),
            contextChanges = arrayOf(selectedChange),
            selectedChanges = null
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `uses context changes outside commit ui`() {
        val contextChange = change()
        val selectedChange = change()

        val result = ReviewChangesResolver.resolve(
            commitIncludedChanges = null,
            contextChanges = arrayOf(contextChange),
            selectedChanges = arrayOf(selectedChange)
        )

        assertEquals(1, result.size)
        assertSame(contextChange, result.single())
    }

    @Test
    fun `uses selected changes when context changes are empty outside commit ui`() {
        val selectedChange = change()

        val result = ReviewChangesResolver.resolve(
            commitIncludedChanges = null,
            contextChanges = emptyArray(),
            selectedChanges = arrayOf(selectedChange)
        )

        assertEquals(1, result.size)
        assertSame(selectedChange, result.single())
    }

    private fun change(): Change {
        changeIndex += 1
        val file = TestFilePath("/tmp/review-$changeIndex.txt")
        return Change(TestContentRevision("before", file), TestContentRevision("after", file))
    }

    private class TestContentRevision(
        private val content: String,
        private val file: FilePath
    ) : ContentRevision {
        override fun getContent(): String = content
        override fun getFile(): FilePath = file
        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class TestFilePath(private val path: String) : FilePath {
        override fun getPath(): String = path
        override fun isDirectory(): Boolean = false
        override fun getIOFile(): File = File(path)
        override fun getName(): String = getIOFile().name
        override fun getPresentableUrl(): String = path
        override fun getVirtualFile(): VirtualFile? = null
        override fun getVirtualFileParent(): VirtualFile? = null
        override fun getDocument(): Document? = null
        override fun getCharset(): Charset = Charsets.UTF_8
        override fun getCharset(project: Project?): Charset = Charsets.UTF_8
        override fun getFileType(): FileType = FileTypes.UNKNOWN
        override fun refresh() = Unit
        override fun hardRefresh() = Unit
        override fun isUnder(parent: FilePath, strict: Boolean): Boolean = path.startsWith(parent.path)
        override fun getParentPath(): FilePath? = getIOFile().parent?.let { TestFilePath(it) }
        override fun isNonLocal(): Boolean = false
    }
}
