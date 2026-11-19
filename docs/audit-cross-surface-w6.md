# W6 cross-surface coherence audit (read-only)

Repo `9ef37001` (v2.41.5). Static reading only: no build, no device, no emulator, no edits. Every file:line is from grep/sed on this commit; anything derived by offset or not re-opened is marked *unverified*. Read first as if W1-W5 had not happened; the W4 diff is in the table at the end. Owners as before: **theme** (`ui/theme`, `ui/components`, `ui/navigation`, `MainActivity`), **chat** (`ui/screens/chat`, markdown), **home-settings** (home, settings, drawer screens, arena, onboarding), **assistant** (`assistant/**`).

Ranking = impact x spread.

## Ranked findings

### 1. Settings bubble-style preview draws a different shape table than the chat bubble (NEW)

Two independent `when (ChatBubbleStyle)` tables of `RoundedCornerShape`: `SettingsScreen.kt:1971-1990` (`previewBubbleShape`) and `MessageBubble.kt:919-970` (`bubbleShapeFor`). They disagree: ROUNDED preview is 28dp (`SettingsScreen.kt:1981`) while the bubble draws 34dp (`MessageBubble.kt:948`); COMPACT/GEOMETRIC bubbles are 20/16 (`MessageBubble.kt:927-928`) vs preview tails 18/4/10 and 16/6 (`SettingsScreen.kt:1983-1990`). The style picker shows the user a shape the chat never renders. Proposed: chat exposes `bubbleShapeFor` (or a `BubbleStyleShapes` table in `ui/theme`) and settings consumes it; `ShapeTokensTest` asserts the two are the same object. Owners: chat (export), home-settings (delete the copy).

### 2. Exception text reaches the user from 40+ sites; W4 counted five

`e.message ?: "..."` into snackbars, error states and field errors: `MainViewModel.kt:60,73,86`; `SettingsScreen.kt:237,260,537,571,606`; `SettingsViewModel.kt:184,340,603-604,653,712,824,841,878,910`; `ModelAssignmentsViewModel.kt:147`; `ChatViewModel.kt:209,389,1084,1205,1233,1328,1616,2007`; `WorkflowExecutionViewModel.kt:100,149`; `SearchViewModel.kt:103`; `PersonaStudioViewModel.kt:52`; `BookmarksViewModel.kt:59,81,111,125,154,168`; `ConversationsViewModel.kt:117`; `InsightsViewModel.kt:58`; plus the snackbar literal `"Failed to share: ${e.message}"`. `SettingsViewModel.kt:653` even uses `e.message` as the *success* status. `OnDeviceModelsViewModel` (the W4 sites) is clean; `ErrorContent`/`LoadErrorContent` forbid pass-through by contract (`LoadError.kt` doc, `ErrorContent.kt:32-41`), so the view models are undoing it. Proposed: one `UserFacingError.message(e)` mapper (network / auth / rate-limit / generic, reusing `ChatErrorCopy`/`AssistantCopy` lines) and a lint-style test that greps `e.message ?:` in `ui/**`. Owners: chat (`ChatViewModel`), home-settings (everything else), theme (mapper).

### 3. Empty state and error state are the same card in two shapes; chat empty regressed to round

| Surface | Component | Shape | Headline / body | Padding | Action | File:line |
|---|---|---|---|---|---|---|
| Error (all screens) | `ErrorContent` | `ExpressiveShapes.Card` squircle 28 | headlineSmall Bold / bodyMedium | - | `ExpressiveButton` FilledTonal + Text | `ErrorContent.kt:118,147,154,86,92` |
| Empty: chat, bookmarks, workflows, memories, generated images | `ExpressiveEmptyState` | `CustomShapes.ConversationItem` **round 24** | *unverified* | *unverified* | `ExpressiveButton` FilledTonal | `ExpressiveSurfaces.kt:324,306`; `ChatEmptyState.kt:73` (W4: chat was on `Card`) |
| Empty: home | `HomeEmptyState` | shape cluster, no card | headlineMedium Bold / bodyLarge | 32 | raw M3 `Button` | `HomeEmptyState.kt:86,96,106,114` |

Proposed: `ExpressiveEmptyState` -> `ExpressiveShapes.Card` + the `ErrorContent` type pair (one `StateCard` primitive under both); home keeps its hero but takes the type pair and `ExpressiveButton`. Owners: theme (`ExpressiveSurfaces.kt:324`), home-settings (`HomeEmptyState`).

