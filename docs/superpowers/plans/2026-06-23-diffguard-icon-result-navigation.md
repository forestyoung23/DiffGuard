# DiffGuard 图标与结果导航实施计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐步执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 添加自定义 DG Mark 插件图标，并让 Review findings 可点击，使用户能跳转到相关文件和行号。

**架构：** 保持渲染和导航分离。renderer 通过回调暴露可点击的 finding 卡片，ToolWindow view 将该回调连接到感知 project 的 navigator。插件图标是静态 SVG resource，并从 `plugin.xml` 注册。

**技术栈：** Kotlin、IntelliJ Platform Swing UI DSL、IntelliJ VirtualFile/OpenFileDescriptor API、JUnit 5、Gradle IntelliJ plugin。

---

## 文件结构

- 创建 `src/main/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigator.kt`：感知 project 的文件解析、编辑器导航和缺失文件通知 hook。
- 修改 `src/main/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`：添加可选点击回调，并让 finding 卡片/位置行可点击。
- 修改 `src/main/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowFactory.kt`：将 `Project` 传入 view，并接入默认 navigator。
- 创建 `src/main/resources/icons/diffguard.svg`：DG Mark 图标。
- 修改 `src/main/resources/META-INF/plugin.xml`：为 ToolWindow 和 Review action 注册图标。
- 修改 `src/test/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`：在不使用 HTML editor pane 的情况下证明点击回调行为。
- 修改 `src/test/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`：适配 project 可选构造函数的 view 构造方式。
- 创建 `src/test/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigatorTest.kt`：使用 fake opener/reporter 验证路径/行号解析行为。

## 任务 1：Renderer 点击回调

**文件：**
- 修改：`src/main/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`
- 测试：`src/test/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`

- [ ] **步骤 1：编写失败的 renderer 回调测试**

添加一个测试：使用 `onFindingSelected` 渲染 finding，找到位置文本组件，通过 mouse listener 点击它，并断言收到的是原始 `ReviewFinding`。

- [ ] **步骤 2：运行聚焦测试并确认红灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.AIReviewResultPanelRendererTest`

预期：编译失败或断言失败，因为 renderer 构造函数尚未接收 `onFindingSelected`。

- [ ] **步骤 3：实现最小可点击 renderer**

更新 renderer 构造函数，使其接收 `private val onFindingSelected: ((ReviewFinding) -> Unit)? = null`。在 `findingCard` 中，当回调存在时，为卡片和位置行添加手形 cursor、tooltip 和 mouse listener。点击后用对应 finding 调用回调。

- [ ] **步骤 4：运行聚焦测试并确认绿灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.AIReviewResultPanelRendererTest`

预期：PASS。

## 任务 2：Navigator

**文件：**
- 创建：`src/main/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigator.kt`
- 测试：`src/test/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigatorTest.kt`

- [ ] **步骤 1：编写失败的 navigator 测试**

测试 finding 文件路径会基于给定 base path 解析，将从 1 开始的行号转换为编辑器从 0 开始的行号，对缺失/无效行号使用 0，并报告缺失文件。

- [ ] **步骤 2：运行聚焦测试并确认红灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.ReviewFindingNavigatorTest`

预期：编译失败，因为 `ReviewFindingNavigator` 尚不存在。

- [ ] **步骤 3：用可注入依赖实现 navigator**

创建 `ReviewFindingNavigator`，构造函数依赖包含 `basePath`、文件存在性查询、opener 和缺失文件 reporter。添加 companion `forProject(project: Project)`，使用 `LocalFileSystem`、`OpenFileDescriptor` 和 `NotificationGroupManager`。

- [ ] **步骤 4：运行聚焦测试并确认绿灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.ReviewFindingNavigatorTest`

预期：PASS。

## 任务 3：接入 ToolWindow 导航

**文件：**
- 修改：`src/main/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowFactory.kt`
- 测试：`src/test/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`

- [ ] **步骤 1：编写/更新 view 接线测试**

添加一个测试：构造 `AIReviewToolWindowView(onFindingSelected = { ... })`，渲染 findings，点击位置文本，并断言回调收到该 finding。

- [ ] **步骤 2：运行聚焦测试并确认红灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.AIReviewToolWindowViewTest`

预期：编译失败或断言失败，因为 view 接线尚未暴露回调。

- [ ] **步骤 3：实现 view 接线**

让 `AIReviewToolWindowView` 接收 `onFindingSelected: ((ReviewFinding) -> Unit)? = null`，并初始化 `AIReviewResultPanelRenderer(onFindingSelected)`。在 `AIReviewToolWindowFactory` 中构造 `ReviewFindingNavigator.forProject(project)`，并传入 `navigator::navigate`。

- [ ] **步骤 4：运行聚焦测试并确认绿灯**

运行：`./gradlew test --tests dev.diffguard.toolwindow.AIReviewToolWindowViewTest`

预期：PASS。

## 任务 4：图标资源注册

**文件：**
- 创建：`src/main/resources/icons/diffguard.svg`
- 修改：`src/main/resources/META-INF/plugin.xml`

- [ ] **步骤 1：添加 DG Mark SVG**

按已确认的 DG Mark 方向创建适配 16x16 的 SVG。使用蓝色圆角底、白色 DG 笔画，以及绿色 check/guard 强调元素。

- [ ] **步骤 2：注册图标**

在 `plugin.xml` 的 `toolWindow` 元素和 `DiffGuard.AIReviewAction` action 元素上添加 `icon="/icons/diffguard.svg"`。

- [ ] **步骤 3：运行插件构建**

运行：`./gradlew buildPlugin`

预期：PASS，且没有资源缺失错误。

## 任务 5：最终验证

**文件：**
- 所有变更文件。

- [ ] **步骤 1：运行聚焦测试**

运行：`./gradlew test --tests dev.diffguard.toolwindow.*`

预期：PASS。

- [ ] **步骤 2：运行完整验证**

运行：`./gradlew test buildPlugin`

预期：PASS。

- [ ] **步骤 3：检查变更文件**

运行：`git diff -- src/main/kotlin/dev/diffguard/toolwindow src/main/resources/META-INF/plugin.xml src/main/resources/icons src/test/kotlin/dev/diffguard/toolwindow`

预期：变更与设计一致，且没有无关编辑。
