# PSI Context Builder 设计文档

日期：2026-05-13

## 目标

让 AI Review 不再仅基于 diff 文本，而是基于 IntelliJ PSI 的代码语义上下文。通过解析 Git Diff 的修改行号，定位对应的 PsiMethod，提取类信息、字段依赖、方法调用和 Spring 语义，生成结构化上下文注入 AI Prompt。

## 范围

### 实现范围

- 解析 unified diff，提取每个文件的修改行号集合（仅 `+` 侧）。
- 使用 IntelliJ PSI API 分析 `.java` 文件。
- 提取类信息：类名、包名、父类、实现接口、注解。
- 提取字段依赖：`@Autowired`、`@Resource`、`final field`。
- 提取方法信息：方法名、签名、返回值、注解。
- 提取方法调用类型：MAPPER、REDIS、FEIGN、HTTP、THREAD、UNKNOWN。
- 识别 Spring 语义注解：`@Service`、`@Controller`、`@Repository`、`@Transactional`。
- 将 PSI Context 注入 Review Prompt，替换现有 Prompt 结构。
- 对非 Java 文件保留原始 diff，不做 PSI 分析。

### 不实现范围

- Kotlin PSI（仅 Java）。
- 删除代码的分析（`-` 侧跳过）。
- 自动修复。
- 跨文件调用链追踪。
- 第三方代码分析框架。

## 架构

### 数据流

```
GitStagedDiffProvider.getStagedDiff()
    ↓
CodeContextBuilder.buildFromDiff(diff, project)
    ├── DiffParser: 解析 unified diff → Map<filePath, Set<lineNumber>>
    ├── 对每个 Java 文件:
    │   ├── PsiManager.findFile() → PsiJavaFile
    │   ├── JavaPsiAnalyzer: 提取类/字段/方法信息
    │   ├── MethodCallExtractor: 提取方法调用
    │   └── SpringSemanticAnalyzer: 识别 Spring 注解
    └── 返回 List<CodeContext>
    ↓
ReviewPromptBuilder.build(diff, codeContexts)
    ↓
OpenAIProvider → AI Review
```

### 包结构

```
src/main/kotlin/com/commitdiffaireview/
├── psi/
│   ├── JavaPsiAnalyzer.kt
│   └── MethodCallExtractor.kt
├── context/
│   ├── CodeContext.kt
│   ├── MethodContext.kt
│   └── CodeContextBuilder.kt
└── analyzer/
    └── SpringSemanticAnalyzer.kt
```

## 数据模型

### CodeContext

```kotlin
data class CodeContext(
    val filePath: String,
    val packageName: String,
    val className: String,
    val superClass: String?,
    val interfaces: List<String>,
    val annotations: List<String>,
    val dependencies: List<DependencyInfo>,
    val springSemantic: SpringSemantic?,
    val modifiedMethods: List<MethodContext>
)
```

### DependencyInfo

```kotlin
data class DependencyInfo(
    val fieldName: String,
    val typeName: String,
    val injectionType: String  // AUTOWIRED, RESOURCE, FINAL, NONE
)
```

### MethodContext

```kotlin
data class MethodContext(
    val methodName: String,
    val signature: String,
    val returnType: String,
    val annotations: List<String>,
    val methodCalls: List<MethodCall>
)
```

### MethodCall

```kotlin
data class MethodCall(
    val qualifier: String,
    val methodName: String,
    val callType: String  // MAPPER, REDIS, FEIGN, HTTP, THREAD, UNKNOWN
)
```

### SpringSemantic

```kotlin
enum class SpringSemantic {
    SERVICE, CONTROLLER, REPOSITORY, COMPONENT, CONFIGURATION, NONE
}
```

## 组件设计

### DiffParser（内置于 CodeContextBuilder）

职责：解析 unified diff 文本，提取每个文件的修改行号集合。

规则：
- 识别 `diff --git a/path b/path` 行提取文件路径。
- 识别 `@@ -a,b +c,d @@` 行提取新文件起始行号。
- 对 `+` 前缀行（非 `+++`）记录行号。
- 对 `-` 前缀行跳过。
- 仅保留 `.java` 文件。
- 返回 `Map<String, Set<Int>>`（文件路径 → 修改行号集合）。

### JavaPsiAnalyzer

职责：分析单个 PsiJavaFile，提取类级别信息和定位修改方法。

方法：
- `extractClassInfo(psiFile: PsiJavaFile): ClassInfo` — 提取包名、类名、父类、接口、注解。
- `findModifiedMethods(psiFile: PsiJavaFile, modifiedLines: Set<Int>): List<PsiMethod>` — 查找包含修改行的方法。
- `extractDependencies(psiClass: PsiClass): List<DependencyInfo>` — 提取字段依赖。

定位修改方法的逻辑：
- 使用 `PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)` 获取所有方法。
- 对每个方法，检查 `method.textRange` 是否包含修改行号中的任意一行。
- 使用 `psiFile.viewProvider.document` 将 offset 转换为行号进行比较。

### MethodCallExtractor

职责：递归遍历方法体，提取所有方法调用及其类型。

