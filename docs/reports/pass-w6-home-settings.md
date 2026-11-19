# Pass W6 — home + settings: report

Branch `pass/w6-home-settings`, base `9ef37001` (v2.41.5), rebased onto `origin/main` at `3293eaa1` (W6 theme merge `ad765acd`) before the Part 3 commit. No emulator, device or push. `./gradlew --stop` run at the end.

## Commits (oldest first)

| Commit | Part | Summary |
|---|---|---|
| `83577c6b` | 2 | Token inventory: 27 files onto `ExpressiveSizes` / `ExpressiveShapes.Chip` (was `1bdb2d80` before the rebase) |
| `c4888952` | 3 | State walk round 3: Explore, Canvas, Mind map, Arena, Generated images, Onboarding, Search; 12 ViewModel tests; orchestrator inventory items |
| `ade38402` | 1 | `HomeSettingsSlot.Snackbar*`/`Dialog*` deleted; `HomeSettingsPalette.slots = pairs() + componentContrastPairs()`; KDoc; two owned tests updated |
| `0d16318e` | 4 | Six raw-role text buttons inside the voice-configuration dialog body onto `dialogActionColor()` / `dialogDestructiveColor()` |
| `82687577` | 5 | `AssistantLaunchRoute` (plain Kotlin) carries the OPEN_CHAT / OPEN_SETTINGS pending route; `MainActivity` delegates; 4 tests |
| (this file) | — | REPORT.md |

## Gates

`JAVA_HOME=temurin-17 ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` rc=0 and `:app:testGithubDebugUnitTest` 0 failures before every commit:

| After | Unit tests |
|---|---|
| Part 2 (`83577c6b`) | 761 |
| Part 3 (`c4888952`) | 776 (theme merge +3, this pass +12) |
| Part 1 (`ade38402`) | 777 (+1) |
| Part 4 (`0d16318e`) | 777 |
| Part 5 (`82687577`) | **781** (+4), 0 failures, 0 errors, 0 skipped |

`git diff 3293eaa1 --stat`: 48 files, 840+/173−. `FontStyle.Italic`: 0. `testTag(` / labelled `contentDescription` removed: 0. `scripts/`, `.github/`, `ui/theme/**`, `ui/screens/chat/**`, `assistant/**`, `SmoothStreamingText.kt`: untouched. No androidTest added (judge minimum unchanged).

## Tests added (17 in 5 files)

| File | Count | Pins |
|---|---|---|
| `ui/screens/arena/ArenaViewModelTest.kt` | 4 | no providers → `NoProviders` (not `Error`); throwing provider store → `Error(PROVIDERS_UNAVAILABLE)` and `retry()` recovers through `Loading`; a failed `fetchModels` is **not** cached as an empty list and re-selecting the provider fetches again; `ContenderUi.content` on `StreamingState.Error` without partial text is `FAILED_ANSWER`, never the exception message |
| `ui/screens/mindmap/MindMapViewModelTest.kt` | 3 | throwing tree → `Error(TREE_UNAVAILABLE)` (exception text absent), `retry()` shows `Loading` synchronously then `Success` with `selectedNodeId = conversationId`; null tree → same fixed error; `selectNode` is a no-op outside `Success` |
| `ui/screens/search/SearchViewModelTest.kt` | 3 | throwing search → `Error(SEARCH_UNAVAILABLE)`, `clearSearch()` → `Idle`; no hits → `Empty(query)`; one-character query never calls the use case |
| `ui/screens/onboarding/OnboardingViewModelTest.kt` | 2 | failed sign-in → `error = SIGN_IN_FAILED` with `isSigningIn = false`; skip → `Completed` event and reset state |
| `ui/navigation/AssistantLaunchRouteTest.kt` | 4 | OPEN_SETTINGS pending until `settingsHandled()`, raised again by a later launch; OPEN_CHAT carries the id and leaves the flag; unknown / null action changes nothing; action strings equal the ones the assistant session sends |
| `ui/components/HomeSettingsContrastTest.kt` | 1 | `theSlotSetCarriesEveryComponentPairByValue`: the resolved slot set = enum walk + every `componentContrastPairs()` entry (same name, fill, content, minimum) on all 39 appearances, and the five deleted enum members stay deleted |