### 4. Chip class: tokens exist, heights still 32 / 40 / 48, capsule set per site

`ExpressiveSizes.Chip { Height 40, Icon 18, Inset 14 }` (`ExpressiveDimensions.kt:15-23`) and `ExpressiveShapes.Chip` (`Shapes.kt:213`) shipped. But: the 17 M3 chip sites each pass `shape = ExpressiveShapes.Chip` by hand (`InsightsScreen.kt:150`, `ModelAssignmentsScreen.kt:670,700`, `InteractionSettingsScreen.kt:210,239`, `SettingsScreen.kt:2571,2673`, `PersonaCard.kt:187`, `PersonaEditorSheet.kt:171`, `BookmarkFilterBar.kt:83,138`, `BookmarkCard.kt`, `AddBookmarkSheet.kt`, `ArenaModelPicker.kt`, `ConversationStarterChips.kt:54` via `CustomShapes.Pill`) and none set height, so they stay at the M3 32dp; `ExpressiveSizes.Chip.Height` has no consumer outside `assistant/**` (`AssistantExpressiveStyle.kt:200`). The chat memory chip is still 48dp (`MessageBubble.kt:1365` *offset-derived from 1375*, shape `:1375`). `AssistantSizes.ChipIcon = 16.dp` deviates from the 18dp token by documented choice (`AssistantExpressiveStyle.kt:204-211`). Proposed: theme-level `ChipDefaults` is not overridable, so add `ui/components/ExpressiveFilterChip/AssistChip` wrappers fixing shape+height; memory chip to 40 or renamed a row. Owners: theme (wrappers), home-settings (17 sites), chat (memory chip), assistant (icon 16 vs 18 decision).

### 5. Dead-but-pinned shape tokens and a duplicated radii record

- `CustomShapes.Fab/BottomSheet/CodeBlock/ProviderCard/Dialog` are `@Deprecated` with **0 consumers** (`Shapes.kt:419-516`); their only readers are `ShapeTokensTest.kt:27,33,38,46,205-206`. `CustomShapes.MessageActionButton` (`:453`) and `SendButton` (`:473`) have 0 consumers and are not deprecated.
- `AssistantShapes.Answer/Notice/Thinking` are `RoundedCornerShape`s kept "as the radii record only" (`AssistantExpressiveStyle.kt:88-108`), while `ExpressiveShapes.AnswerSurface/NoticeSurface/ThinkingSurface` restate the same radii literally (`Shapes.kt:193,196,203`). Two sources of truth for one geometry, both pinned by the theme test (`ShapeTokensTest.kt:174-178`).
Proposed: delete the seven dead `CustomShapes` members and their asserts; `ExpressiveShapes.*Surface` take their radii from one `AssistantShapes.AnswerRadii` (Dp quadruple) or the assistant record is deleted. Owners: theme, assistant.

### 6. Raw capsules and raw corners where a token exists (NEW)

`RoundedCornerShape(50)`/`999.dp` with `CustomShapes.Pill`/`ExpressiveShapes.Chip` available: `MaterialChatNavBar.kt:259`; `ConversationItem.kt:194,219`; `ExpandableConversationGroup.kt:252,276,289`; `BranchConversationItem.kt:207`; `OnDeviceModelsScreen.kt:509`; `AssistantExpressiveStyle.kt:111` (`Toolbar`). Other raw literals: `OnboardingScreen.kt:478` (44dp), `MarkdownText.kt:1720` (8dp header), `DateSeparator.kt:81`, `ActivityHeatmap.kt:156` (3dp cells, exempt), `SettingsScreen.kt:1714,1720` and nine other `CircleShape` dots/avatars (acceptable as glyph circles; decide once). Owners: theme (nav bar), home-settings, chat (markdown), assistant (`Toolbar`).

### 7. Every animated-corner pressable is round; every static container is squircle

