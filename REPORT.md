# Pass W5 — Home + Settings + drawer screens: report

Branch `pass/w5-home-settings`, base `ec82b40` (v2.41.4). Not rebased: `origin/main` carries no `docs/reports/pass-w5-theme.md` at the time of this report (checked `git fetch origin && git show origin/main:docs/reports/pass-w5-theme.md` → absent; latest `origin/main` is `fbe8b4f`, the W5 chat report). Everything that needs no new theme token is done; the token-dependent remainder of parts 1 and 3 is listed under "Blocked on theme". No emulator, device or push.

## Commits (oldest first)

| Commit | Part | Summary |
|---|---|---|
| `9299edb` | 5 + rulings | "Retry" → "Try again" (home, bookmarks, settings); one-phrase snackbars drop the trailing period; fetch-models snackbar fixed copy; `NativeAuthManager` ellipsis |
| `a6e076f` | 2, 4 | `ExpressiveSnackbar` hosts on Bookmarks / Workflows / Memories with typed failure events; Insights + MindMap bars join the small-bar family |
| `5aa1f9b` | 1 (token-free half) | five private full-screen error layouts folded onto the shared `LoadErrorContent` with fixed action order |
| `717e383` | 1, 6 | `Error*` contrast slots on `HomeSettingsSlot`; masthead title row no longer clips at font scale 1.3 |
| `27d1305` | 6 | masthead wordmark scales from the start edge under RTL |
| (this file) | 7 | REPORT.md |

## Gates (at `27d1305`)

- `:app:compileGithubDebugKotlin` rc=0 and `:app:compileGithubDebugAndroidTestKotlin` rc=0 before every commit.
- `:app:testGithubDebugUnitTest`: **714 tests, 0 failures, 0 errors, 0 skipped** (708 on `ec82b40` + 6 added here).
- `git diff ec82b40`: 23 files, 445+/247−. `FontStyle.Italic`: 0. `testTag(` removed: 0. `contentDescription` removed: one `contentDescription = null` line — the decorative `Icons.Default.Error` glyph inside the deleted private `ExecutionErrorContent` (`WorkflowExecutionScreen.kt`), replaced by the shared component's hero glyph which is also decorative. No labelled description removed. `scripts/`, `.github/`, `ui/theme/**`, `chat/**`, `assistant/**`, `SmoothStreamingText.kt`, preference keys, nav graph, swipe order, launcher icon: untouched.

## Tests added (6)

| File | Count | Pins |
|---|---|---|
| `ui/screens/bookmarks/BookmarksViewModelTest.kt` | 2 | failing `removeBookmark` / `updateBookmark` emit `BookmarksEvent.ShowSnackbar` with fixed copy (no exception text), list state untouched |
| `ui/screens/workflows/WorkflowsViewModelEventsTest.kt` | 2 | failing `delete` emits `WorkflowsEvent.ShowSnackbar("Could not delete workflow")`; success emits nothing |
| `ui/screens/memories/MemoriesViewModelEventsTest.kt` | 2 | failing `deleteMemory` / `deleteAllMemories` emit the snackbar event instead of crashing the scope |

## Orchestrator rulings applied (`9299edb`)

| Ruling | File:line | Change |
|---|---|---|
| action verb "Try again" | `ConversationsScreen.kt:1317`, `BookmarksScreen.kt:170`, `SettingsScreen.kt:2860` (pre-fold lines) | `"Retry"` → `"Try again"`; after `5aa1f9b` all three read `LoadErrorContent.ERROR_RETRY_LABEL` (`ui/components/LoadError.kt`) so there is one string |
| one-phrase snackbar, no period | `SettingsViewModel.kt:666,681,709` | trailing period dropped (`:131` and `OnDeviceModelsViewModel.kt:67,75,102,112` were already period-free; verified, no change) |
| no exception text | `SettingsViewModel.kt:719` | fetch-models failure snackbar `error.message ?: …` → `"Could not fetch models"`; the inline `modelsError` (`:712-716`) keeps the provider message as the form diagnostic, same rule as W4's `authStatus` |
| ellipsis | `data/auth/NativeAuthManager.kt:185,481` | `...` → `\u2026` (the only two lines touched in that file) |
| `MindMapCanvas.kt:512` ellipsis | — | already on `MindMapLabelLayout.ELLIPSIS` (`\u2026`) since W4; line 512 is the node-background `drawRoundRect`; no change |

