# Pass W7 — home + settings: report

Branch `pass/w7-home-settings`, base `7e6f6624` (v2.41.6), rebased onto `origin/main` at `213efa87` (W7 theme merge `4bf268d3`) after Part 7/8. Static work only: no device, no emulator, no screenshot, no push. `./gradlew --stop` run at the end.

## Commits (oldest first, post-rebase hashes)

| Commit | Part | Summary |
|---|---|---|
| `2d42bdba` | 1 | Settings bubble-style preview draws chat's `bubbleShapeFor`; local radii table deleted (superseded by `f6bf1775`, see below) |
| `2136eda5` | 2, 3 | Exception text never reaches the user; "Could not …" verb family; `HomeSettingsCopyRulingsTest` scanner |
| `61a06965` | 4, 5 | `HomeEmptyState` type pair + `ExpressiveButton`; `ArenaVotingBar` and the two onboarding buttons onto `ExpressiveButton` |
| `0878bd51` | 7, 8 | Search error onto `ErrorContent` with `SearchViewModel.retry()`; `PersonaCard` / `ConversationStarterChips` on `ExpressiveShapes`; arena composer off `CustomShapes.MessageInputContainer`; typographic ellipsis in the six data/domain sites; dead `CanvasRefinementInput` deleted |
| `f6bf1775` | theme rows H1–H5 | Bubble preview on `ExpressiveShapes.Bubble.*` (chat import dropped); `MindMapCanvas` on `Tweens.linear`; every failure line through `CopyRules.failureLine`; voice-settings notices pass `CopyRules.isNotice` |
| `79dcb1a8` | 6, icon ladder, top-bar rule | Text/icons on alpha washes gated as `HomeSettingsSlot` entries; icon literals onto `ExpressiveSizes.Icon/IconSmall/IconLarge`; `HomeSettingsTopBarRuleTest` |
| (this) | — | REPORT.md |

## Gates

`JAVA_HOME=temurin-17 ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` rc=0 and `:app:testGithubDebugUnitTest` 0 failures before every commit:

| After | Unit tests |
|---|---|
| Part 1 | 796 |
| Parts 2+3 | 802 (+6 `HomeSettingsCopyRulingsTest`) |
| Parts 4+5 | 802 |
| Parts 7+8 | 804 (+2 `SearchViewModelTest`) |
| rebase + theme rows | 811 (theme merge +7) |
| Part 6 + icons + top-bar rule | **815** (+4 `HomeSettingsTopBarRuleTest`), 0 failures, 0 errors, 0 skipped |

`FontStyle.Italic`: 0. `testTag(` / `contentDescription` removed: 0. `scripts/`, `.github/`, `ui/theme/**`, `ui/screens/chat/**`, `assistant/**`, `MainActivity.kt`, `SmoothStreamingText.kt`: untouched. No androidTest added (judge minimum unchanged). `local.properties` not committed.

One slip: `f6bf1775` was first committed with 3 red pins (`MemoriesViewModelEventsTest`, `WorkflowsViewModelEventsTest` expect "Could not delete memory/memories/workflow" without an article) because the batch committed before reading the test rc; the production wording was restored to the pinned form and the commit amended before anything else was built on it. The tests were not changed.

## Tests added (12 in 3 files)

| File | Count | Pins |
|---|---|---|
| `ui/components/HomeSettingsCopyRulingsTest.kt` | 6 | on-disk scan of every owned main source (14 screen packages, `ui/navigation`, owned `ui/components`, `MainViewModel`, `MainActivity`, `NativeAuthManager`): no `.message ?:` fallback in code; no `${x.message}` inside a string literal (lines that wrap into a new `Exception`/`IOException` are exempt — they are not user copy); no literal opening with "Failed"; no "Retry" literal; no ASCII `...`; no snackbar literal ending in `!` or containing an em dash |
| `ui/screens/search/SearchViewModelTest.kt` | +2 | `retry()` after `Error` shows `Loading` synchronously and re-runs the same query; `retry()` with a now-too-short query returns to `Idle` without searching |
| `ui/components/HomeSettingsTopBarRuleTest.kt` | 4 | `LargeTopAppBar` callers under ownership == `SettingsScreen.kt` only; `ConversationsScreen.kt` has none (custom masthead); no `MediumTopAppBar` / `CenterAlignedTopAppBar`; every other owned bar is `TopAppBar` |

