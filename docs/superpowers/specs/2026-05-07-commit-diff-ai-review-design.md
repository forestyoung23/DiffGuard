# CommitDiffAIReview MVP Design

Date: 2026-05-07

## Goal

Build an IntelliJ IDEA plugin MVP named `CommitDiffAIReview`. Before a user commits code, the plugin reviews the current staged Git diff with an OpenAI-compatible API and displays structured findings in an IntelliJ ToolWindow.

## Scope

### In scope

- IntelliJ IDEA 2024+ plugin using IntelliJ Platform SDK.
- Kotlin with Gradle Kotlin DSL.
- Swing UI.
- OkHttp for HTTP calls.
- kotlinx.serialization for JSON request/response handling.
- An `AI Review` action exposed from the Commit/VCS workflow.
- IntelliJ API-based staged unified diff collection; no shell `git` commands.
- OpenAI-compatible chat completions API integration.
- `AI Review` ToolWindow displaying risk level, file, line, and message.
- Global IDE settings for `baseUrl`, `apiKey`, and `model`.

### Out of scope

- Automatic fixes.
- Agent workflows.
- Vector databases.
- Multi-turn chat.
- Local models.
- User systems.
- Enterprise rules.
- Configuration center.
- Spring, JavaFX, Compose, databases, or external service frameworks.

## Architecture

The project is a small IntelliJ plugin with explicit package boundaries:

- `action`: user entry points, especially `AIReviewAction`.
- `git`: staged diff retrieval.
- `ai`: AI provider interface and OpenAI-compatible provider.
- `review`: prompt construction, response parsing, and review orchestration.
- `toolwindow`: Swing ToolWindow UI and result update service.
- `model`: review models and OpenAI DTOs.
- `settings`: global IDE configuration UI and persistence.

Primary flow:

1. User clicks `AI Review` from the Commit/VCS workflow.
2. `AIReviewAction` opens the `AI Review` ToolWindow and sets status to `Reviewing...`.
3. `GitStagedDiffProvider` collects the current staged unified diff using IntelliJ Git APIs.
4. If no staged diff exists, the ToolWindow shows `No staged changes to review.` and no AI request is sent.
5. `ReviewPromptBuilder` creates a structured review prompt from the diff.
6. `OpenAIProvider` sends the request to the configured OpenAI-compatible chat completions endpoint.
7. `ReviewResultParser` parses the response into `ReviewFinding` items.
8. The ToolWindow table displays findings.

## Components

### `AIReviewAction`

Responsibilities:

- Start the review when the user invokes `AI Review`.
- Keep orchestration thin and delegate Git, AI, parsing, and UI work.
- Run network/review work off the EDT.
- Update ToolWindow UI on the EDT.

It must not run shell commands or contain HTTP/JSON parsing details.

### `GitStagedDiffProvider`

Responsibilities:

- Use IntelliJ Git/Change APIs to obtain the staged/index unified diff.
- Support projects with one or more Git roots.
- Return a single combined diff string.
- Return a blank string when no staged changes exist.

The implementation must not execute `git diff` via shell. If API signatures differ across 2024.x builds, the implementation should remain within IntelliJ Platform/Git4Idea APIs, such as Git change utilities, content revisions, or diff providers.

### `AIProvider` and `OpenAIProvider`

`AIProvider` is the minimal abstraction:

```kotlin
interface AIProvider {
    suspend fun review(prompt: String): String
}
```

`OpenAIProvider` responsibilities:

- Read `baseUrl`, `apiKey`, and `model` from global settings.
- POST to the OpenAI-compatible `/v1/chat/completions` endpoint.
- Use OkHttp for HTTP and kotlinx.serialization for JSON.
- Send a system message plus the user review prompt.
- Return `choices[0].message.content`.
- Throw clear errors for missing API key, HTTP failures, malformed responses, and network failures.

Default settings:

- `baseUrl`: `https://api.openai.com`
- `apiKey`: empty
- `model`: `gpt-4o-mini`

### `ReviewPromptBuilder`

Responsibilities:

- Build a prompt that says the input is a staged unified diff.
- Ask the model to focus on:
  - Bug risk
  - Null pointer risk
  - Concurrency problems
  - Transaction problems
  - SQL risk
  - Security problems
  - Code readability
- Require a JSON array response only.

Expected response shape:

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

Rules:

- `level` must be `HIGH`, `MEDIUM`, or `LOW`.
- `line` may be `null` when the line cannot be identified.
- Return `[]` when no issues are found.
- Do not return Markdown or explanatory text.

### `ReviewResultParser`

Responsibilities:

- Parse model output into `ReviewFinding` objects.
- Support:
  - Pure JSON array.
  - Markdown fenced JSON code blocks.
  - The first JSON array embedded in surrounding text.
- If parsing fails, return one fallback finding:
  - `level = LOW`
  - `file = AI Response`
  - `line = null`
  - `message = truncated raw model response`

This avoids silent failure and keeps the ToolWindow useful even when the model violates the format.

### ToolWindow

ToolWindow ID: `AI Review`

UI:

- Swing-based.
- Top status label: `Ready`, `Reviewing...`, `No staged changes to review.`, `Error: ...`, `No issues found.`, or `N findings`.
- Main `JBTable` columns:
  - `Level`
  - `File`
  - `Line`
  - `Message`
- Action invocation automatically opens the ToolWindow and refreshes the table.

### Settings

Settings page:

`Settings / Tools / CommitDiffAIReview`

Fields:

- `Base URL`
- `API Key`
- `Model`

Configuration is IDE-global through `PersistentStateComponent`. The API key is stored in IDE configuration for MVP simplicity; PasswordSafe integration is intentionally excluded from the MVP.

## Error Handling

- No project: action exits.
- No staged diff: show `No staged changes to review.` and skip AI request.
- Missing API key: show a settings hint and skip AI request.
- HTTP non-2xx: show an error summary in the ToolWindow.
- Network exception: show an error summary in the ToolWindow.
- Invalid AI JSON: create the fallback LOW finding with raw response content.
- Long-running review: run in a background coroutine/task and update Swing UI on the EDT.

## Testing and Verification

Automated tests:

- Prompt builder includes all required review categories.
- Parser handles pure JSON arrays.
- Parser handles fenced `json` code blocks.
- Parser handles a JSON array embedded in text.
- Parser falls back on invalid output.

Manual verification:

- `./gradlew test` passes.
- `./gradlew runIde` starts the plugin sandbox.
- Settings page saves and reloads configuration.
- Staged changes can be reviewed without shell Git commands.
- ToolWindow shows findings, empty results, no staged changes, and API errors.

## Deliverables

- Complete Gradle Kotlin DSL project.
- `plugin.xml` with action, ToolWindow, and settings registration.
- Kotlin source code for all packages.
- OpenAI-compatible API implementation.
- IntelliJ API-based staged diff implementation.
- JSON parsing implementation.
- README with setup, configuration, run, and usage instructions.
