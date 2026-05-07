# CommitDiffAIReview MVP 设计文档

日期：2026-05-07

## 目标

构建一个名为 `CommitDiffAIReview` 的 IntelliJ IDEA 插件 MVP。在用户提交代码前，插件对当前 Git staged diff 调用 OpenAI Compatible API 进行 AI Code Review，并在 IntelliJ ToolWindow 中展示结构化 Review 结果。

## 范围

### 实现范围

- 基于 IntelliJ Platform SDK，兼容 IntelliJ IDEA 2024+。
- 使用 Kotlin 与 Gradle Kotlin DSL。
- 使用 Swing 构建 UI。
- 使用 OkHttp 发起 HTTP 请求。
- 使用 kotlinx.serialization 处理 JSON 请求与响应。
- 在 Commit/VCS 工作流中提供 `AI Review` 入口 Action。
- 使用 IntelliJ API 获取 staged unified diff，不通过 shell 执行 `git` 命令。
- 调用 OpenAI Compatible Chat Completions API。
- 新增 `AI Review` ToolWindow，展示风险等级、文件名、行号和 Review 信息。
- 提供 IDE 全局配置：`baseUrl`、`apiKey`、`model`。

### 不实现范围

- 自动修复。
- Agent 工作流。
- 向量数据库。
- 多轮对话。
- 本地模型。
- 用户系统。
- 企业规则。
- 配置中心。
- Spring、JavaFX、Compose、数据库或外部服务框架。

## 架构

项目是一个小型 IntelliJ 插件，按职责划分包结构：

- `action`：用户入口，主要是 `AIReviewAction`。
- `git`：获取 staged diff。
- `ai`：AI Provider 接口与 OpenAI Compatible Provider 实现。
- `review`：Prompt 构造、响应解析、Review 编排。
- `toolwindow`：Swing ToolWindow UI 与结果更新服务。
- `model`：Review 模型与 OpenAI DTO。
- `settings`：全局 IDE 配置 UI 与持久化。

主流程：

1. 用户在 Commit/VCS 工作流中点击 `AI Review`。
2. `AIReviewAction` 打开 `AI Review` ToolWindow，并将状态设置为 `Reviewing...`。
3. `GitStagedDiffProvider` 使用 IntelliJ Git API 获取当前 staged unified diff。
4. 如果没有 staged diff，ToolWindow 显示 `No staged changes to review.`，不调用 AI。
5. `ReviewPromptBuilder` 根据 diff 构造结构化 Review Prompt。
6. `OpenAIProvider` 将请求发送到配置的 OpenAI Compatible Chat Completions endpoint。
7. `ReviewResultParser` 将模型响应解析为 `ReviewFinding` 列表。
8. ToolWindow 表格展示 Review 结果。

## 组件设计

### `AIReviewAction`

职责：

- 响应用户点击 `AI Review`。
- 只做轻量编排，将 Git、AI、解析、UI 逻辑委托给对应组件。
- 在 EDT 之外执行网络请求与 Review 流程。
- 在 EDT 中更新 ToolWindow UI。

该类不执行 shell 命令，也不包含 HTTP 或 JSON 解析细节。

### `GitStagedDiffProvider`

职责：

- 使用 IntelliJ Git/Change API 获取 staged/index 状态的 unified diff。
- 支持包含多个 Git root 的项目。
- 返回合并后的 diff 字符串。
- 没有 staged changes 时返回空字符串。

实现不得通过 shell 执行 `git diff`。如果 IntelliJ IDEA 2024.x 不同小版本中的 API 签名存在差异，实现仍应保持在 IntelliJ Platform/Git4Idea API 范围内，例如 Git change utilities、content revisions 或 diff providers。

### `AIProvider` 与 `OpenAIProvider`

`AIProvider` 是最小抽象：

```kotlin
interface AIProvider {
    suspend fun review(prompt: String): String
}
```

`OpenAIProvider` 职责：

