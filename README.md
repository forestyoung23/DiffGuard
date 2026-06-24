# DiffGuard

DiffGuard 是一个 IntelliJ IDEA 2024+ 插件 MVP，用于在提交代码前对当前 staged diff 执行 AI Code Review。

## 功能

- 在 Commit/VCS 相关入口提供 `Review with DiffGuard` Action。
- 获取当前选中的 Git changes，并生成 unified diff。
- 调用用户配置的 OpenAI Compatible Chat Completions API。
- 在 `DiffGuard` ToolWindow 中展示审查结果。
- 支持解析纯 JSON、Markdown fenced JSON，以及文本中嵌入的 JSON 数组。
- 当 AI 返回无法解析时，会把原始响应作为 LOW 级结果展示。
- 点击结果卡片可跳转到对应文件和行号。
- 支持 workspace guidance 和 ignore patterns，用于补充项目级审查规则。

## 配置

打开 `Settings / Tools / DiffGuard`，配置：

- `Base URL`：OpenAI Compatible API 地址，默认 `https://api.openai.com/v1`
- `API Key`：API Key，会保存到 IntelliJ PasswordSafe；设置页会以密码形式隐藏已保存的 Key，清空后保存会删除 Key
- `Model`：模型名称，默认 `gpt-4o-mini`

## 隐私与数据

DiffGuard 不运营托管服务，也不会把 API Key 打包进插件包。

- API Key 存储在 IntelliJ PasswordSafe 中。
- 非敏感配置存储在 IDE 的应用级配置中。
- 执行 Review 时，插件会把选中的 unified diff、可用的代码上下文和 workspace guidance 发送到用户配置的 `Base URL`。
- 插件不会主动发送数据到 DiffGuard 作者控制的服务器。
- 用户应只配置自己信任的 OpenAI-compatible API Provider，并确认团队允许将相关代码片段发送给该 Provider。

## 使用方式

1. 在 Git 中 stage 需要提交的文件。
2. 在 Commit/VCS 相关入口点击 `Review with DiffGuard`。
3. 插件会打开 `DiffGuard` ToolWindow，并显示 `Reviewing...`。
4. 审查完成后，结果会按风险等级、文件、行号和消息展示。

## 本地运行

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew runIde
```

## 测试

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew test
```

## 构建插件

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew buildPlugin
```

构建产物位于：

```text
build/distributions/
```

## 发布到 JetBrains Marketplace

首次发布建议先在 JetBrains Marketplace 网页手动上传 `build/distributions/*.zip`。后续版本可以使用 Gradle 的 `publishPlugin` 任务自动上传。

发布前检查：

```bash
./gradlew test
./gradlew verifyPluginStructure
./gradlew buildPlugin
```

签名插件需要通过环境变量提供证书和私钥，仓库中不要提交这些值：

```bash
export JETBRAINS_CERTIFICATE_CHAIN="-----BEGIN CERTIFICATE-----..."
export JETBRAINS_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----..."
export JETBRAINS_PRIVATE_KEY_PASSWORD="..."
./gradlew signPlugin
```

发布到 Marketplace 需要 JetBrains Marketplace token：

```bash
export JETBRAINS_MARKETPLACE_TOKEN="perm:..."
./gradlew publishPlugin
```

发布配置位于 `gradle.properties` 和 `build.gradle.kts`：

- `pluginId=dev.diffguard`
- `pluginVersion=0.1.0`
- `pluginSinceBuild=241`
- `pluginUntilBuild=253.*`
- `pluginPublishChannel=default`

每次上传新版本前必须递增 `pluginVersion`。
真实 homepage、source code、support email 等链接建议在 JetBrains Marketplace 插件页面填写；不要在仓库中提交猜测或临时链接。

### Marketplace 页面建议

Short description:

```text
AI code review for selected Git changes before commit, powered by your OpenAI-compatible provider.
```

Privacy summary:

```text
DiffGuard sends selected Git diffs and optional local context only to the OpenAI-compatible API endpoint configured by the user. API keys are stored in IntelliJ PasswordSafe and are not bundled with the plugin.
```

## MVP 不包含

- 自动修复
- Agent
- 向量数据库
- 多轮对话
- 本地模型
- 用户系统
- 企业规则
- 配置中心