## Part 1 → theme H1 — bubble preview (`2d42bdba`, then `f6bf1775`)

`SettingsScreen.kt` `previewBubbleShape`: the 1971-1990 radii table (ROUNDED 28, COMPACT 16/6, GEOMETRIC 18/4/18/10) is gone. First commit called chat's `bubbleShapeFor(style, isUser, isAssistant = !isUser, MessageGroupPosition.Single)`; after the theme merge the function reads `ExpressiveShapes.Bubble.Rounded / Compact.User|Assistant / Geometric.User|Assistant` and `MessageBubbleShapes.UserBubble|AssistantBubble` for EXPRESSIVE, with no import from `ui/screens/chat` (orchestrator instruction: chat moves `bubbleShapeFor` onto the same tokens). **Visible change**: ROUNDED preview 28 → 34dp; COMPACT 16/6/16/16 → 18/6/18/18 (user) and 6/18/18/18 (assistant); GEOMETRIC 18/4/18/10 → 18/4/18/10 user (same) and 4/18/10/18 assistant (was 4/18/10/18 — same). `ShapeTokensTest` (theme) pins the tokens; the preview now cannot drift.

## Parts 2+3 — exception text and verb family (`2136eda5`, `f6bf1775`)

Every listed site re-opened and changed; no `e.message` / `error.message` / `throwable.message` reaches a snackbar, error state, field error or status line under ownership:

| File | Sites | Now |
|---|---|---|
| `ui/MainViewModel.kt` | 60, 73, 86 | `CopyRules.failureLine("create the conversation")`, `("create a temporary conversation")` |
| `SettingsScreen.kt` | 237, 260, 537, 571, 606 | `failureLine("save/read/create/unlock/restore the backup")` |
| `SettingsScreen.kt` | 318-322 (`ConnectionTestResult`) | `failureLine("connect to ${providerName}")`; `SettingsEvent.ConnectionTestResult.errorMessage` no longer populated from the exception (field kept, nullable) |
| `SettingsScreen.kt` | 2434, 2502 (title model picker) | "Couldn't fetch models" → `failureLine("fetch models")`; "Some providers failed: …" → the picker error line itself |
| `SettingsViewModel.kt` | 184, 340, 603-604, 653, 712, 824, 841, 878, 904, 910 | per-provider failure list carries provider names only (`failureLine("fetch models from A, B")`); `failureLine("load settings")`; `failureLine("sign in")` ×3; 653 (the exception text used as a *success* status) → "Signed in, but could not save the provider. Tap Save to try again."; `modelsError = failureLine("fetch models")`; `failureLine("save/activate/delete the provider")`; `failureLine("test the connection")` |
| `SettingsViewModel.kt` | 925-1350 (29 "Failed to save … setting") | `failureLine("save … setting")` (theme row H4) |
| `ModelAssignmentsViewModel.kt` | 147 | provider names only, same `failureLine` as above |
| `BookmarksViewModel.kt` | 59, 81, 111, 125, 154, 168 | `failureLine("load bookmarks")`, `("filter bookmarks")` |
| `WorkflowExecutionViewModel.kt` | 100, 149 | `failureLine("load the workflow")`, `("run the workflow")`; `WorkflowExecutionScreen.kt:336` banner no longer prefixes "Failed:" |
| `PersonaStudioViewModel.kt` | 52, 133, 151 | `failureLine("load personas")`, `("save the persona")`, `("delete the persona")` |
| `ConversationsViewModel.kt` | 117, 222, 242, 387, 412, 430, 537, 577, 585 | `failureLine(...)` for load/create/archive/restore/delete/update title/generate a title |
| `InsightsViewModel.kt` | 58 | `failureLine("load insights")` |
| `ArenaViewModel.kt` | 311 | "Vote recorded — identities revealed!" → "Vote recorded, identities revealed"; 318 `failureLine("record the vote")` |
| `AssistantVoiceSettingsViewModel.kt` | 83, 108, 113, 143, 152, 203, 221 (theme row H5) | "Unable to …" / "Please retry" / "Reopen to retry" → "Could not … and try again." notices; every line passes `CopyRules.isNotice` by reading |
| `MemoriesViewModel.kt`, `OnDeviceModelsViewModel.kt`, `WorkflowsViewModel.kt` | the five plain "Could not …" snackbars | `failureLine(...)` with the exact pinned wording |
| `data/auth/NativeAuthManager.kt` | 489 | loopback failure page body: fixed "Token exchange failed. Return to MaterialChat and try again." (no `throwable.message`); the two `IOException("… ${e.message}")` wrappers at 640/664 are exception chaining, not copy, left |
| `ConversationsScreen.kt` | 367, 879 | "Retry with AI" → "Try again with AI"; "Not recorded yet — retry/regenerate …" → "Not recorded yet. Generate the title again to capture the source" |
| `AddProviderSheet.kt` | 331 | placeholder `sk-...` → `sk-…` |