- 从全局配置读取 `baseUrl`、`apiKey`、`model`。
- POST 到 OpenAI Compatible `/v1/chat/completions` endpoint。
- 使用 OkHttp 发起 HTTP 请求。
- 使用 kotlinx.serialization 构造与解析 JSON。
- 发送一个 system message 和一个 user prompt。
- 返回 `choices[0].message.content`。
- 对缺失 API Key、HTTP 失败、响应格式异常、网络异常抛出清晰错误。

默认配置：

- `baseUrl`：`https://api.openai.com`
- `apiKey`：空
- `model`：`gpt-4o-mini`

### `ReviewPromptBuilder`

职责：

- 明确说明输入内容是 staged unified diff。
- 要求模型重点检查：
  - Bug 风险
  - 空指针风险
  - 并发问题
  - 事务问题
  - SQL 风险
  - 安全问题
  - 代码可读性
- 要求模型只返回 JSON 数组。

期望返回格式：

```json
[
  {
    "level": "HIGH",
    "file": "UserService.java",
    "line": 42,
    "message": "Potential null pointer"
  }
]
```

规则：

- `level` 只能是 `HIGH`、`MEDIUM` 或 `LOW`。
- 如果无法定位行号，`line` 可以为 `null`。
- 没有问题时返回 `[]`。
- 不返回 Markdown，也不返回额外解释文本。

### `ReviewResultParser`

职责：

- 将模型输出解析为 `ReviewFinding` 对象。
- 支持以下格式：
  - 纯 JSON 数组。
  - Markdown fenced JSON code block。
  - 包含在其他文本中的第一个 JSON 数组。
- 如果解析失败，返回一条兜底结果：
  - `level = LOW`
  - `file = AI Response`
  - `line = null`
  - `message = 截断后的模型原始响应`

这样可以避免静默失败，即使模型没有严格遵循格式，ToolWindow 仍能展示有用信息。

### ToolWindow

ToolWindow ID：`AI Review`

UI 设计：

- 使用 Swing。
- 顶部状态标签显示：`Ready`、`Reviewing...`、`No staged changes to review.`、`Error: ...`、`No issues found.` 或 `N findings`。
- 主体使用 `JBTable`，包含以下列：
  - `Level`
  - `File`
  - `Line`
  - `Message`
- 用户触发 Action 后自动打开 ToolWindow，并刷新表格。

### Settings

Settings 页面：

`Settings / Tools / CommitDiffAIReview`

字段：

- `Base URL`
- `API Key`
- `Model`

配置通过 `PersistentStateComponent` 以 IDE 全局方式保存。MVP 中 API Key 直接保存到 IDE 配置里；为了保持实现简单，暂不接入 PasswordSafe。

## 错误处理

- 无 Project：Action 直接退出。
- 无 staged diff：显示 `No staged changes to review.`，跳过 AI 请求。
- 未配置 API Key：显示配置提示，跳过 AI 请求。
- HTTP 非 2xx：在 ToolWindow 中显示错误摘要。
- 网络异常：在 ToolWindow 中显示错误摘要。
- AI 返回 JSON 无法解析：生成一条 LOW 级兜底结果，展示原始响应内容。
- Review 请求耗时较长：在后台任务中执行，Swing UI 更新切回 EDT。

## 测试与验证

自动化测试：

- Prompt builder 包含所有要求的 Review 类别。
- Parser 能解析纯 JSON 数组。
- Parser 能解析 fenced `json` 代码块。
- Parser 能解析嵌入在文本中的 JSON 数组。
- Parser 对非法输出能生成兜底结果。

手动验证：

- `./gradlew test` 通过。
- `./gradlew runIde` 能启动插件沙箱。
- Settings 页面可以保存并重新加载配置。
- staged changes 可以被 Review，且不通过 shell 执行 Git 命令。
- ToolWindow 能展示 findings、空结果、无 staged changes 和 API 错误。

## 交付物

- 完整 Gradle Kotlin DSL 项目。
- 包含 Action、ToolWindow、Settings 注册的 `plugin.xml`。
- 按分层包组织的 Kotlin 源码。
- OpenAI Compatible API 调用实现。
- 基于 IntelliJ API 的 staged diff 获取实现。
- JSON 解析实现。
- 中文 README，包含安装、配置、运行与使用说明。
