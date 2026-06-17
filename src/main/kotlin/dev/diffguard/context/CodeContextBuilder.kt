package dev.diffguard.context

import dev.diffguard.analyzer.SpringSemanticAnalyzer
import dev.diffguard.psi.JavaPsiAnalyzer
import dev.diffguard.psi.MethodCallExtractor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import git4idea.GitUtil

/**
 * 解析 unified diff 文本，提取每个 Java 文件的修改行号集合
 */
object DiffParser {

    /**
     * 解析 unified diff，返回 文件路径 → 修改行号集合（仅 + 侧）
     */
    fun parse(diff: String): Map<String, Set<Int>> {
        if (diff.isBlank()) return emptyMap()

        val result = mutableMapOf<String, MutableSet<Int>>()
        var currentFile: String? = null
        var newLineNum = 0

        for (line in diff.lines()) {
            when {
                // 匹配 diff --git a/path b/path
                line.startsWith("diff --git ") -> {
                    currentFile = extractFilePath(line)
                    if (currentFile != null && !currentFile.endsWith(".java")) {
                        currentFile = null
                    }
                }
                // 优先以新文件路径为准，兼容 rename、new file、路径中包含空格等场景
                line.startsWith("+++ ") -> {
                    currentFile = extractNewFilePath(line)
                    if (currentFile != null && !currentFile.endsWith(".java")) {
                        currentFile = null
                    }
                }
                // 匹配 @@ -a,b +c,d @@
                line.startsWith("@@ ") -> {
                    newLineNum = extractNewStartLine(line)
                }
                // + 行（非 +++）
                currentFile != null && line.startsWith("+") && !line.startsWith("+++") -> {
                    result.getOrPut(currentFile) { mutableSetOf() }.add(newLineNum)
                    newLineNum++
                }
                // - 行（跳过，不增加 newLineNum）
                currentFile != null && line.startsWith("-") && !line.startsWith("---") -> {
                    // 跳过删除行
                }
                // 上下文行（以空格开头）
                currentFile != null && line.startsWith(" ") -> {
                    newLineNum++
                }
            }
        }

        return result.filterValues { it.isNotEmpty() }
    }

    /**
     * 从 diff --git a/path b/path 中提取文件路径（取 b/ 后面的部分）
     */
    private fun extractFilePath(line: String): String? {
        val tokens = parseGitPathTokens(line.removePrefix("diff --git ").trim())
        return tokens.getOrNull(1)?.normalizeDiffPath()
    }

    /**
     * 从 +++ b/path 中提取新文件路径。/dev/null 表示删除文件，没有新文件上下文。
     */
    private fun extractNewFilePath(line: String): String? {
        val rawPath = parseGitPathTokens(line.removePrefix("+++ ").trim()).firstOrNull() ?: return null
        return rawPath.takeUnless { it == "/dev/null" }?.normalizeDiffPath()
    }

    private fun String.normalizeDiffPath(): String =
        removePrefix("a/").removePrefix("b/")

    private fun parseGitPathTokens(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
            if (index >= text.length) break

            val token = StringBuilder()
            if (text[index] == '"') {
                index++
                var escaped = false
                while (index < text.length) {
                    val char = text[index++]
                    when {
                        escaped -> {
                            token.append(char)
                            escaped = false
                        }
                        char == '\\' -> escaped = true
                        char == '"' -> break
                        else -> token.append(char)
                    }
                }
            } else {
                while (index < text.length && !text[index].isWhitespace()) {
                    token.append(text[index++])
                }
            }
            tokens.add(token.toString())
        }
        return tokens
    }

    /**
     * 从 @@ -a,b +c,d @@ 中提取新文件起始行号 c
     */
    private fun extractNewStartLine(line: String): Int {
        val regex = Regex("""@@\s+-\d+(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@""")
        val match = regex.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }
}