Remaining `"Retry"` under my ownership after this pass: none (grep).

## Part 2 — snackbar hosts (`a6e076f`)

Per-screen justification:

- **Bookmarks** (`BookmarksScreen.kt:88-89` host; `BookmarksViewModel.kt:43-44,193,206`): `deleteBookmark` and `updateBookmark` swallowed exceptions silently (`catch (_: Exception) {}`), so a failed swipe-delete left the card on screen with no explanation. Now a `BookmarksEvent.ShowSnackbar` with fixed copy; load errors stay on the full-screen error state (already visible). New `bookmarks/BookmarksEvent.kt`.
- **Workflows** (`WorkflowsScreen.kt:95-96`; `WorkflowsViewModel.kt:36-37,88`): `deleteWorkflow` was best-effort silent; same treatment. Load errors already go through `WorkflowsUiState.Error`. New `workflows/WorkflowsEvent.kt`.
- **Memories** (`MemoriesScreen.kt:98-99`; `MemoriesViewModel.kt:42-43,105,112`): `deleteMemory` / `deleteAllMemories` had **no catch at all** — a repository throw would have crashed the `viewModelScope`. Wrapped in `runCatching`, failure → snackbar. New `memories/MemoriesEvent.kt`.
- Events are `MutableSharedFlow(extraBufferCapacity = 4)` collected in a `LaunchedEffect(Unit)` and shown through the existing `ExpressiveSnackbar` (same host shape as Personas / OnDeviceModels), so the F1 snackbar family is unchanged.

## Part 4 — top-bar family rule (`a6e076f`)

Rule recorded: **Large collapsing bar (`LargeTopAppBar` + `exitUntilCollapsed`) only for the two top-level destinations reached from the nav bar (Home `ConversationsScreen.kt:228`, Settings `SettingsScreen.kt:425-431`). Every pushed screen uses the small `TopAppBar` with `ExpressiveTopBarTitle(title, subtitle)` and an `ExpressiveFilledIconButton` back control, `containerColor = surfaceContainer`.** The 13 small-bar screens already followed this; the two outliers were moved:

| File:line | Was | Now |
|---|---|---|
| `insights/components/InsightsTopBar.kt:30,44` | `CenterAlignedTopAppBar` + `centerAlignedTopAppBarColors` | `TopAppBar` + `topAppBarColors` |
| `mindmap/components/MindMapTopBar.kt:30,44` | same | same |

`canvas/components/CanvasTopBar.kt:74` is already a small `TopAppBar` (it has its own action cluster; left as is). Chat's custom masthead is the chat owner's.

## Part 1 — error content, token-free half (`5aa1f9b`, `717e383`)

Theme's `ErrorContent` has not shipped, so the five private layouts were folded onto the component I own, `ui/components/LoadError.kt`, which is now the single site to repoint when the theme component lands:

- `LoadErrorContent(title, message, modifier, onRetry?, onBack?)` (`LoadError.kt:35-50`): fixed order — "Try again" as the filled primary action, then "Go back" as a low-emphasis text action; constants `ERROR_RETRY_LABEL` / `ERROR_BACK_LABEL`.
- `HomeEmptyState` gained `secondaryAction: EmptyStateAction? = null` (`HomeEmptyState.kt:72,84,128-138`) drawn as a `TextButton` under the primary, so Bookmarks / Personas / WorkflowExecution keep their "Go back".

| Screen | File:line (call) | Deleted private layout | Copy |
|---|---|---|---|
| Home | `ConversationsScreen.kt:847` | `ErrorContent` (was `:1286`) | "Conversations could not be loaded" / fixed body; retry |
| Settings | `SettingsScreen.kt:785` | `ErrorContent` (was `:2829`) | "Settings could not be loaded"; retry |
| Bookmarks | `BookmarksScreen.kt:121` | `BookmarksErrorContent` (was `:142`) | retry + back |
| Personas | `PersonaStudioScreen.kt:139` | `PersonaStudioErrorContent` (was `:222`) | back only (view-model re-observes; the old `onRetry` was a no-op lambda) |
| Workflow execution | `WorkflowExecutionScreen.kt:139` | `ExecutionErrorContent` (was `:245`) | back only |