Deliberately kept: `CopyRules.REQUEST_FAILED` was not used — every site has a nameable action, so `failureLine(action)` is more specific. Multi-sentence notices (voice panel) were written by hand because `failureLine` builds a phrase, not a notice.

## Parts 4+5 — raw M3 buttons (`61a06965`)

- `HomeEmptyState.kt`: headline `headlineMedium` → `headlineSmall` Bold, body `bodyLarge` → `bodyMedium` (the `ErrorContent` type pair), raw `Button` + `ButtonDefaults.shapes()` → `ExpressiveButton` FilledTonal (it performs the CLICK haptic itself; the inline haptic call is gone). Hero shape cluster untouched. `EmptyHeadline` / `EmptyBody` slots unchanged (same fills).
- `ArenaVotingBar.kt`: the per-contender `Button` and the two `OutlinedButton`s → `ExpressiveButton` FilledTonal / Outlined; copy and enabled logic unchanged; the crown glyph stays in the label.
- `OnboardingScreen.kt:191` `TextButton` → `ExpressiveButton` Text with `leadingIcon = Settings`; `:326` sign-in `Button` (CircleShape, custom colours, inline spinner) → `ExpressiveButton` Filled, 72dp min height kept. **Layout change**: `ExpressiveButton` owns its content, so the in-flight `M3ExpressiveInlineLoading` now sits under the button beside the status line while `isSigningIn`, and the label reads "Waiting for sign-in". Unused imports (`Button`, `ButtonDefaults`, `TextButton`, `CircleShape`, `CustomShapes`, `Spacer`) removed.

## Part 6 — alpha fills (`79dcb1a8`)

Every listed site re-opened and classified. Text or icons that sit on a wash now read a named `HomeSettingsSlot` whose fill is the wash composited over its real backdrop (`ThemeContrast.compositeOver`), gated on all 39 appearances by `ThemeContrastTest.homeSettingsPaletteReadsOnEveryFillInEveryTheme` and listed by name in `HomeSettingsContrastTest.newSharedComponentSlotsArePresent`:

