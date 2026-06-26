# Review 工作流加固设计

## 目标

让 DiffGuard 的真实 Review 路径使用工作区规则和忽略文件，改进取消与长耗时任务行为，降低 IDE 读锁压力，并在不替换当前 MVP 架构的前提下加固 Provider 和 Settings 的边界情况。

## 架构

现有 `ReviewOrchestrator` 继续作为核心工作流协调器。它会加载可选的 `.ai-review` 工作区上下文，在 PSI 分析和 Prompt 构造前过滤被忽略的 diff 片段，并通过 `PromptContextBuilder` 构建 Prompt。

取消能力建模为一个小型 token，并贯穿 orchestrator 与 provider 调用。IntelliJ 后台任务持有该 token，并将 `ProgressIndicator` 的取消映射为 AI 调用取消。

PSI 分析保留现有公开同步 API，但限制工作量，并把读操作收敛到单文件粒度。更大范围的异步 PSI 重构延后处理。

## 范围

- 在真实 Review 中使用 `.ai-review/rules.md`、`architecture.md`、`review.md` 和 `ignore.md`。
- 将被忽略的 diff 片段视为不存在，既不进入 Prompt，也不进入 PSI context。
- 允许用户取消 Review 任务，包括正在进行的 HTTP 调用。
- 避免对所有变更 Java 文件执行一次大型读操作。
- 防止多 Git root 的 unstaged 过滤逻辑比较互不相关的仓库相对路径。
- 改进空响应、截断响应和结构化 API 错误的 provider 错误信息。
- 避免把已保存 API key 作为可编辑明文模型状态加载进设置文本框。
- 将详细 PSI context 日志移动到 debug 级别。

## 测试

每个行为变更都先补充一个聚焦的单元测试。最终验证命令为 `./gradlew test`。