`RoundedCornerShape(cornerRadius)` driven by `Press.Shape`: `ExpressiveButton.kt:105,293,360,435,546`; `ScrollToBottomFab.kt:181`; `ChatTopBar.kt:532`; `ModelPickerDropdown.kt:171`; `MessageBubble.kt:1375`; `PersonaCard.kt:104`; `SwipeToDeleteBox.kt:232,434`; `AssistantOverlay.kt:1063,1125,1749`; `MessageInput.kt:565-584` (connected menu). Static surfaces beside them are `SquircleShape` (`Shapes.kt:163-244`). `SquircleShape(radius: Dp)` exists (`Shapes.kt:130`), so a morphing squircle is one `remember` away. Proposed: `ShapeMorphing.buttonShape` (already used by `ExpressiveSnackbar.kt:57`) returns a squircle, or `Shapes.kt` documents "pressables are round" as the rule. Owner: theme (decision + `ExpressiveButton`), then each surface.

### 8. Arena drift bundle (NEW)

`ArenaPromptInput.kt:61` is the sole consumer of `CustomShapes.MessageInputContainer` (28dp round) after chat left it (`MessageInput.kt` has no reference). `ArenaVotingBar.kt:47,67,72` uses raw `Button`/`OutlinedButton` beside `ExpressiveButton` everywhere else. `ArenaViewModel.kt:311` snackbar `"Vote recorded — identities revealed!"` is the only exclamation and em-dash in the snackbar corpus. Owner: home-settings.

### 9. Raw M3 buttons outside `ui/components` (NEW)

Non-dialog sites: `HomeEmptyState.kt:114` (`Button` + `ButtonDefaults.shapes()`), `OnboardingScreen.kt:191,326`, `FusionModelSelector.kt:241`, `ArenaVotingBar.kt:47,67,72`, `AssistantOverlay.kt:1536` (`FilledTonalButton`), `ComposerSendButton.kt:169` (bespoke send morph, exempt). Dialog action `TextButton`s (`ConversationsScreen.kt:356-422,910`, `MemoriesScreen.kt:164,176`, `AssistantVoiceSettingsPanel.kt:289,300`) follow the M3 dialog convention via `dialogActionButtonColors()` and are fine. Proposed: the five non-dialog sites on `ExpressiveButton`. Owners: home-settings, chat (fusion), assistant.

### 10. Search error is the one error state off `ErrorContent`

`ConversationsScreen.kt:1335` -> private `SearchErrorContent` (`:1466`), while the same file's load error already uses `LoadErrorContent` (`:853`). Owner: home-settings.

### 11. Icon size ladder inside one component family

`ExpressiveButton.kt:197,213,372` draw 20dp icons, `:305,452` draw 24dp; `ComposerSendButton.kt:216,222` 22dp; `BookmarkSearchBar.kt:60` 20dp; `AssistantSizes.ChipIcon` 16 vs `ExpressiveSizes.Chip.Icon` 18. `ExpressiveSizes` has no `Icon`/`IconSmall` token (`ExpressiveDimensions.kt:13-48`). Owner: theme (token + `ExpressiveButton`), chat.

### 12. Motion: two tokens with identical values, one raw tween, one hard-coded clearance

`Spatial.pressMorph()` and `Spatial.fast()` are both `0.6 / 800` (`Motion.kt:206-218`); `AssistantMotionTest.kt:35` pins `fast == Press.Progress`, so the two names cannot diverge without an assistant-owned test breaking. `StreamingIndicator.kt:481` keeps a parametrised raw `tween` (gated). `MainActivity.kt:343` hard-codes 104dp above the nav bar (vs `HomeListInsets` *unverified*). `ExpressiveControlShapes.kt` press spec: *not re-opened* (W4 F3 item). Owners: theme, chat.

### 13. ASCII ellipsis survives only in domain/data strings that are shown to users

`ui/**` and `assistant/**` are clean (hits are comments). User-visible leftovers: `ConversationRepositoryImpl.kt:498,500` (search snippet), `SendMessageUseCase.kt:825,1064`, `GenerateConversationTitleUseCase.kt:324,339` (conversation titles), `GetBuiltinPersonasUseCase.kt:104` (starter prompt). Owner: unowned (domain/data) - assign in W7.

### 14. Dead composables (NEW)

`ExpressiveLinearIndicator`, `ExpressiveArcIndicator`: 0 callers; `ExpressiveLoadingIndicator`: no caller outside its own file (*unverified* whether the 2 hits are definition + preview); `FusionBadge`: 0 external callers. Owners: theme, chat.

### 15. Cross-owner test pins (category 9)