/**
 * Code Context 构建器
 * 集成 PSI 分析组件，从 unified diff 构建完整的代码上下文
 */
class CodeContextBuilder(private val project: Project) {

    private val javaPsiAnalyzer = JavaPsiAnalyzer()
    private val methodCallExtractor = MethodCallExtractor()
    private val springSemanticAnalyzer = SpringSemanticAnalyzer()

    /**
     * 从 unified diff 构建 CodeContext 列表
     */
    fun buildFromDiff(diff: String): List<CodeContext> {
        val fileLineMap = DiffParser.parse(diff)
        if (fileLineMap.isEmpty()) return emptyList()

        return ReadAction.compute<List<CodeContext>, Nothing> {
            fileLineMap.mapNotNull { (relativePath, modifiedLines) ->
                try {
                    analyzeFile(relativePath, modifiedLines)
                } catch (e: Exception) {
                    // 跳过无法分析的文件（如文件不存在、PSI 解析失败等）
                    null
                }
            }
        }
    }

    /**
     * 分析单个 Java 文件，构建 CodeContext
     */
    private fun analyzeFile(relativePath: String, modifiedLines: Set<Int>): CodeContext? {
        val virtualFile = findVirtualFile(relativePath) ?: return null

        // 通过 PsiManager 获取 PsiFile
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: return null

        // 获取文件中的主类（第一个 public class，或第一个 class）
        val psiClass = findMainClass(psiFile) ?: return null

        // 提取类信息
        val classInfo = javaPsiAnalyzer.extractClassInfo(psiFile, psiClass)

        // 提取字段依赖
        val dependencies = javaPsiAnalyzer.extractDependencies(psiClass)

        // 查找被修改的方法
        val modifiedMethods = javaPsiAnalyzer.findModifiedMethods(psiClass, modifiedLines)

        // 分析每个修改方法的上下文
        val methodContexts = modifiedMethods.map { method ->
            analyzeMethod(method)
        }

        // 分析类的 Spring 语义
        val springSemantic = springSemanticAnalyzer.analyzeClass(psiClass)

        return CodeContext(
            filePath = relativePath,
            packageName = classInfo.packageName,
            className = classInfo.className,
            superClass = classInfo.superClass,
            interfaces = classInfo.interfaces,
            annotations = classInfo.annotations,
            dependencies = dependencies,
            springSemantic = springSemantic,
            modifiedMethods = methodContexts
        )
    }

    /**
     * 查找文件中的主类：优先 public class，否则取第一个 class
     */
    private fun findMainClass(psiFile: PsiJavaFile): PsiClass? {
        val classes = psiFile.classes
        if (classes.isEmpty()) return null
        return classes.firstOrNull { it.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC) }
            ?: classes.first()
    }

    /**
     * 分析单个方法，构建 MethodContext
     */
    private fun analyzeMethod(method: PsiMethod): MethodContext {
        val methodName = method.name
        val signature = buildMethodSignature(method)
        val returnType = method.returnType?.presentableText ?: "void"
        val annotations = springSemanticAnalyzer.analyzeMethodAnnotations(method)
        val calls = methodCallExtractor.extractCalls(method)

        return MethodContext(
            methodName = methodName,
            signature = signature,
            returnType = returnType,
            annotations = annotations,
            methodCalls = calls
        )
    }

    /**
     * 构建方法签名，如 createUser(UserDTO dto, String name)
     */
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        return "${method.name}($params)"
    }

    private fun findVirtualFile(relativePath: String): VirtualFile? {
        val normalizedRelativePath = relativePath.trimStart('/')
        val roots = buildList {
            project.basePath?.let { add(it) }
            GitUtil.getRepositoryManager(project).repositories
                .map { it.root.path }
                .forEach { add(it) }
        }.distinct()

        return roots.firstNotNullOfOrNull { root ->
            LocalFileSystem.getInstance().findFileByPath("$root/$normalizedRelativePath")
        }
    }
}