The `MindMapViewModelTest` retry test failed on first run and exposed a real defect: `retry()` only set `Loading` inside the launched coroutine, so the error card stayed until the dispatcher ran. Fixed in `MindMapViewModel.retry()` (synchronous `Loading`, like Arena).

## Part 2 — token inventory (`83577c6b`)

Grep over the ownership list at `9ef37001`; every hit re-opened.

### Migrated

| Class | Token | Sites |
|---|---|---|
| 48dp touch target | `ExpressiveSizes.Target` | `ExpressiveButton.kt:249,354`; `ExpressiveLoadingIndicator.kt:596`; `MaterialChatNavBar.kt:239,254`; `BookmarkCard.kt:197,211`; `PersonaCard.kt:141`; `StepEditor.kt:93`; `CanvasRefinementInput.kt:139`; `ConversationsScreen.kt:706`; `OnboardingScreen.kt:195`; `ModelAssignmentsScreen.kt:467,559,678,705`; `SettingsScreen.kt:2579` |
| 48dp hero indicator | `ExpressiveSizes.IndicatorHero` | `ExpressiveLoadingIndicator.kt:96,289,517`; `BookmarksScreen.kt:154`; `MemoriesScreen.kt:194`; `PersonaStudioScreen.kt:215`; `WorkflowsScreen.kt:173`; `WorkflowExecutionScreen.kt:244`; `InsightsScreen.kt:103`; `ArenaScreen.kt:154`; `ArenaLeaderboard.kt:103`; `MindMapScreen.kt:66`; `GeneratedImagesScreen.kt:152` |
| 22 / 24dp inline indicator | `ExpressiveSizes.IndicatorInline` (20; sites shrink 2–4dp) | `ConversationItem.kt:272`; `ExpandableConversationGroup.kt:427`; `BranchConversationItem.kt:236`; `SettingsScreen.kt:2993,2999`; `WorkflowExecutionScreen.kt:534` |
| 48 / 26dp leading tile + glyph | `ExpressiveSizes.Tile` / `TileIcon` | `ConversationItem.kt:147,162`; `ExpandableConversationGroup.kt:205,220`; `ProviderCard.kt:293,310`; `MemoriesScreen.kt:300`; `PersonaCard.kt:124`; `WorkflowCard.kt:67,74` |
| `RoundedCornerShape(50)` chips | `ExpressiveShapes.Chip` | `ConversationItem.kt:194,219`; `ExpandableConversationGroup.kt:252,276,289`; `BranchConversationItem.kt:207`; `OnDeviceModelsScreen.kt:509` |
| `RoundedCornerShape(999.dp)` | `ExpressiveShapes.Chip` (via `CustomShapes.Pill` in Part 2, token in Part 3) | `MaterialChatNavBar.kt:259` |

Part 3 added from the orchestrator list: `OnboardingScreen.kt:478` `RoundedCornerShape(44.dp)` → `SquircleShape(44.dp)` (same radius, G2 corner class like every hero container); `ArenaLeaderboard.kt:250` progress colour `contentColor @0.6` → full `contentColor` (meaningful indicator, WCAG 1.4.11; track stays a 0.12 wash); `StepProgressIndicator.kt:106,172` inactive connector / ring `outline @0.3/0.4` → `outlineVariant` at full alpha (the `outline` role has no consumer left in that file).

### Deliberate leftovers