| Test (owner) | Pins values owned by | Lines |
|---|---|---|
| `ui/theme/ShapeTokensTest` (theme) | assistant `AssistantShapes.Answer/Notice/Thinking`; deprecated `CustomShapes.*` | `:174-178`; `:27,33,38,46,205-206` |
| `ui/theme/MotionTokensTest` (theme) | chat top-bar loop duration `HeaderPulse` | `:48-51` |
| `assistant/ui/AssistantMotionTest` (assistant) | theme `ExpressiveMotion.Press.Shape/Progress`, `Spatial.fast`, `Disclosure.Expand/Shrink` | `:29-46` |
| `chat/.../ChatErrorCopyTest` (chat) | assistant `AssistantCopy.RATE_LIMITED/NO_CONNECTION/REQUEST_FAILED/AFTER_ERROR` | `:96-100` |
| `ui/components/ErrorContentCopyTest` (theme) | chat `ChatErrorCopy.TRY_AGAIN`, assistant `AssistantCopy.AFTER_ERROR` | `:19-20` |
| `ui/components/HomeSettingsContrastSlotsTest` (home) | theme `MaterialChatThemePalettes` (enumeration only, benign) | `:5,19` |

Proposed: copy constants collapse to one `ui/components/Copy` object owned by theme (chat/assistant alias it), so the three copy tests become one; shape/motion pins move into the owning test file. Owner: theme coordinates.

### 16. Seam notes

- Snackbar family is one answer now: `ExpressiveSnackbar` on `ExpressiveShapes.Notice` + `ExpressiveSpacing.Inset` (`ExpressiveSnackbar.kt:88,91`) hosted on all 9 screens incl. chat/arena/bookmarks/workflows/memories; `CustomShapes.Snackbar` removed (`Shapes.kt:500`). Snackbar copy: no trailing period anywhere (40 literals), assistant notices always end with one (`AssistantCopy.kt`) - consistent per family; record the rule.
- Top bars: `LargeTopAppBar` on settings (`SettingsScreen.kt:436`); 15 plain `TopAppBar`s; Insights/MindMap moved to plain (`InsightsTopBar.kt:30`, `MindMapTopBar.kt:30`). Home bar type: *unverified* (no `LargeTopAppBar` hit in `ConversationsScreen.kt`). Still an undecided Large-vs-small rule.
- Contrast: `ChatContrast` delegates to `ThemeContrast` (`ChatContrast.kt:10-15,36-50`); `ContrastSlot`/`ContrastSlotSet`/`ContrastSlotResolver` exist (`ContrastSlot.kt:17,46,58`); dialog and snackbar slots gated (`HomeSettingsContrast.kt:80-84`); error content gated in `componentContrastPairs` (`ErrorContent.kt:54`). `ExpressiveSurfaces` disabled alpha 0.38: *not re-opened*.
- Alpha on content: only progress-/animation-driven (`MessageBubble.kt:716`, `StreamingIndicator.kt:187`, `ExpressiveLoadingIndicator.kt:390`); rest are fills/washes. Raw `Color(`: only palette definitions and resolver fallbacks (`Color.kt`, `ThemePalettes.kt`, `PitchBlack.kt`, `ThemeContrast.kt:86-88`).
- Type: weights used outside theme (Normal/Medium/SemiBold/Bold/ExtraBold) are all shipped instances (`Typography.kt:77-83`). No finding.
- Accessibility: 0 `clearAndSetSemantics`, 0 `IconButton` with `contentDescription = null` (grep), `ExpressiveSizes.CompactTarget = 44` now legitimises the W4 44dp targets rather than removing them (`ExpressiveDimensions.kt:30`).
- Copy verb: "Try again" pinned in all three families (`ErrorContent.kt:38`, `ChatErrorCopy.kt:39`, `AssistantCopy.kt:32`); `LoadError.kt` `ERROR_RETRY_LABEL` value *unverified* (not shown in the read window).

## W4 -> W6 diff

