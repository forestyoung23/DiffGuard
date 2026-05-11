# AI Review 原生结果页 UI 优化设计

**目标：** 将 AI Review 结果页从 `JEditorPane + HTML` 网页式展示改为更贴近 IntelliJ IDEA 原生风格的 Swing 组件结果面板。

## 背景

当前测试截图显示结果页与 IDEA 原生 UI 差异较大：内容区域是大面积白底，字体和间距不像 ToolWindow 内置组件，原本设计中的卡片、badge、间距等 HTML/CSS 样式没有稳定呈现。

主要原因是 `JEditorPane` 的 HTML/CSS 支持有限，现代 CSS 如 `flex`、`gap`、`border-radius` 等在 Swing HTML 中不可靠。继续修 HTML 会导致样式能力受限，且仍难以匹配 IDEA 原生视觉。

## 已确认方向

采用原生 Swing 组件结果面板，不继续把 HTML 作为主展示层。

本轮范围只做展示优化，不增加交互功能：

- 不增加点击跳转源码。
- 不增加复制按钮。
- 不增加折叠、过滤、搜索。
- 不修改 AI Provider、Prompt、Settings 或 JSON 返回格式。
- 不修改 `ReviewFinding` 数据模型。
- 不修改 `ReviewResultParser`。

## 目标体验

结果页应像 IDEA ToolWindow 内部的原生面板，而不是嵌入网页。

目标结构：

```text
AI Review 结果

[摘要卡片]
发现 3 个问题
[HIGH 0] [MEDIUM 1] [LOW 2]

[MEDIUM] src/main/kotlin/.../AIReview.kt:23
callback 回调参数未校验，建议在调用前检查空值。

[LOW] src/main/kotlin/.../Parser.kt:12
建议将长文本拆分，提升阅读体验。
```

视觉要求：

- 背景跟随 IDE ToolWindow / Panel 背景，避免大面积纯白。
- 内容区保留 12-16px padding。
- 摘要区使用轻量卡片样式：边框、内边距、略微区分的背景。
- finding 列表按卡片纵向排列，卡片间距约 8-10px。
- HIGH / MEDIUM / LOW 使用低饱和 badge：
  - HIGH：红色系文字或边框。
  - MEDIUM：橙色系文字或边框。
  - LOW：蓝色系文字或边框。
- 文件位置使用等宽字体，视觉上弱于正文但可辨认。
- message 使用自动换行文本组件，避免长文本挤在一行。
- 空结果和 status 状态也使用同一套原生面板风格。

## 技术设计

新增或改造一个原生 UI renderer：

```text
AIReviewResultPanelRenderer
```

职责：

- 输入 status message 或 `List<ReviewFinding>`。
- 输出 Swing `JComponent`。
- 内部负责构建：
  - status panel
  - empty panel
  - summary panel
  - severity badge panel
  - finding card panel

`AIReviewToolWindowView` 职责保持简单：

- 持有 `JBScrollPane` 和内容容器。
- `showStatus(message)`：清空并替换为 status panel。
- `showFindings(findings)`：清空并替换为 findings panel。
- 每次展示后滚动到顶部。

原有 `AIReviewResultHtmlRenderer` 不再作为 ToolWindow 主展示依赖。可以保留到后续清理，也可以在实现中删除，前提是对应测试同步调整且无其它引用。

## 数据和排序

继续保留当前结构化数据流：

```text
AI JSON 响应
→ ReviewResultParser
→ List<ReviewFinding>
→ AIReviewToolWindowView
→ AIReviewResultPanelRenderer
→ 原生 Swing 组件
```

排序规则保持不变：

1. HIGH
2. MEDIUM
3. LOW
4. 其它未知等级

同等级内保持输入顺序。

统计规则保持不变：

- HIGH / MEDIUM / LOW 统计忽略大小写。
- 未知等级不计入这三个 badge，但仍展示在列表末尾。

## 安全性

原生 Swing 展示不应执行 AI 返回内容中的 HTML。默认使用普通文本组件展示 file、level、message。

如果某处必须用支持 HTML 的 `JLabel`，只允许用于内部固定模板，外部输入必须转义或避免放入 HTML label。

## 测试策略

新增/调整 View 和 renderer 测试，重点验证组件结构与用户可见文本：

- status：显示 status 文案。
- empty findings：显示 “未发现明显问题”。
- non-empty findings：显示 “AI Review 结果”、问题总数、HIGH/MEDIUM/LOW 统计。
- finding card：显示 level、file:line、message。
- 排序：HIGH → MEDIUM → LOW → unknown，同等级稳定排序。
- 组件迁移：不再包含 `JBTable`，不再依赖 `JEditorPane` 作为主展示组件。
- ToolWindow View 每次 show 后内容被替换，而不是追加旧内容。

## 非目标

- 不实现 Markdown 渲染库。
- 不让 AI 直接输出自由 Markdown。
- 不实现源码跳转。
- 不实现复制、折叠、过滤、搜索。
- 不重做 AI 调用流程。
- 不处理 API Key 或网络错误逻辑之外的行为。