| Site | Why |
|---|---|
| `tween(` at `MindMapCanvas.kt:135`, `ExpressiveLoadingIndicator.kt:111,126,303,313,419,429` | every duration is already a `MaterialChatMotion.Durations` token; only the `tween` wrapper is raw and no linear-tween token exists (theme W6 report says the same) |
| `spring(` | none under ownership |
| `RoundedCornerShape(` at `SwipeToDeleteBox.kt:232,434`, `ExpandableConversationGroup.kt:315`, `BranchConversationItem.kt:132`, `SegmentedRowShapes.kt:64`, `PersonaCard.kt:104`, `SettingsScreen.kt:1951`, `ExpressiveButton.kt:105,293,360,435,546` | animated / lerped / per-position corner radii; a static token cannot express them |
| `SettingsScreen.kt:1977-1990` bubble-style preview literals | duplicates the chat bubble family; see Needs another owner |
| `ActivityHeatmap.kt:156` `RoundedCornerShape(3.dp)` on a 12dp cell | no token (Badge is 8dp) |
| `.copy(alpha =` remaining (`ConversationsScreen.kt:571`, `SwipeToDeleteBox.kt:252,304`, `ConversationItem.kt:109,115`, `ExpandableConversationGroup.kt:207,253,290,348`, `BranchConversationItem.kt:100`, `ModelAssignmentsScreen.kt:286`, `SettingsScreen.kt:3189,3366`, `SystemPromptField.kt:244,300`, `ExploreScreen.kt:281,292,430`, `MemoriesScreen.kt:302`, `PersonaPickerSheet.kt:92,165`, `PersonaCard.kt:195`, `ActivityHeatmap.kt:148,149`, `ArenaLeaderboard.kt:206,251`, `SearchResultItem.kt:157-159,229`, `SearchBar.kt:120`, `OnboardingScreen.kt:477`, `MaterialChatNavBar.kt:220`, `ExpressiveButton.kt:276`, `ExpressiveLoadingIndicator.kt:390`, `HomeSettingsContrast.kt:145`, `MindMapCanvas.kt:497`) | all fills, washes, tracks, hairlines, the animated indicator dot or the compositing helper; none is a text or icon tint. The text that sits on the alpha fills is gated (`HomeSettingsSlot` `Group*`, `Branch*`, `AssistantDefaultCardText`, `SearchNativeNote`, new `SearchMatch*`) |
| ASCII `...`: `AddProviderSheet.kt:331` `"sk-..."` | API-key prefix sample (W4 ruling: leave); `MarkdownText.kt:259-260` are regex comments |
| 44dp: `OnboardingScreen.kt:240` (glyph inside the 104dp hero tile), `ExpressiveSlider.kt:92` (`ThumbRestingHeight`) | not touch targets |
| 48dp: `SettingsScreen.kt:1822` (48×24 preview bubble), `SwipeToDeleteBox.kt:114` (reveal threshold), `ExpressiveFastScrollBar.kt:82` (thumb min height) | geometry, not targets |
| `CustomShapes.Pill` badges in `SearchResultItem.kt` (match badge) and `GeneratedImagesScreen.kt` (`ImageActionButton`) | value-identical to `ExpressiveShapes.Chip`; not renamed |

## Part 3 — state walk round 3 (`c4888952`)

Method: ViewModel + state types + every composable of the seven screens read in full; states enumerated (empty / loading / error / long content), then 360dp, font scale 1.3 and RTL reasoned from the tree (fixed sizes on text, `maxLines` without overflow, absolute offsets, directional glyphs).