| W4 # | Finding | Status | Evidence |
|---|---|---|---|
| 1 | Snackbar family | **closed** | `ExpressiveSnackbar.kt:88,91`; hosts in chat/arena/bookmarks/workflows/memories; `Shapes.kt:500` |
| 2 | Assistant round corners / composer | **closed** (assistant), **residual** (arena input, radii duplicate -> F5, F8) | `AssistantOverlay.kt:1243,1660`, `AssistantResponseCard.kt:253`; `ArenaPromptInput.kt:61` |
| 3 | Press/disclosure physics | **closed** (`AssistantMotion.morph` gone; `Press.Shape` used at `AssistantOverlay.kt:854,1037,1047,1744`); `expressiveControlShape` *unverified* | F12 |
| 4 | Chip class | **partial**: tokens shipped, shape adopted; heights 32/40/48 remain | F4 |
| 5 | Contrast resolvers | **closed** | `ChatContrast.kt:10-15`, `ContrastSlot.kt`, `HomeSettingsContrast.kt:80-84` |
| 6 | Copy: ellipsis / period / exception / verb | ellipsis **closed in UI**, residual in domain (F13); verb **closed**; exception text **wider than reported** (F2) | |
| 7 | Full-screen error layouts | **closed** except search error (F10) | `LoadError.kt:31`, 10 consumers |
| 8 | Dialog shape/entrance | **closed** | `MemoriesScreen.kt:159,180`, `AssistantVoiceSettingsPanel.kt:83-84,97-98` |
| 9 | Sheet headers | **closed** | `PersonaPickerSheet.kt:77`, `PersonaEditorSheet.kt:87`, `AddBookmarkSheet.kt:90` |
| 10 | Ungated loop / raw springs | **closed** (`StreamingIndicator.kt:152` gate, `HeaderPulse`, no `spring(` in `MainActivity.kt`); residual `StreamingIndicator.kt:481`, 104dp | F12 |
| 11 | Empty-state families | **open, chat regressed** (chat empty now on the 24dp round token) | F3 |
| 12 | Top-bar families | **partial** (center-aligned bars gone; Large/small rule undecided) | seam note |
| 13 | Size ladder | **partial** (`ExpressiveSizes`/`ExpressiveSpacing` exist; icon ladder remains) | F11 |
| 14 | FAB tokens | **partial** (`ExtendedFab/FabPressed` gone; `Fab` deprecated, 0 consumers, test-pinned) | F5 |
| 15 | Alpha on text/icons | **closed** | seam note |
| new | Bubble preview vs bubble table | **new** | F1 |
| new | Raw capsules, raw M3 buttons, arena bundle, dead composables, cross-owner pins | **new** | F6, F8, F9, F14, F15 |

## Per-owner work list (paste into W7 briefs)

### theme
1. `ExpressiveEmptyState` -> `ExpressiveShapes.Card` + `ErrorContent` type pair (`ExpressiveSurfaces.kt:324`); consider one `StateCard` under both. [F3]
2. Delete `CustomShapes.Fab/BottomSheet/CodeBlock/ProviderCard/Dialog/MessageActionButton/SendButton` and their `ShapeTokensTest` asserts (`:27,33,38,46,205-206`); move the `AssistantShapes.*` asserts (`:174-178`) to the assistant test or derive `ExpressiveShapes.*Surface` from one radii record. [F5, F15]
3. Decide pressable geometry: `ShapeMorphing.buttonShape` returns a squircle, or document "pressables are round" in `Shapes.kt`; apply to `ExpressiveButton.kt:105,293,360,435,546`. [F7]
4. `ExpressiveFilterChip/AssistChip/SuggestionChip` wrappers with shape + `ExpressiveSizes.Chip.Height`. [F4]
5. `ExpressiveSizes.Icon = 20`, `IconSmall = 18`; unify `ExpressiveButton.kt:197,213,305,372,452`. [F11]
6. `UserFacingError` mapper + a test that fails on `e.message ?:` in `ui/**`. [F2]
7. `MaterialChatNavBar.kt:259` -> `CustomShapes.Pill`; `MainActivity.kt:343` 104dp -> `HomeListInsets`. [F6, F12]
8. Remove or consume `ExpressiveLinearIndicator`, `ExpressiveArcIndicator`, `ExpressiveLoadingIndicator`. [F14]
9. Either merge `Spatial.pressMorph`/`fast` or give them different values and update `AssistantMotionTest.kt:35`. [F12]
10. One `Copy` object for `TRY_AGAIN`/shared notice lines; collapse `ErrorContentCopyTest`/`ChatErrorCopyTest` cross-pins. [F15]

