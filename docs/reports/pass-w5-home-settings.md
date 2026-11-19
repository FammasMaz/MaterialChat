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

## Part 7 — theme rows (after rebase onto `c22e062`)

Rebased with nothing to replay (the six W5 commits were already on `origin/main`). `MainActivity.kt` (`17170a9`, OPEN_SETTINGS routing) untouched.

### Commits

| Commit | Part |
|---|---|
| `17e37b2` | 7a — `ErrorContent` repoint, retry hooks, `ContrastSlotSet`, dialog action colours, masthead target token |
| `60dff1f` | 7b — chip shape, size tokens, `TileBadge`/`Tile`, indicator tokens |

### Gates (at `60dff1f`)

`:app:compileGithubDebugKotlin` and `:app:compileGithubDebugAndroidTestKotlin` rc=0 before each commit; `:app:testGithubDebugUnitTest` **746 tests, 0 failures** (3 test files added in 7a: `PersonaStudioViewModelTest` (1), `WorkflowExecutionViewModelRetryTest` (1), `HomeSettingsContrastSlotsTest` (1)). No existing test, testTag, contentDescription, `scripts/` or `.github/` file touched; no italics.

### 7a (`17e37b2`)

| File:line | Change |
|---|---|
| `ui/components/LoadError.kt:31-47` | `LoadErrorContent` is a thin alias over the shared `ui/components/ErrorContent` (error tile on `errorContainer`, `headlineSmall` Bold, `bodyMedium`, Card on `surfaceContainer`); `onRetry` is now required, `onBack` maps to `secondaryLabel = "Go back"` / `onSecondary` so the order is always Try again, then Go back; `ERROR_RETRY_LABEL = ErrorContentCopy.TRY_AGAIN` |
| `personas/PersonaStudioViewModel.kt` (`retry()`), `personas/PersonaStudioScreen.kt:139` | Personas had no reload; `retry()` returns to `Loading` and re-observes, the screen passes `onRetry = viewModel::retry` plus `onBack` |
| `workflows/WorkflowExecutionViewModel.kt` (`retry()`), `workflows/WorkflowExecutionScreen.kt:139` | `retry()` clears `error`, sets `isLoading` and reloads the workflow; screen passes it plus `onBack` |
| `conversations/ConversationsScreen.kt:847`, `bookmarks/BookmarksScreen.kt:121`, `settings/SettingsScreen.kt:785` | unchanged call sites, now render the shared card through the alias |
| `conversations/HomeEmptyState.kt` | `secondaryAction` (added in `5aa1f9b` for the fold) removed again: no consumer after the repoint |
| `ui/components/HomeSettingsContrast.kt` | `HomeSettingsPalette` implements `ContrastSlotSet` (`surfaceName = "home-settings"`, `slots = pairs()`); new `HomeSettingsContrastSlots : ContrastSlotResolver` so the theme gate can drop its adapter at `ThemeContrastTest.kt:55-58` |
| `ui/components/DialogActionColors.kt` (new) | `dialogActionButtonColors()` / `dialogDestructiveButtonColors()` = `ButtonDefaults.textButtonColors(contentColor = dialogActionColor() / dialogDestructiveColor())` |
| `ui/components/ExpressiveButton.kt:64-66,77,166-170` | optional `contentColor` for the Text style (filled styles ignore it) so `AlertDialog` hosts built on `ExpressiveButton` can draw the resolved colour |
| `memories/MemoriesScreen.kt:162,173` | Delete all → `dialogDestructiveButtonColors()` (raw `Text(color = error)` dropped), Cancel → `dialogActionButtonColors()` |
| `settings/SettingsScreen.kt:2694,2745,2795` | backup-passphrase, restore-confirmation and delete-provider dialog buttons on `dialogActionColor()`; the Delete action on `dialogDestructiveColor()` |
| `conversations/ConversationsScreen.kt:336-425` (rename dialog: Retry with AI, Delete, Archive, Save, Cancel), `:904` (info dialog Done), `:918` (`MetadataRow` label) | `dialogActionButtonColors()` / `dialogDestructiveButtonColors()` / `dialogActionColor()`; the `HomeSettingsSlot.DialogLabel` read is replaced by the theme-level resolution (same value, one source) |
| `settings/OnDeviceModelsScreen.kt:183-215` | not in the brief's list but the same dialog container: Delete / Got it / Cancel on the resolved colours |
| `conversations/ConversationsScreen.kt:589` | masthead row `heightIn(min = ExpressiveSizes.CompactTarget)` |

### 7b (`60dff1f`)

| File:line | Change |
|---|---|
| `settings/SettingsScreen.kt:2566,2667`; `settings/ModelAssignmentsScreen.kt:667,696`; `settings/InteractionSettingsScreen.kt:208,236`; `insights/InsightsScreen.kt:148`; `bookmarks/components/BookmarkFilterBar.kt:80,134`; `bookmarks/components/BookmarkCard.kt:153`; `bookmarks/components/AddBookmarkSheet.kt:121,162`; `personas/components/PersonaEditorSheet.kt:170` (was `CustomShapes.Pill`); `personas/components/PersonaCard.kt:185` (was `ExpressiveShapes.Badge`); `arena/components/ArenaModelPicker.kt:75,134` | 16 M3 chip sites → `shape = ExpressiveShapes.Chip` (the brief counted 14; the two persona chips already carried a non-default shape and were migrated too) |
| `conversations/HomeEmptyState.kt:222`, `settings/components/SettingsExpressive.kt:70` | 44dp → `ExpressiveSizes.CompactTarget` |
| `workflows/components/StepEditor.kt:65-67` | `RoundedCornerShape(14.dp)` / `size(32.dp)` → `ExpressiveShapes.TileBadge` / `ExpressiveSizes.Compact` |
| `settings/components/SystemPromptField.kt:122` | `CustomShapes.Fab` → `ExpressiveShapes.Tile` (last consumer of the deprecated token) |
| `settings/SettingsScreen.kt:2319` (18), `:2427` (16), `settings/ModelAssignmentsScreen.kt:438` (20) | → `ExpressiveSizes.IndicatorInline` (20; two sites grow by 2–4dp) |
| `settings/SettingsScreen.kt:807`, `conversations/ConversationsScreen.kt:1032,1385` | 48 → `ExpressiveSizes.IndicatorHero` |

### Deviation from the brief

- `HomeSettingsSlot.Snackbar*` / `Dialog*` slots were **kept**, not dropped: the theme-owned `ThemeContrastTest.kt:133-137,164-165` and `HomeSettingsContrastTest.kt:66` pin them by name, so removing them would mean editing tests I may not weaken. The theme report's own migration row also says keep them. **Needs theme owner**: delete the pins, then this owner removes the five slots.
- `ThemeContrastTest.kt:55-58` adapter can now collapse to `HomeSettingsContrastSlots` — theme-owned test file, not edited here.

### Not verifiable without a device

- The shared `ErrorContent` card inside each of the five screens (home list weight slot, settings content surface, bookmarks, personas on the scaffold, workflow execution) at 360dp / font scale 1.3.
- Dialog action labels on VIOLET light / CITRUS light: the resolved colour falls back to `onSurface` there (pinned numerically), the rendered look of a non-primary dialog button was not seen.
- Capsule chips next to their neighbours (settings model/format pickers, bookmark filter bar, arena picker, persona tag chips) — rendered only in the JVM's absence.
- `StepEditor` badge: 12dp squircle on a 32dp tile; `SystemPromptField` 18dp squircle on a 40dp box.
- Indicator growth 16/18 → 20 inside the two settings rows.