| Site | Classification | Slot(s) |
|---|---|---|
| `ExploreScreen.kt:281, 292` (soft burst 0.72, cookie 0.85) | decorative shapes, no content on them | none |
| `ExploreScreen.kt` clover (`graphicsLayer alpha 0.45`, primary) under the title column | text on wash | `ExploreHeroTitle`, `ExploreHeroBody` (primary @0.45 over primaryContainer) |
| `PersonaPickerSheet.kt:92, 165` (surfaceContainerHighest @0.5 over the sheet) | row title/supporting | `PickerRowTitle`, `PickerRowSupporting` |
| `ModelAssignmentsScreen.kt:286` (surfaceContainerHighest @0.55 over the card) | row title/meta/"Ready" | `OnDeviceRowTitle`, `OnDeviceRowMeta`, `OnDeviceRowReady` |
| `MemoriesScreen.kt:302` (kind content @0.12 over the kind container) | tile icon | `MemoryTileIconPreference/Instruction/Fact/Other` via `memoryTileIconColor(kind)` |
| `SettingsScreen.kt:3366` (secondaryContainer @0.58 over surfaceContainerHigh) | note text | existing `SearchNativeNote` slot, now actually read by the call site (it was defined in W6 but unused) |
| `OnboardingScreen.kt:477` (`DecorativeTonalShape` @0.36) | decorative backdrop behind the hero tile, no content | none |
| `ArenaLeaderboard.kt:250` | already lifted to full `contentColor` in W6; `trackColor @0.12` is a track wash | none |

## Part 7 (`0878bd51`)

- `ConversationsScreen.kt:1335, 1466`: private `SearchErrorContent` deleted; the `SearchUiState.Error` branch draws `ErrorContent(title = "Could not search", message = SEARCH_UNAVAILABLE, onRetry)`; `SearchContent` gained `onRetry`; `SearchViewModel.retry()` added (synchronous `Loading`, like Arena/MindMap).
- `PersonaCard.kt:104`: animated `RoundedCornerShape(cornerRadius)` → `ExpressiveShapes.Card` (static squircle, consistent with every other card); the press scale morph stays, the unused `animateDpAsState` corner state and `RoundedCornerShape` import are gone.
- `ConversationStarterChips.kt:54`: `CustomShapes.Pill` → `ExpressiveShapes.Chip` by name.
- `BookmarkSearchBar.kt:60` (and `:50`): 20dp → `ExpressiveSizes.Icon` (landed in `79dcb1a8` once the token existed).
- `ArenaPromptInput.kt:61`: `CustomShapes.MessageInputContainer` → file-private `ArenaComposerShape = RoundedCornerShape(28.dp)` with a KDoc pointing at the chat composer ruling (bubbles and the composer share circular corners). `CustomShapes.MessageInputContainer` now has **0 consumers** (theme may delete it).

## Part 8 (`0878bd51`)

- `ConversationRepositoryImpl.kt:498, 500`, `SendMessageUseCase.kt:825, 1064`, `GenerateConversationTitleUseCase.kt:324, 339`: `"..."` → `"\u2026"`, `take(n - 3)` → `take(n - 1)` so the length ceiling is unchanged. Nothing else in those files touched; the title/preview length tests stay green.
- `ui/screens/canvas/components/CanvasRefinementInput.kt`: grep of `app/src` found only its own definition (the `refinementInput` state in `CanvasUiState`/`SmartCanvasViewModel` is unrelated string state); deleted.

## Part 9

`SettingsScreen.kt:3433` `while (true)` is the backup-file read loop in `Context.readBytesFromUri` (reads until `-1`, enforces `MAX_BACKUP_FILE_BYTES`). It is I/O, not an animation; no motion gate applies. Left as is.

## Theme rows consumed (`f6bf1775`, `79dcb1a8`)

| Row | Done |
|---|---|
| H1 bubble preview | yes (above) |
| H2 `MindMapCanvas.kt:135` | `MaterialChatMotion.Tweens.linear(Durations.StreamingIndicator)`; `tween`/`LinearEasing` imports dropped |
| H3/H4 failure lines | every ViewModel site through `CopyRules.failureLine`; 0 "Failed to" / "Unable to" / "Couldn't" literals under ownership (grep) |
| H5 voice-settings notices | rewritten (table above) |
| H6 `HomeListInsets` | contract note only; untouched |
| `ExpressiveSizes.Icon/IconSmall/IconLarge` | 33 icon-size literals in 15 owned files → tokens (18 → `IconSmall`, 20 → `Icon`, 24 → `IconLarge`); values unchanged |
| Deleted symbols | grep after the rebase: no consumer under ownership of `CustomShapes.Fab/BottomSheet/CodeBlock/ProviderCard/Dialog/MessageActionButton/SendButton`, `M3ExpressiveCircularProgress/LinearProgress` or the legacy aliases; compile green |