### chat
1. Export `bubbleShapeFor` (`MessageBubble.kt:919-970`) for settings; add a same-object test. [F1]
2. `ChatViewModel.kt:209,389,1084,1205,1233,1328,1616,2007` through the error mapper; no `e.message`. [F2]
3. Memory chip `MessageBubble.kt:1365,1375`: 40dp + token shape, or rename it a row. [F4]
4. `FusionModelSelector.kt:241` -> `ExpressiveButton`; `FusionBadge` delete or consume. [F9, F14]
5. `StreamingIndicator.kt:481` onto a duration token. [F12]
6. `MarkdownText.kt:1720` header corner onto a token. [F6]
7. Chat-empty shape: confirm the move to `ExpressiveEmptyState` is wanted once theme fixes its shape. [F3]

### home-settings
1. Delete `previewBubbleShape` (`SettingsScreen.kt:1971-1990`); consume chat's table. [F1]
2. Exception text: `MainViewModel.kt:60,73,86`; `SettingsScreen.kt:237,260,537,571,606`; `SettingsViewModel.kt:184,340,603-604,653,712,824,841,878,910`; `ModelAssignmentsViewModel.kt:147`; `WorkflowExecutionViewModel.kt:100,149`; `SearchViewModel.kt:103`; `PersonaStudioViewModel.kt:52`; `BookmarksViewModel.kt:59,81,111,125,154,168`; `ConversationsViewModel.kt:117`; `InsightsViewModel.kt:58`; `"Failed to share: ${e.message}"`. [F2]
3. `HomeEmptyState.kt:96,106,114` -> shared type pair + `ExpressiveButton`. [F3, F9]
4. 17 M3 chip sites onto the theme wrappers (list in F4). [F4]
5. Raw capsules: `ConversationItem.kt:194,219`, `ExpandableConversationGroup.kt:252,276,289`, `BranchConversationItem.kt:207`, `OnDeviceModelsScreen.kt:509`; `OnboardingScreen.kt:478`. [F6]
6. Arena: `ArenaPromptInput.kt:61` off `MessageInputContainer` (then delete the token); `ArenaVotingBar.kt:47,67,72` -> `ExpressiveButton`; `ArenaViewModel.kt:311` copy to "Vote recorded. Identities revealed." or two snackbars. [F8]
7. `ConversationsScreen.kt:1335,1466` -> `ErrorContent`. [F10]
8. `OnboardingScreen.kt:191,326` -> `ExpressiveButton`. [F9]
9. Top-bar rule: Large for home/settings only; confirm home bar type. [seam]

### assistant
1. Drop the `AssistantShapes.Answer/Notice/Thinking` radii record or make it the single source `ExpressiveShapes.*Surface` reads. [F5]
2. `AssistantExpressiveStyle.kt:111` `Toolbar` -> `ExpressiveShapes.Chip`/`CustomShapes.Pill`. [F6]
3. `AssistantOverlay.kt:1536` `FilledTonalButton` -> `ExpressiveButton` (or document why the overlay needs raw M3). [F9]
4. `ChipIcon` 16 vs token 18: adopt 18 or get the deviation ratified in `ExpressiveDimensions`. [F4, F11]
5. Move theme-value pins out of `AssistantMotionTest.kt:29-46` or make them aliases. [F15]

### unowned (assign in W7)
- `ConversationRepositoryImpl.kt:498,500`, `SendMessageUseCase.kt:825,1064`, `GenerateConversationTitleUseCase.kt:324,339`, `GetBuiltinPersonasUseCase.kt:104` (ASCII ellipsis in user-visible strings). [F13]

## Not established from source
- `ExpressiveControlShapes.kt` press spec (W4 F3 residual) - not re-opened.
- `ExpressiveEmptyState` type styles/padding (only shape and action lines read); exact chat composer container shape (`MessageInput.kt` read only at 565-584 and 974).
- `ExpressiveSurfaces.kt` disabled-content alpha; `ERROR_RETRY_LABEL` value in `LoadError.kt`; home top-bar component; `MainActivity.kt:343` 104dp vs `HomeListInsets`.
- Whether `ExpressiveLoadingIndicator`'s two in-file hits are definition + preview.
- Memory chip `height(48.dp)` line (offset-derived from `MessageBubble.kt:1375`).
- Everything rendered: no device, no emulator, no screenshots.
