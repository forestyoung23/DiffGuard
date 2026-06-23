# DiffGuard Icon and Result Navigation Design

## Context

DiffGuard currently uses the default IntelliJ tool window icon. The review results panel renders findings as static Swing cards, even though each `ReviewFinding` already carries a file path and optional line number.

## Goals

- Add a custom DiffGuard icon using the approved DG Mark direction.
- Use the icon for the DiffGuard tool window and review action.
- Allow users to click a review finding location and jump to the corresponding project file and line.
- Keep result text rendered as plain Swing text, preserving the current HTML injection protection.

## Non-Goals

- No remote model list or settings changes in this work.
- No redesign of the full results UI.
- No changes to AI parsing beyond using the existing `ReviewFinding.file` and `ReviewFinding.line` fields.

## Icon Design

The icon will be a checked-in SVG under plugin resources. It will use a compact DG mark: blue rounded square base, white DG form, and green guard/check accent. The icon should remain legible at IntelliJ toolbar/tool-window sizes.

`plugin.xml` will register the icon for:

- `toolWindow` `icon`
- `DiffGuard.AIReviewAction` `icon`

## Result Navigation

The result renderer will accept an optional finding selection callback. Finding cards with a project location will expose a clickable location row. Clicking it sends the `ReviewFinding` to the callback.

Navigation will live outside the renderer. The tool window view will receive a navigator dependency and pass a callback into the renderer. The default navigator will:

- Resolve `ReviewFinding.file` against the project base directory.
- Open the file with `OpenFileDescriptor`.
- Jump to `line - 1` when `line` is present and positive.
- Open the first line when no valid line is present.
- Show a lightweight notification when the file cannot be found.

This keeps the renderer testable and avoids coupling pure UI rendering to project file-system APIs.

## Testing

- Add renderer tests proving clickable findings invoke the callback and plain text rendering remains intact.
- Add navigator tests for opening a resolved file and reporting a missing file when practical in the IntelliJ test framework.
- Keep existing tool window tests passing.
- Run the focused tool window/settings tests, then `buildPlugin`.