## Top-bar rule (#11)

Ruling: `LargeTopAppBar` + `exitUntilCollapsed` only for Home (custom masthead, no M3 large bar) and Settings; every other screen a small start-aligned `TopAppBar`. Verified over the 17 bars under ownership: 1 `LargeTopAppBar` (`SettingsScreen.kt:439`), 16 `TopAppBar`, 0 medium/centre-aligned. Pinned by `HomeSettingsTopBarRuleTest`. The requested KDoc on `ExpressiveTopBarTitle` lives in `ui/components/ExpressiveSurfaces.kt`, which is theme-owned — see Needs another owner.

Dialog action convention (`TextButton` + `dialogActionButtonColors()` / `dialogDestructiveButtonColors()`) is already documented once on `ui/components/DialogActionColors.kt`; nothing added.

## Deliberately left alone

- 17 M3 chip sites (`FilterChip`/`AssistChip`/`SuggestionChip` with `shape = ExpressiveShapes.Chip`, 32dp height): the theme wrappers with `ExpressiveSizes.Chip.Height` were not shipped in W7 theme; migrating by hand would duplicate the wrapper at 17 sites.
- `ExpressiveShapes.Bubble` vs chat: `MessageBubble.bubbleShapeFor` still holds its own literals at this commit; the orchestrator said chat is moving it onto the tokens. Not mine.
- `ArenaLeaderboard` inline empty state, `ArenaBattleView` 420dp pager, `CanvasTopBar` five actions, the settings row-family refactor: W6 decisions, unchanged.
- `StepProgressIndicator.kt` inactive connector alpha: W6 already lifted it to `outlineVariant`.

## Needs another owner

- **chat**: `ui/screens/chat/ChatScreen.kt:283` `"Failed to share: ${e.message}"` → `CopyRules.failureLine("share")`; `MessageBubble.bubbleShapeFor` ROUNDED/COMPACT/GEOMETRIC branches → `ExpressiveShapes.Bubble.*` (the settings preview already reads them; until chat does, `ShapeTokensTest` is the only thing holding the two equal).
- **theme**: KDoc on `ExpressiveTopBarTitle` (`ui/components/ExpressiveSurfaces.kt:124`) stating the top-bar rule (text above); `CustomShapes.MessageInputContainer` has 0 consumers and can be deleted; `ExpressiveFilterChip/AssistChip/SuggestionChip` wrappers still pending (audit F4).
- **orchestrator / domain**: `domain/usecase/GetBuiltinPersonasUseCase.kt:104` `"Challenge my argument that..."` → `…` (not in my ownership list).

## Not verifiable without a device

- The bubble-style preview at the new radii (ROUNDED 34, COMPACT 18/6 on a 52×24 / 48×24 tile — a 34dp radius on a 24dp-tall tile renders as a capsule; whether that still reads as "rounded" next to "compact" was not seen).
- `HomeEmptyState` at headlineSmall / bodyMedium under the shape cluster; the FilledTonal action's tonal container on every palette (slot-gated numerically only).
- Onboarding sign-in button as `ExpressiveButton` Filled at 72dp, and the spinner's new position under the button while signing in.
- `ArenaVotingBar` FilledTonal / Outlined `ExpressiveButton`s at 56dp min height inside the `FlowRow` (was the 40dp M3 default): wrapping at 360dp with 3+ contenders was reasoned, not seen.
- Search error card (`ErrorContent` inside the `ScreenContainer` surface) and the retry round-trip; the JVM test covers the state machine only.
- `PersonaCard` as a static squircle (the corner no longer morphs on press; scale still does).
- Every Part 6 slot resolves numerically against the composited wash on the resting backdrop; the picker row on a pressed/ripple state and the Explore clover under a tilt offset are not re-resolved.
- Icon-size tokens are value-identical (18/20/24); nothing renders differently.
- No RTL composition, no font-scale run, no screenshot, no hosted run.