| Screen | File:line | Found | Change |
|---|---|---|---|
| Explore | `ExploreScreen.kt:338` | hero description `maxLines = 2` in 62% width: at 360dp × 1.3 the sentence needs three | `maxLines = 3` |
| Explore | `ExploreScreen.kt:444` | eyebrow `maxLines = 1` with no overflow mode | `TextOverflow.Ellipsis` |
| Explore | decorative `offset(x =` | `Modifier.offset` is layout-direction aware and the shapes are aligned `TopEnd`/`BottomEnd`/`BottomStart`; mirrors correctly | none |
| Canvas | `SmartCanvasScreen.kt:106` | save failure toast printed `e.message` | fixed copy "Could not save the artifact" |
| Canvas | `CanvasUiState` | no loading/error state: the artifact is parsed synchronously with an HTML fallback; code view scrolls both axes | none |
| Canvas | `components/CanvasRefinementInput.kt` | has **no consumer** (grep of `app/src/main`): dead composable since the refinement bar was removed from the screen | left; see Needs another owner |
| Mind map | `MindMapScreen.kt:73`, `MindMapViewModel.kt:66-80` | error state was a raw `bodyLarge` line in the `error` role, no retry, and the exception message reached the screen | `LoadErrorContent` (Try again, Go back) with fixed `TREE_UNAVAILABLE`; `retry()` added |
| Arena | `ArenaScreen.kt:168`, `ArenaViewModel.kt:76-104` | error state was a raw error-role line, no retry; "No providers configured" was modelled as an error | `ArenaUiState.NoProviders` → `ExpressiveEmptyState` card with Go back; `Error` → `LoadErrorContent` + `retry()`; fixed `PROVIDERS_UNAVAILABLE` |
| Arena | `ArenaViewModel.kt:124-148` | a failed `fetchModels` (`Result.failure`) was cached as an empty list: read as "provider has no models" and could never be retried; a thrown fetch showed the snackbar but also could not be retried | failure leaves the cache untouched, snackbar `MODELS_UNAVAILABLE`; re-selecting the provider fetches again (test) |
| Arena | `ArenaUiState.kt:26` | contender card body `"Error: ${error.message}"` | `FAILED_ANSWER` fixed copy; non-blank partial answer still shown |
| Arena | `ArenaBattleView.kt:111` | "Swipe for other responses →": the glyph arrow points the wrong way under RTL | glyph dropped |
| Arena leaderboard | `ArenaLeaderboard.kt:250` | win-rate track at `contentColor @0.6` | full `contentColor` |
| Generated images | — | already on `LoadErrorContent` / `ExpressiveEmptyState` / `IndicatorHero`; toasts fixed copy; grid `Adaptive(164)` gives two columns at 360dp | none |
| Onboarding | `OnboardingViewModel.kt:69` | error card body was `e.message` from the auth flow | `SIGN_IN_FAILED` fixed copy |
| Onboarding | `OnboardingScreen.kt:388` | `ProviderOptionCard` row had no vertical inset: a two-line subtitle at 1.3 outgrows the 88dp minimum and touches the card edge | `vertical = 12.dp` |
| Onboarding | `OnboardingScreen.kt:478` | `RoundedCornerShape(44.dp)` on the 132dp backdrop | `SquircleShape(44.dp)` |
| Search | `SearchViewModel.kt:102` | `Error(e.message ?: …)` reached the sheet | `SEARCH_UNAVAILABLE` fixed copy |
| Search | `SearchResultItem.kt:110-132` | leading emoji in a bare 40dp box at `headlineMedium` (36sp line = 47dp at 1.3): clipped; icon tinted raw `primary` | 48dp `primaryContainer` `Tile` with `headlineSmall` / `TileIcon`, like `ConversationItem`; icon on `HomeSettingsSlot.RowTileIcon` |
| Search | `SearchResultItem.kt:162-166` | match badge text in raw `primary`/`secondary`/`tertiary` on a half-alpha container over the row fill: no slot gated it | new slots `SearchMatchTitle` / `SearchMatchContent` / `SearchMatchBoth` (fill = container @0.5 over `surfaceContainerLow`) |
| Search | `SearchResultItem.kt:309` | highlight span inherited `onSurfaceVariant` on a `tertiaryContainer` wash | span colour = `HomeSettingsSlot.SearchHighlight` (`onTertiaryContainer`) |
| Search | `SearchResultItem.kt:215` | snippet indent `56.dp` literal | `ExpressiveSizes.Tile + 16.dp` (tile + gap) |

The four new slots are enumerated by `ThemeContrastTest` across all 39 appearances (green) and by `HomeSettingsContrastTest.newSharedComponentSlotsArePresent`.

## Part 1 — duplicate slots (`ade38402`)

- `HomeSettingsContrast.kt:80-84`: `DialogBody`, `DialogLabel`, `DialogDestructive`, `SnackbarBody`, `SnackbarAction` deleted. No `app/src/main` consumer read them (grep).
- `HomeSettingsContrast.kt:178`: `override val slots get() = pairs() + scheme.componentContrastPairs()`. `pairs()` stays the pure enum walk (`ThemeContrastTest.homeSettingsPaletteReadsOnEveryFillInEveryTheme` asserts `HomeSettingsSlot.values().size == pairs().size`).
- `HomeSettingsContrast.kt:203-207`: `HomeSettingsContrastSlots` KDoc rewritten (no longer claims the theme test pins the names).
- `HomeSettingsContrastSlotsTest.kt`: size expectation = enum size + component pairs, and the zip walks the concatenated list. `HomeSettingsContrastTest.kt`: `"DialogDestructive"`, `"SnackbarAction"` removed from the by-name presence list (they no longer exist); replaced by the stronger `theSlotSetCarriesEveryComponentPairByValue` (all 13 component pairs by name, fill, content and minimum on every appearance, plus the five enum members asserted absent). `ThemeContrastTest.componentPairsGate…` and `dialogActionColourKeeps…` (theme-owned, value assertions) stay green.