The exception-derived `message` strings these screens used to display (`e.message ?: "Failed to load …"` in `SettingsViewModel.kt:339`, `ConversationsViewModel.kt:116`, `PersonaStudioViewModel.kt:45`, `WorkflowExecutionViewModel.kt:94`) are no longer shown; the state fields are unchanged so the view-model tests still pass. `SearchErrorContent` (`ConversationsScreen.kt:~1495`, inline search failure) is a two-line inline notice inside the search sheet, not a full-screen state; left as is.

`Error*` slots (`ui/components/HomeSettingsContrast.kt:128-138`): `ErrorHeadline`, `ErrorBody`, `ErrorHeadlineOnContainer`, `ErrorBodyOnContainer` (Personas draws on the Scaffold `surfaceContainer`, the others inside `ExpressiveContentSurface` on `surfaceContainerLow`), `ErrorHeroIcon` (`onSecondaryContainer` on `secondaryContainer`), `ErrorPrimaryAction` (`onPrimary` on `primary`), `ErrorSecondaryAction[OnContainer]` (`primary` on the surface). They are enumerated by the existing `ThemeContrastTest.homeSettingsPaletteReadsOnEveryFillInEveryTheme` across all 39 appearances — green.

## Part 6 — static walk at 360dp / font scale 1.3 / RTL

Method: grep over `conversations/**`, `settings/**`, `onboarding/**`, `insights/**` for fixed `width`/`height` on text containers, `maxLines = 1` without overflow, `TransformOrigin`, `Alignment.*Left/Right`, `offset(x =` with absolute values, non-mirrored directional icons, and `.dp` text sizes; then each hit re-opened.

Fixed:
- `ConversationsScreen.kt:586-589` — masthead title row was `height(44.dp)` with `headlineLarge` inside `clipToBounds()`. headlineLarge line height is 40sp = 52dp at 1.3, so the wordmark clipped ~4dp top and bottom. Now `heightIn(min = 44.dp)` (row still aligns with the 44dp icon buttons at scale 1.0).
- `ConversationsScreen.kt:595` — `TransformOrigin(0f, 0.5f)` scaled the wordmark from the left edge; the row is `BottomStart`-aligned so under RTL it scaled away from its anchor. Origin now follows `LocalLayoutDirection` (`1f` under RTL).

Measured and left (nothing clearly wrong):
- `SettingsScreen.kt:1804,1910` — 128dp / 124dp preview cards: inside `LazyRow`s (`:1779,:1884`), so 360dp width scrolls; the label is `labelLarge` `maxLines=1` + ellipsis.
- All 25 `maxLines = 1` sites in the four areas carry `TextOverflow.Ellipsis`; `RowDateLabel.kt:25` uses `Visible` + `softWrap=false` deliberately (W3: date keeps intrinsic width, model name absorbs the squeeze).
- Rows with a trailing control (`ConversationItem.kt:179`, `ExpandableConversationGroup.kt:237`, `ProviderCard.kt:175`, `SettingsExpressive.kt:300`, `ResponseTimeChart.kt:113`) weight the text column.
- `BranchConversationItem.kt:123` `height(56.dp)` is the connector stroke, not text. `ModelComparisonCard.kt:84` 80dp is an empty-state box with one `bodyMedium` line (20sp×1.3 = 26dp). `ModelUsageChart.kt:99,113` 180dp and `ResponseTimeChart.kt:83` 100dp are chart canvases with no text inside.
- Onboarding: all fixed sizes (`OnboardingScreen.kt:201-475`) are icons/tiles; copy is in unconstrained `Column`s.
- No `Alignment.CenterLeft/Right`, no absolute `offset(x=`, no `.dp` text sizes, no non-mirrored `ArrowBack`/`ArrowForward` in the four areas.

