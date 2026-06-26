# DiffGuard 图标与结果导航设计

## 背景

DiffGuard 当前使用默认 IntelliJ ToolWindow 图标。Review 结果面板把 findings 渲染为静态 Swing 卡片，但每个 `ReviewFinding` 已经携带文件路径和可选行号。

## 目标

- 按已确认的 DG Mark 方向添加自定义 DiffGuard 图标。
- 将该图标用于 DiffGuard ToolWindow 和 Review action。
- 允许用户点击 Review finding 的位置并跳转到对应项目文件和行号。
- 继续用普通 Swing 文本渲染结果文本，保留现有 HTML 注入防护。

## 非目标

- 本次不处理远程模型列表或 Settings 变更。
- 不重新设计完整结果 UI。
- 除使用现有 `ReviewFinding.file` 和 `ReviewFinding.line` 字段外，不修改 AI 解析逻辑。

## 图标设计

图标会作为 SVG 提交到插件 resources 下。图标采用紧凑的 DG mark：蓝色圆角方形底、白色 DG 形态，以及绿色 guard/check 强调元素。图标在 IntelliJ 工具栏和 ToolWindow 尺寸下应保持清晰可辨。

`plugin.xml` 会为以下位置注册图标：

- `toolWindow` `icon`
- `DiffGuard.AIReviewAction` `icon`

## 结果导航

结果 renderer 会接收一个可选的 finding 选择回调。带项目位置的 finding 卡片会暴露一个可点击的位置行。点击后会把对应 `ReviewFinding` 传给回调。

导航逻辑放在 renderer 之外。ToolWindow view 接收 navigator 依赖，并把回调传入 renderer。默认 navigator 会：

- 基于项目根目录解析 `ReviewFinding.file`。
- 使用 `OpenFileDescriptor` 打开文件。
- 当 `line` 存在且为正数时跳转到 `line - 1`。
- 当没有有效行号时打开第一行。
- 当文件找不到时展示轻量通知。

这样可以保持 renderer 可测试，并避免把纯 UI 渲染与项目文件系统 API 耦合。

## 测试

- 添加 renderer 测试，证明可点击 finding 会调用回调，且普通文本渲染仍保持不变。
- 在 IntelliJ 测试框架可行时，添加 navigator 测试，覆盖打开已解析文件和报告缺失文件。
- 保持现有 ToolWindow 测试通过。
- 先运行聚焦的 ToolWindow/Settings 测试，再运行 `buildPlugin`。