## Part 4 — settings coherence (`0d16318e`)

### Dialogs: every `AlertDialog` under ownership (grep, 10 hosts)

| Host | Shape + entrance | Confirm / dismiss colours |
|---|---|---|
| `SettingsScreen.kt:2699` (backup passphrase) | `Dialog` + `dialogEntrance()` | `dialogActionColor()` × 2 |
| `SettingsScreen.kt:2752` (restore confirm) | same | `dialogActionColor()` × 2 |
| `SettingsScreen.kt:2804` (delete provider) | same | `dialogDestructiveColor()` / `dialogActionColor()` |
| `OnDeviceModelsScreen.kt:185` | same | destructive or action by backend / `dialogActionColor()` |
| `AssistantVoiceSettingsPanel.kt:81` (loading) | same | no buttons |
| `AssistantVoiceSettingsPanel.kt:95` (voice configuration) | same | Save / Cancel on `dialogActionColor()` (`:293,304`); **six body buttons were raw `primary`** → fixed this pass: Retry/Refresh models (`:124`), Stop preview ×2 (`:211,266`), Cancel download (`:241`) on `dialogActionColor()`; Delete model (`:245`), Remove stored key on save (`:282`) on `dialogDestructiveColor()` |
| `WorkflowExecutionScreen.kt:176` | same (`containerColor = surfaceContainerHigh`) | Run is `FilledTonal` (tonal container, gated by M3 roles); Cancel `dialogActionColor()` |
| `ConversationsScreen.kt:338` (rename / actions) | same | `dialogActionButtonColors()` / `dialogDestructiveButtonColors()` |
| `ConversationsScreen.kt:882` (info) | same | `dialogActionButtonColors()` |
| `MemoriesScreen.kt:158` (delete all) | same | `dialogDestructiveButtonColors()` / `dialogActionButtonColors()` |

All ten are on `ExpressiveShapes.Dialog` + `dialogEntrance()`; after this pass every text button inside a dialog under ownership draws a resolved dialog colour.

### Row types, headers, dividers, gutters — audited, not consolidated

| Family | Drawn once? | Sites bypassing it |
|---|---|---|
| Switch row | `SettingsToggleRow` (`SettingsExpressive.kt:142`) | inline `ExpressiveSwitch`: `SettingsScreen.kt:2164,2216,2940,3285`; `ModelAssignmentsScreen.kt:140,360`; `InteractionSettingsScreen.kt:184` |
| Disclosure row | `SettingsNavRow` (`:210`) | — |
| Slider row | no shared row | `SettingsScreen.kt:2061,3389`; `AssistantVoiceSettingsPanel.kt:331` |
| Dropdown row | no shared row | `SettingsScreen.kt:2406`; `ModelAssignmentsScreen.kt:493,581`; `ProviderCard.kt:255`; `AssistantVoiceSettingsPanel.kt:377` |
| Action / destructive row | no shared row | provider card actions menu, backup/restore buttons, delete-all |
| Section header | `SettingsSectionHeader` (10 sites) | ~24 inline `titleSmall` headers in `SettingsScreen.kt` (`:1309-3274`) |
| Divider | `HorizontalDivider` default | 5 sites: 3 inside `DropdownMenu`s, `SettingsScreen.kt:2420`, voice panel ×2 |
| Card gutter | — | `horizontal = 16.dp` ×7, `14.dp` ×2, `10.dp`, `4.dp`; `ExpressiveSpacing.Gutter` has 0 consumers in settings |

