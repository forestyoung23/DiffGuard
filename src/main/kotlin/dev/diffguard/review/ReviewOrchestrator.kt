package dev.diffguard.review

import dev.diffguard.ai.AIProvider
import dev.diffguard.ai.OpenAIProvider
import dev.diffguard.ai.ReviewCancellationToken
import dev.diffguard.context.CodeContext
import dev.diffguard.context.CodeContextBuilder
import dev.diffguard.context.DiffParser
import dev.diffguard.git.GitStagedDiffProvider
import dev.diffguard.git.filterStagedSafePaths
import dev.diffguard.model.AISettingsState
import dev.diffguard.model.ReviewFinding
import dev.diffguard.prompt.PromptContextBuilder
import dev.diffguard.settings.AIReviewSettingsService
import dev.diffguard.workspace.IgnoreMatcher
import dev.diffguard.workspace.WorkspaceContext
import dev.diffguard.workspace.WorkspaceLoader
import dev.diffguard.workspace.WorkspaceLoadResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance(ReviewOrchestrator::class.java)

sealed interface ReviewOutcome {
    data object NoChanges : ReviewOutcome
    data object NeedsConfiguration : ReviewOutcome
    data class Completed(val findings: List<ReviewFinding>) : ReviewOutcome
    data class ParseFallback(val rawResponsePreview: String) : ReviewOutcome
}

fun ReviewOutcome.compatibilityFindings(): List<ReviewFinding> = when (this) {
    ReviewOutcome.NoChanges -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Git",
            line = null,
            message = "没有可 Review 的 staged 变更。请先 stage 需要审查的文件。"
        )
    )
    ReviewOutcome.NeedsConfiguration -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "Settings",
            line = null,
            message = "请先在 Settings / Tools / DiffGuard 中配置 API Key。"
        )
    )
    is ReviewOutcome.Completed -> findings
    is ReviewOutcome.ParseFallback -> listOf(
        ReviewFinding(
            level = "LOW",
            file = "AI Response",
            line = null,
            message = "AI 返回内容不是合法 JSON，已显示原始内容：$rawResponsePreview"
        )
    )
}

class ReviewOrchestrator(
    private val diffProvider: () -> String,
    private val settingsProvider: () -> AISettingsState,
    private val promptBuilder: PromptContextBuilder = PromptContextBuilder(),
    private val parser: ReviewResultParser = ReviewResultParser(),
    private val providerFactory: (AISettingsState) -> AIProvider = { settings ->
        OpenAIProvider(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            client = OpenAIProvider.clientFor(settings)
        )
    },
    private val onStatus: (String) -> Unit = {},
    private val contextBuilder: ((String) -> List<CodeContext>)? = null,
    private val workspaceProvider: () -> WorkspaceContext = { WorkspaceLoadResult.empty().context },
    private val cancellationToken: ReviewCancellationToken = ReviewCancellationToken()
) {
    constructor(
        project: Project,
        onStatus: (String) -> Unit = {},
        cancellationToken: ReviewCancellationToken = ReviewCancellationToken()
    ) : this(
        diffProvider = { GitStagedDiffProvider(project).getStagedDiff() },
        settingsProvider = { AIReviewSettingsService.getInstance().stateWithSecrets() },
        onStatus = onStatus,
        contextBuilder = { diff -> buildStagedSafeCodeContext(project, diff) },
        workspaceProvider = { WorkspaceLoader(project).load().context },
        cancellationToken = cancellationToken
    )

    fun reviewStagedDiff(): ReviewOutcome {
        cancellationToken.throwIfCancellationRequested()
        onStatus("正在读取本次变更...")
        val originalDiff = diffProvider()
        cancellationToken.throwIfCancellationRequested()
        val workspaceContext = workspaceProvider()
        val diff = IgnoreMatcher(workspaceContext.ignorePatterns).filterDiff(originalDiff)
        if (diff.isBlank()) {
            return ReviewOutcome.NoChanges
        }

        val settings = settingsProvider()
        if (settings.apiKey.isBlank()) {
            return ReviewOutcome.NeedsConfiguration
        }

        // PSI Context 分析
        val codeContexts = if (contextBuilder != null) {
            onStatus("正在分析代码上下文...")
            try {
                cancellationToken.throwIfCancellationRequested()
                val contexts = contextBuilder(diff)
                cancellationToken.throwIfCancellationRequested()
                logCodeContexts(contexts)
                contexts
            } catch (e: Exception) {
                LOG.warn("PSI 分析失败，使用纯 diff 模式", e)
                onStatus("PSI 分析失败，使用纯 diff 模式: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        onStatus("正在准备 Review 请求...")
        val prompt = promptBuilder.build(diff, codeContexts, workspaceContext)
        LOG.info("Prompt 长度: ${prompt.length}, 包含 PSI Context: ${codeContexts.isNotEmpty()}")

        val provider = providerFactory(settings)
        onStatus("正在请求 AI，非流式模型可能需要等待一段时间...")
        val rawResponse = provider.review(prompt, cancellationToken)
        cancellationToken.throwIfCancellationRequested()
        onStatus("正在解析 AI 返回结果...")
        return when (val parseResult = parser.tryParse(rawResponse)) {
            is ReviewParseResult.Parsed -> ReviewOutcome.Completed(parseResult.findings)
            is ReviewParseResult.Fallback -> ReviewOutcome.ParseFallback(parseResult.rawResponsePreview)
        }
    }

    /**
     * 输出 PSI 提取结果到日志，用于验证
     * 查看方式：Help → Diagnostic Tools → Debug Log Settings → 添加 #dev.diffguard
     * 日志文件：~/.cache/JetBrains/IntelliJIdea<version>/log/idea.log
     */
    private fun logCodeContexts(contexts: List<CodeContext>) {
        if (contexts.isEmpty()) {
            LOG.info("PSI Context: 未提取到任何 Java 文件上下文")
            return
        }
        LOG.info("PSI Context: 提取到 ${contexts.size} 个文件的上下文")
        for (ctx in contexts) {
            LOG.debug("  文件: ${ctx.filePath}")
            LOG.debug("    类: ${ctx.className}, 包: ${ctx.packageName}")
            LOG.debug("    注解: ${ctx.annotations}")
            LOG.debug("    Spring: ${ctx.springSemantic}")
            LOG.debug("    依赖: ${ctx.dependencies.map { "${it.fieldName}:${it.typeName}[${it.injectionType}]" }}")
            LOG.debug("    修改方法: ${ctx.modifiedMethods.size} 个")
            for (method in ctx.modifiedMethods) {
                val significantCalls = method.methodCalls.filter { it.callType != "UNKNOWN" }
                LOG.debug("      ${method.signature} -> ${significantCalls.map { "${it.qualifier}.${it.methodName}[${it.callType}]" }}")
            }
        }
    }

    private companion object {
        fun buildStagedSafeCodeContext(project: Project, diff: String): List<CodeContext> {
            val stagedPaths = DiffParser.parse(diff).keys.toSet()
            if (stagedPaths.isEmpty()) return emptyList()

            val diffProvider = GitStagedDiffProvider(project)
            val stagedSafePaths = stagedPaths.filterStagedSafePaths(diffProvider.getUnstagedChangedPathsByRoot())
            val contexts = CodeContextBuilder(project).buildFromDiff(diff)
            return contexts.filter { it.filePath in stagedSafePaths }
        }
    }
}