Not measured here: `ExpressiveSurfaces.kt:139` has the same left-edge `TransformOrigin(0f, 0.5f)` pattern (theme-owned, listed below).

## Blocked on theme (parts 1 and 3 remainder)

To do after `git fetch origin && git rebase origin/main` once `docs/reports/pass-w5-theme.md` exists:
1. Repoint `LoadErrorContent` (`ui/components/LoadError.kt:35`) onto theme `ErrorContent`, or make it a thin alias — one site now.
2. Implement theme `ContrastSlot` on `HomeSettingsSlot` (`HomeSettingsContrast.kt:24`).
3. M3 chip sites from audit F4 onto the theme chip defaults / `ExpressiveSizes.Chip`: `SettingsScreen.kt:2563,2664`, `ModelAssignmentsScreen.kt:667,696`, `InteractionSettingsScreen.kt:208,236`, `InsightsScreen.kt:148`, `BookmarkFilterBar.kt:80,134`, `BookmarkCard.kt:153`, `AddBookmarkSheet.kt:120,161`, `PersonaEditorSheet.kt:169`, `PersonaCard.kt:184`, `ArenaModelPicker.kt:75,134` (line numbers from the W4 audit at `bb91963`; re-grep after rebase).
4. 44dp targets → `ExpressiveSizes.CompactTarget`: `ConversationsScreen.kt:589` (`heightIn(min = 44.dp)`, this pass), `HomeEmptyState.kt:221`, `SettingsExpressive.kt:70`.
5. `StepEditor.kt:65` 14dp step badge → `TileBadge`.
6. `ExpressiveSnackbar` onto `ExpressiveShapes.Notice` if the theme ships it (`ExpressiveSnackbar.kt:26`, owned).

## Deliberately left alone
- `ExpressiveEmptyState` type pair (F11): theme owns `ExpressiveSurfaces.kt`; `LoadErrorContent` inherits whatever it draws.
- `SearchErrorContent` (`ConversationsScreen.kt`): inline, two lines, inside the search sheet; not a full-screen error.
- Inline form diagnostics (`modelsError`, `authStatus`, `apiKeyError`) keep provider messages — W4 ruling; only snackbars and full-screen states use fixed copy.
- `CanvasTopBar.kt:74`: already small-bar; its action cluster is canvas-specific.
- `WorkflowExecutionViewModel`/`OnboardingViewModel` `error: String?` fields: carried from W4.

## Needs another owner
- **Theme**: `ExpressiveSurfaces.kt:139` `TransformOrigin(0f, 0.5f)` — same RTL defect as the masthead; should read `LocalLayoutDirection`.
- **Theme**: `ErrorContent`, `ContrastSlot`, `ExpressiveSizes` (Chip / CompactTarget), `TileBadge`, `ExpressiveShapes.Notice` — consumers listed under "Blocked on theme".
- **Chat**: `ChatScreen.kt:600`, `MessageBubble.kt:1720` still say "Retry" (ruling is "Try again" app-wide).
- **Orchestrator**: `OnDeviceModelsViewModel.kt:56,93,103,113` already use fixed copy (W4); nothing left there.

## Not verifiable without a device
- Snackbar hosts on Bookmarks / Workflows / Memories: JVM tests prove the events fire; the rendered snackbar position over the FAB (Workflows) and the list bottom inset were not seen.
- Insights / MindMap bars: start-aligned title with subtitle vs the previous centred layout; no screenshot.
- Folded error states: `HomeEmptyState` hero cluster + two stacked buttons at 360dp / 1.3 — the hero is 32dp padding + cluster + headlineMedium; expected to fit but not rasterised.
- Masthead at font scale 1.3: `heightIn` math (52dp line in a ≥44dp row) is reasoned, not rendered; the collapsed-bar alignment with the icon buttons at scale 1.3 may shift by the extra 8dp.
- RTL transform origin: not exercised under `LayoutDirection.Rtl`.
- `Error*` contrast slots are numeric WCAG checks on the resolved scheme; rendered elevation tint is not covered.