实现：
- 继承 `JavaRecursiveElementVisitor`。
- 重写 `visitMethodCallExpression`。
- 通过 `callExpression.methodExpression.getQualifierExpression()` 获取调用对象。
- 通过 `callExpression.methodExpression.referenceName` 获取方法名。
- 根据 qualifier 的文本和方法名判断 callType：
  - qualifier 包含 `Mapper` → MAPPER
  - qualifier 包含 `Redis`/`redis` 或方法名在 Redis 操作中 → REDIS
  - qualifier 包含 `Feign`/`Client` → FEIGN
  - 方法名为 `get`/`post`/`put`/`delete`/`exchange` 且来自 RestTemplate/WebClient → HTTP
  - 来自 `Thread`/`Executor`/`CompletableFuture` → THREAD
  - 其他 → UNKNOWN

### SpringSemanticAnalyzer

职责：检查类和方法的 Spring 注解语义。

方法：
- `analyzeClass(psiClass: PsiClass): SpringSemantic` — 检查类级别注解。
- `analyzeMethod(psiMethod: PsiMethod): List<String>` — 检查方法级别注解。

检查的注解：
- 类级别：`@Service`、`@Controller`、`@RestController`、`@Repository`、`@Component`、`@Configuration`
- 方法级别：`@Transactional`、`@Async`、`@Scheduled`、`@Cacheable`

### CodeContextBuilder

职责：串联 diff 解析和 PSI 分析，生成 `List<CodeContext>`。

流程：
1. 调用 DiffParser 解析 diff，得到 `Map<String, Set<Int>>`。
2. 对每个 Java 文件，通过 `PsiManager.getInstance(project).findFile(virtualFile)` 获取 PsiFile。
3. 调用 `JavaPsiAnalyzer` 提取类信息和修改方法。
4. 对每个修改方法，调用 `MethodCallExtractor` 提取调用。
5. 调用 `SpringSemanticAnalyzer` 识别语义。
6. 组装 `CodeContext` 返回。

注意事项：
- PSI 操作必须在 `ReadAction` 中执行。
- VirtualFile 通过 `LocalFileSystem.getInstance().findFileByPath(path)` 获取。
- 文件可能不在项目中（已删除等），需要 null check。

### ReviewPromptBuilder（修改）

替换现有 Prompt 结构，增加 PSI Context 区域。

新 Prompt 结构：

```
You are a senior code reviewer. Review the following staged git unified diff.

## Code Context

### File: UserService.java
Class: UserService
Package: com.demo.user.service
Annotations: @Service
Spring Semantic: SERVICE

Dependencies:
- UserMapper [AUTOWIRED]
- RedisTemplate [FINAL]

Modified Methods:
- createUser(UserDTO dto): String
  Annotations: @Transactional
  Calls:
  - userMapper.insert [MAPPER]
  - redisTemplate.opsForValue().set [REDIS]

### File: OrderController.java
...

## Diff

{unified diff}

## Focus Areas

- Bug 风险
- null pointer issues
- concurrency issues
- transaction issues
- SQL risk
- security issues
- readability

Return only a JSON array in this exact structure:
[
  {
    "level": "HIGH",
    "file": "UserService.java",
    "line": 42,
    "message": "问题描述（中文）"
  }
]

Use level HIGH, MEDIUM, or LOW. Use null for line when no exact line is available.
The message must be written in Chinese.
```

## 集成点

### ReviewOrchestrator 修改

```kotlin
// 现有
val prompt = promptBuilder.build(diff)

// 修改为
val codeContexts = CodeContextBuilder(project).buildFromDiff(diff)
val prompt = promptBuilder.build(diff, codeContexts)
```

`ReviewOrchestrator` 需要新增 `project: Project` 参数（已有构造函数传入）。

## 错误处理

- PSI 文件不可用（文件不在项目中、非 Java 文件）：跳过该文件，不报错。
- PSI 解析异常：跳过该文件，在 status 中提示。
- Diff 解析失败：回退到原始 prompt（无 context）。
- 无修改方法：只输出类级别信息。

## 测试策略

- `DiffParser` 测试：纯文本解析，不依赖 PSI。
- `MethodCallExtractor` 测试：需要 IntelliJ 测试框架（`BasePlatformTestCase`）。
- `SpringSemanticAnalyzer` 测试：需要 IntelliJ 测试框架。
- `ReviewPromptBuilder` 测试：验证 prompt 包含 context 和 diff。
- 集成测试：端到端验证完整流程。

## 交付物

- `context/CodeContext.kt` — 数据模型
- `context/MethodContext.kt` — 方法上下文模型
- `context/CodeContextBuilder.kt` — 核心构建器
- `psi/JavaPsiAnalyzer.kt` — PSI 分析器
- `psi/MethodCallExtractor.kt` — 方法调用提取器
- `analyzer/SpringSemanticAnalyzer.kt` — Spring 语义分析器
- 修改 `ReviewPromptBuilder.kt` — 新 Prompt 结构
- 修改 `ReviewOrchestrator.kt` — 集成 PSI Context
- 对应单元测试