**Not done:** folding the inline switch/slider/dropdown/action rows and the ~24 inline headers onto one row family. That is a structural refactor of a 3,400-line `SettingsScreen.kt` (new `SettingsSliderRow`/`SettingsDropdownRow`/`SettingsActionRow` composables plus every call site), not a polish fix; it would need its own wave with device acceptance of the segmented-group shapes. The table above is the work list.

## Part 5 — OPEN_SETTINGS (`82687577`)

The pending-route rule was extractable: `ui/navigation/AssistantLaunchRoute.kt` holds `pendingConversationId`, `pendingOpenSettings` (`mutableStateOf`, so the nav host still recomposes), `accept(action, conversationId)` and `settingsHandled()`; the action/extra constants moved there and `MainActivity.companion` aliases them so `MaterialChatVoiceInteractionSession.kt:521` keeps compiling. `MainActivity.handleAssistantIntent` now only unpacks the `Intent`; `onCreate` and `onNewIntent` paths are unchanged. `AssistantLaunchRouteTest` (4) runs on the JVM without Robolectric. The Compose side (`LaunchedEffect(openSettings)` navigating with `launchSingleTop` and calling `onOpenSettingsHandled`) is unchanged and remains a manual check.

## Deliberately left alone

- `ArenaLeaderboard.kt:108-140` empty state is an inline icon + two lines (the W5 theme report listed it as an `ExpressiveEmptyState` caller; it is not). Candidate for the card like the other five screens; not changed because the brief's ruling covers the five existing callers only.
- `ArenaBattleView` fixed `height(420.dp)` pager: the card body scrolls inside, so font scale 1.3 does not clip; at 360dp wide the 4dp horizontal padding is tight but intentional for the swipe pager.
- `CanvasTopBar` five icon actions: at 360dp the title gets ~120dp; `ExpressiveTopBarTitle` ellipsises. An overflow menu would be cleaner; not changed without a device look.
- `SearchErrorContent` (`ConversationsScreen.kt:1466`) stays the inline two-line notice inside the search sheet (W5 decision); only its body copy changed.

## Needs another owner

- **Chat**: `SettingsScreen.kt:1977-1990` bubble-style preview hard-codes the chat bubble radii (`24/6/24/24`, `28`, `16/6/16/16`, `18/4/18/10`); chat should expose one function the preview reads so it cannot drift from `MessageBubble`.
- **Theme** (optional): a `MaterialChatMotion.Tweens.linear(duration)` wrapper would retire the seven raw `tween(` sites listed above; the durations are already tokens.
- **Orchestrator**: `ui/screens/canvas/components/CanvasRefinementInput.kt` has no consumer; delete or re-wire into `SmartCanvasScreen`.
- **Orchestrator**: decide whether the Part 4 row-family refactor (table above) gets its own wave.

## Not verifiable without a device

- Every rendered outcome of Part 2: `IndicatorInline` shrinking the 22/24dp indicators to 20dp inside the home rows and the settings update row; the 999dp→`Chip` nav pill (shape-equivalent, not seen).
- Part 3: `LoadErrorContent` card on Mind map (inside the `ScreenContainer` surface) and Arena (same), the Arena `NoProviders` card, the search row tile at 48dp beside the title column at 1.3, the Explore hero at three lines, the onboarding option card with the new 12dp inset, the `SquircleShape(44.dp)` backdrop.
- `SearchMatch*` / `SearchHighlight` slots are numeric WCAG checks on the resolved scheme; the half-alpha compositing assumes the resting row fill (`surfaceContainerLow`); on the pressed row (`surfaceContainerHighest`) the badge is not re-resolved.
- Arena model-fetch retry: the JVM test proves the cache behaviour; the picker's visible loading indicator during the re-fetch was not seen.
- Part 4: the six voice-panel body buttons on VIOLET / CITRUS light fall back to `onSurface` (computed, not seen).
- Part 5: the real intent path (assistant notice action → `MainActivity` cold start and `onNewIntent`) is unchanged but was not exercised; `AssistantLaunchRouteTest` covers only the rule.
- RTL: no `LayoutDirection.Rtl` composition was run; the arrow-glyph removal and the offset reasoning are from the tree.
