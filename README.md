# CommitDiffAIReview

CommitDiffAIReview 是一个 IntelliJ IDEA 2024+ 插件 MVP，用于在提交代码前对当前 staged diff 执行 AI Code Review。

## 功能

- 在 Commit/VCS 相关入口提供 `AI Review` Action。
- 使用 IntelliJ Git API 获取当前 staged changes 的 unified diff。
- 调用 OpenAI Compatible Chat Completions API。
- 在 `AI Review` ToolWindow 中展示审查结果。
- 支持解析纯 JSON、Markdown fenced JSON，以及文本中嵌入的 JSON 数组。
- 当 AI 返回无法解析时，会把原始响应作为 LOW 级结果展示。

## 配置

打开 `Settings / Tools / CommitDiffAIReview`，配置：

- `Base URL`：OpenAI Compatible API 地址，默认 `https://api.openai.com/v1`
- `API Key`：API Key
- `Model`：模型名称，默认 `gpt-4o-mini`

## 使用方式

1. 在 Git 中 stage 需要提交的文件。
2. 在 Commit/VCS 相关入口点击 `AI Review`。
3. 插件会打开 `AI Review` ToolWindow，并显示 `Reviewing...`。
4. 审查完成后，结果会按风险等级、文件、行号和消息展示。

## 本地运行

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle runIde
```

## 测试

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle test
```

## 构建插件

```bash
JAVA_HOME="/Users/forest/Library/Java/JavaVirtualMachines/corretto-17.0.15/Contents/Home" gradle buildPlugin
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
