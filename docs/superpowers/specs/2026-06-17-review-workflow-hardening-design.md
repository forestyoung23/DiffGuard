# Review Workflow Hardening Design

## Goal

Make the real DiffGuard review path use workspace rules and ignore files, improve cancellation and long-running behavior, reduce IDE read-lock pressure, and harden provider/settings edge cases without replacing the current MVP architecture.

## Architecture

The existing `ReviewOrchestrator` remains the central workflow coordinator. It will load optional `.ai-review` workspace context, filter ignored diff sections before PSI and prompt construction, and build prompts through `PromptContextBuilder`.

Cancellation is modeled as a small token passed through orchestrator and provider calls. The IntelliJ background task owns the token and maps `ProgressIndicator` cancellation into AI call cancellation.

PSI analysis keeps its public synchronous API but limits work and narrows read actions per file. More invasive async PSI refactoring is deferred.

## Scope

- Use `.ai-review/rules.md`, `architecture.md`, `review.md`, and `ignore.md` in real reviews.
- Treat ignored diff sections as absent from both prompt and PSI context.
- Let users cancel review tasks, including in-flight HTTP calls.
- Avoid one large read action for all changed Java files.
- Prevent multi-root unstaged filtering from comparing unrelated repository-relative paths.
- Improve provider error messages for empty responses, truncation, and structured API errors.
- Avoid loading the saved API key into the settings text field as editable plain model state.
- Move detailed PSI context logs to debug.

## Testing

Each behavior change gets a focused unit test first. Final verification is `./gradlew test`.
