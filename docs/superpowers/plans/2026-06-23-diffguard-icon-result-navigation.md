# DiffGuard Icon and Result Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a custom DG Mark plugin icon and make review findings clickable so users can jump to the related file and line.

**Architecture:** Keep rendering and navigation separate. The renderer exposes clickable finding cards through a callback, while the tool window view wires that callback to a project-aware navigator. Plugin icons are static SVG resources registered from `plugin.xml`.

**Tech Stack:** Kotlin, IntelliJ Platform Swing UI DSL, IntelliJ VirtualFile/OpenFileDescriptor APIs, JUnit 5, Gradle IntelliJ plugin.

---

## File Structure

- Create `src/main/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigator.kt`: project-aware file resolution, editor navigation, and missing-file notification hook.
- Modify `src/main/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`: add optional click callback and make finding cards/location rows clickable.
- Modify `src/main/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowFactory.kt`: pass `Project` into the view and wire the default navigator.
- Create `src/main/resources/icons/diffguard.svg`: DG Mark icon.
- Modify `src/main/resources/META-INF/plugin.xml`: register the icon for tool window and review action.
- Modify `src/test/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`: prove click callback behavior without HTML editor panes.
- Modify `src/test/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`: adapt view construction to project-optional constructor.
- Create `src/test/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigatorTest.kt`: verify path/line resolution behavior using a fake opener/reporter.

## Task 1: Renderer Click Callback

**Files:**
- Modify: `src/main/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRenderer.kt`
- Test: `src/test/kotlin/dev/diffguard/toolwindow/AIReviewResultPanelRendererTest.kt`

- [ ] **Step 1: Write failing renderer callback test**

Add a test that renders a finding with `onFindingSelected`, finds the location text component, clicks it through its mouse listeners, and asserts the original `ReviewFinding` was received.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew test --tests dev.diffguard.toolwindow.AIReviewResultPanelRendererTest`

Expected: compile failure or assertion failure because the renderer constructor does not yet accept `onFindingSelected`.

- [ ] **Step 3: Implement minimal clickable renderer**

Update the renderer constructor to accept `private val onFindingSelected: ((ReviewFinding) -> Unit)? = null`. In `findingCard`, attach a hand cursor, tooltip, and mouse listener to the card and location row when the callback is present. Invoke the callback with the clicked finding.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew test --tests dev.diffguard.toolwindow.AIReviewResultPanelRendererTest`

Expected: PASS.

## Task 2: Navigator

**Files:**
- Create: `src/main/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigator.kt`
- Test: `src/test/kotlin/dev/diffguard/toolwindow/ReviewFindingNavigatorTest.kt`

- [ ] **Step 1: Write failing navigator tests**

Test that a finding file path resolves relative to a supplied base path, converts one-based line numbers to zero-based editor line numbers, uses zero for missing/invalid lines, and reports missing files.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew test --tests dev.diffguard.toolwindow.ReviewFindingNavigatorTest`

Expected: compile failure because `ReviewFindingNavigator` does not exist.

- [ ] **Step 3: Implement navigator with injectable dependencies**

Create `ReviewFindingNavigator` with constructor dependencies for `basePath`, file existence lookup, opener, and missing-file reporter. Add a companion `forProject(project: Project)` that uses `LocalFileSystem`, `OpenFileDescriptor`, and `NotificationGroupManager`.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew test --tests dev.diffguard.toolwindow.ReviewFindingNavigatorTest`

Expected: PASS.

## Task 3: Wire Tool Window Navigation

**Files:**
- Modify: `src/main/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowFactory.kt`
- Test: `src/test/kotlin/dev/diffguard/toolwindow/AIReviewToolWindowViewTest.kt`

- [ ] **Step 1: Write/update view wiring test**

Add a test that constructs `AIReviewToolWindowView(onFindingSelected = { ... })`, renders findings, clicks the location text, and asserts the callback receives the finding.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew test --tests dev.diffguard.toolwindow.AIReviewToolWindowViewTest`

Expected: compile failure or assertion failure because view wiring does not expose the callback yet.

- [ ] **Step 3: Implement view wiring**

Let `AIReviewToolWindowView` accept `onFindingSelected: ((ReviewFinding) -> Unit)? = null` and initialize `AIReviewResultPanelRenderer(onFindingSelected)`. In `AIReviewToolWindowFactory`, construct `ReviewFindingNavigator.forProject(project)` and pass `navigator::navigate`.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew test --tests dev.diffguard.toolwindow.AIReviewToolWindowViewTest`

Expected: PASS.

## Task 4: Icon Resource Registration

**Files:**
- Create: `src/main/resources/icons/diffguard.svg`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Add DG Mark SVG**

Create a 16x16-friendly SVG using the approved DG Mark direction. Use a blue rounded base, white DG strokes, and a green check/guard accent.

- [ ] **Step 2: Register icon**

Add `icon="/icons/diffguard.svg"` to the `toolWindow` element and the `DiffGuard.AIReviewAction` action element in `plugin.xml`.

- [ ] **Step 3: Run plugin build**

Run: `./gradlew buildPlugin`

Expected: PASS and no missing resource errors.

## Task 5: Final Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew test --tests dev.diffguard.toolwindow.*`

Expected: PASS.

- [ ] **Step 2: Run full verification**

Run: `./gradlew test buildPlugin`

Expected: PASS.

- [ ] **Step 3: Review changed files**

Run: `git diff -- src/main/kotlin/dev/diffguard/toolwindow src/main/resources/META-INF/plugin.xml src/main/resources/icons src/test/kotlin/dev/diffguard/toolwindow`

Expected: changes match the design, with no unrelated edits.
