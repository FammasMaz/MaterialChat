# W8 — theme pass report

Branch `pass/w8-theme`, base `6ade255c` (v2.41.7). Static work only: no device, no emulator, no screenshot. Every file:line below is from grep/sed on this branch after the edits.

Gates before each commit: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` (0 failures, 0 errors, 0 skipped at every commit).

## Commits

| Commit | Part | Unit tests |
|---|---|---|
| `3d9fcafc` | 1 — `CopyRules.RATE_LIMITED` / `NO_CONNECTION` / `COULD_NOT_SHARE` | 827 |
| `d22a6db8` | 2 — `ExpressiveButton` `colors` / `size` / `shape`; tertiary pair helpers | 830 |
| `fb8a0d34` | 3 — `ExpressiveFilterChip` / `AssistChip` / `SuggestionChip` | 832 |
| `976e8440` | 4 — four zero-caller composables + `CustomShapes.MessageInputContainer` deleted | 832 |
| `5cef7c9c` | 5 — top-bar rule KDoc; `ExpressiveShapes.ContentBlockHeader` pinned | 833 |
| (this) | 4 note dedupe; REPORT.md | 833 |

Base had 826 unit tests; 7 added (`CopyRulesTest` +1, `ExpressiveButtonColorsTest` +3, `ExpressiveChipsTest` +2, `ShapeTokensTest` +1). No androidTest added, so `scripts/run-connected-tests.sh` and the judge minimum are untouched.

## Part 1 — shared class lines (`3d9fcafc`)

- `ui/theme/CopyRules.kt:39` `RATE_LIMITED = "The model is busy right now. Wait a moment and try again."`; `:45` `NO_CONNECTION = "No connection. Check your network and try again."` — the exact strings `ChatErrorCopy.kt:32, 41` and `AssistantCopy.kt:56, 54` carry today. `:51` `COULD_NOT_SHARE = "Could not share"` (`== failureLine("share")`, the share-failure snackbar line).
- `ui/theme/CopyRulesTest.kt:35` `theClassLinesChatAndTheAssistantShareAreTheThemeConstants`: value pins, equality against both surfaces' constants, `isNotice` on both; `:52-53` `COULD_NOT_SHARE` pins. The two chat↔assistant cross-owner pins at `ChatErrorCopyTest.kt:117-118` can now move (C1 below).

## Part 2 — `ExpressiveButton` colours / size / shape (`d22a6db8`)

- `ui/components/ExpressiveButton.kt:95-97`: three new optional parameters, all defaulted so **no existing call site changes behaviour** (78 call sites across 20 files; none passes the new parameters). `colors: ExpressiveButtonColors?` (Filled / FilledTonal only; Outlined / Text ignore it), `size: ExpressiveButtonSize = Default`, `shape: Shape?` (null keeps the 28 -> 16dp round press morph; non-null is drawn as given and only the scale dip animates).
- `:223` `ExpressiveButtonColors(container, content)` with `tertiary(scheme)` `:226`, `tertiaryTonal(scheme)` `:230` and the composable overloads `:235, :239`. `:253` `ExpressiveButtonSize(minHeight, inset, textInset, icon)`; `Default` `:261` = 56 / 28 / 24 / `ExpressiveSizes.Icon` (the values every site drew); `Chip` `:267` = `ExpressiveSizes.Chip.Height / Inset / Inset / Icon`.
- KDoc at `:66-83` documents the API and that a hand-built pair is the caller's contrast responsibility; the two helper pairs are already in `standardContrastPairs` (`ThemeContrast.kt`: `onTertiary/tertiary`, `onTertiaryContainer/tertiaryContainer`).
- `ui/components/ExpressiveButtonColorsTest.kt:21, 29, 37`: Default and Chip presets pinned value for value; both tertiary pairs are the scheme's own roles and meet 4.5:1 in every static palette × light / dark / pitch-black (`withPitchBlackSurfaces`). Dynamic schemes are the system's choice and stay reported-only in `ThemeContrastTest`.

## Part 3 — chip wrappers (`fb8a0d34`)

- `ui/components/ExpressiveChips.kt` (new): `ExpressiveFilterChip` `:85`, `ExpressiveAssistChip` `:121`, `ExpressiveSuggestionChip` `:155`. Same parameter list as the M3 chip minus `shape`; `height(ExpressiveSizes.Chip.Height)` (40dp, not the M3 32dp), resting shape **is** `ExpressiveShapes.Chip` (the token object, so `shape ==` holds at rest), press morph capsule -> 12dp on `ExpressiveMotion.Press.Shape`, scale 0.95 on `Press.Scale`, click haptic (`HapticPattern.CLICK`, the `ExpressiveButton` pattern). Colours / borders stay the M3 defaults unless passed. `ExpressiveChipDefaults` `:41` holds the pure values.
- material3 1.5.0-alpha15 chip borders are plain `BorderStroke?`; `ChipBorder` / `SelectableChipBorder` are not public there (first compile failed on that import; fixed).
- `ui/components/ExpressiveChipsTest.kt:17, 23`: height / icon / inset alias the chip class; resting radius is half the height (a capsule at 40dp), pressed 12dp, scale 0.95.
- Consumers **not** migrated (ruling). The 17 M3 chip sites are listed per owner below.

## Part 4 — deletions (`976e8440`)

Word-exact grep over `app/src/main`, `app/src/androidTest`, `scripts/`, `.github/` for each name before deleting:

| Symbol | Callers | Action |
|---|---|---|
| `CustomShapes.MessageInputContainer` | 0 (three KDoc mentions: `ComposerSendButton.kt:51`, `ArenaPromptInput.kt:32`, `Shapes.kt:364`) | deleted (`Shapes.kt:473` note); KDoc at `Shapes.kt:375` now names `ComposerFieldRadius` |
| `M3ExpressiveDeterminateProgress` | 0 | deleted |
| `M3ExpressiveLoadingWithText` | 0 | deleted |
| `M3ExpressiveFullscreenLoading` | 0 | deleted |
| `MorphingSendButton` (+ private `calculateMorphParams`, `drawMorphingShapeFilled`) | 0 | deleted |
| `M3ExpressiveLoadingIndicator`, `M3ExpressivePulsingDots`, `M3ExpressiveInlineLoading` | in-file / `AddProviderSheet.kt:540`, `OnboardingScreen.kt:331` | kept |

`ExpressiveLoadingIndicator.kt` 731 -> 321 lines; 29 orphaned imports removed; `:249` records the deletion. No test referenced any deleted name.

## Part 5 — top-bar rule and header token (`5cef7c9c`)

- `ui/components/ExpressiveSurfaces.kt:127-142` KDoc on `ExpressiveTopBarTitle` (`:143`): `LargeTopAppBar` + `exitUntilCollapsed` only for Home (`ConversationsScreen.kt:236`, its own masthead) and Settings (`SettingsScreen.kt:440`); every pushed screen a small `TopAppBar` with a start-aligned title; no Medium / CenterAligned. Pinned at source level by `HomeSettingsTopBarRuleTest` (home-settings owned).
- `ui/theme/Shapes.kt:265` `ExpressiveShapes.ContentBlockHeader = RoundedCornerShape(topStart = 0, topEnd = 0, bottomEnd = 8, bottomStart = 8)` — the header strip of a markdown table (`MarkdownText.kt:1727`, square top edge against the block, 8dp bottom corners; the brief's `:1720` is `:1727` on this base). `ShapeTokensTest.kt:77-86`: exact value, bottom radii == `Badge`, not `Badge`, not `ContentBlock`, outline is a rounded rect.

## Part 6 — inventory (no edits; `app/src/main/java/com/materialchat`)

- **`RoundedCornerShape(` outside `ui/theme`: 35.** theme 6 (`ExpressiveButton.kt:125, 386, 453, 528, 639` press-animated corner, audit F7; `ExpressiveChips.kt:76` press-animated). chat 12 (`MessageInput.kt:566, 577, 585`; `MessageBubble.kt:1086, 1377`; `ExpressivePositionShapes.kt:33, 64`; `ScrollToBottomFab.kt:181`; `ModelPickerDropdown.kt:171`; `MessageActions.kt:428`; `ChatTopBar.kt:532`; `MarkdownText.kt:1727` -> `ContentBlockHeader`). home-settings 8 (`SwipeToDeleteBox.kt:232, 434`; `SettingsScreen.kt:1955`; `ActivityHeatmap.kt:156` exempt 3dp cells; `SegmentedRowShapes.kt:64`; `ExpandableConversationGroup.kt:316`; `BranchConversationItem.kt:134`; `ArenaPromptInput.kt:34` 28dp = `ExpressiveShapes.Card` radius, round). assistant 9 (`AssistantExpressiveStyle.kt:92, 115, 127`; `AssistantOverlay.kt:885, 1074, 1136, 1612, 1659, 1778`). All but `MarkdownText.kt:1727` and `ArenaPromptInput.kt:34` are animated or per-position corners, which have no static token by design.
- **`spring(` / `tween(` outside `ui/theme`: 0** (one comment hit, `StreamingIndicator.kt:466`).
- **`.copy(alpha =` : 97 raw hits**, of which 8 sit on a `color = / tint = / trackColor =` argument: `ContextWindowIndicator.kt:73, 100` (track washes), `MessageBubble.kt:716` (progress-driven swipe icon tint, documented exempt), `ArenaLeaderboard.kt:207, 254` (fill / track), `MemoriesScreen.kt:307` (fill), `ExpressiveSurfaces.kt:229` (M3 disabled-content alpha), `ImageViewerDialog.kt:121` (scrim container). None is a resting text or icon colour.
- **`e.message` in `ui/**`: 1 Kotlin site**, `ChatErrorCopy.kt:107` (in-stream provider error text passed through as the notice; chat). The other three hits are KaTeX JavaScript inside string templates (`ArtifactHtmlTemplates.kt:330`, `MarkdownText.kt:1638, 1699`). Every `e.message ?: "Failed to …"` site from W6 #2 is gone.
- **Banned failure verbs in string literals: 0 in `ui/**` and `assistant/ui/**`.** Remaining: `util/AttachmentImporter.kt:61, 68, 82, 101` ("Couldn't … this image", user-visible `ImportResult.Failure`), `assistant/voice/TextToSpeechManager.kt:118, 128`, `data/repository/UpdateManager.kt:97, 105` ("Failed to check for updates", with `e.message` pass-through), `data/remote/**` (exception / log text, 11 sites).
- **ASCII `...` in string literals: 1 user-visible**, `domain/usecase/GetBuiltinPersonasUseCase.kt:104` (unowned, carried from W6/W7). Others are comments, JSON prompt templates (`ExtractMemoriesUseCase.kt:375`) and the predicate itself (`CopyRules.kt:90`).
- **`FontStyle.Italic`: 0.**
- **Raw M3 `Button` / `TextButton` / `FilledTonalButton` / `OutlinedButton` outside `ui/components`: 12.** `AssistantOverlay.kt:1566` `FilledTonalButton` (the 40dp chip; now migratable, A1). `ComposerSendButton.kt:191` `Button` (bespoke send morph, exempt). Dialog-action `TextButton`s on `dialogActionButtonColors()` (documented exception): `AssistantVoiceSettingsPanel.kt:279, 290`; `ConversationsScreen.kt:358, 371, 386, 407, 424, 912`; `MemoriesScreen.kt:167, 179`. The W6 non-dialog raw sites (`HomeEmptyState`, `OnboardingScreen`, `ArenaVotingBar`, `FusionModelSelector`) are all on `ExpressiveButton` now.

## Migration table — what each owner consumes from this pass

Nothing deleted had a consumer outside the theme, so no rebase conflict is expected from a deletion. Rows marked **required** are needed for the stated goal; the rest are optional consumption.

### chat

| # | File:line | Change | Notes |
|---|---|---|---|
| C1 | `ui/screens/chat/components/ChatErrorCopy.kt:32, 41` | `const val RATE_LIMITED = CopyRules.RATE_LIMITED`; `const val NO_CONNECTION = CopyRules.NO_CONNECTION` | value-identical; `CopyRulesTest:35` already pins equality |
| C2 | `ChatErrorCopyTest.kt:117-118` | delete the two `AssistantCopy.*` cross-owner pins (the comment at `:114-116` says they wait on a `CopyRules` constant; it exists now) | the last chat↔assistant pins |
| C3 | `ui/screens/chat/components/FusionModelSelector.kt:241-251` | `ExpressiveButton(..., style = Filled, colors = ExpressiveButtonColors.tertiary())` and drop the `:240-244` comment saying the tertiary fill is not a parameter | **visible change**: the Start fusion button regains its tertiary tint (W7 chat part 2 lost it) |
| C4 | `ui/components/MarkdownText.kt:1727` | `RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)` -> `ExpressiveShapes.ContentBlockHeader` | value-identical; retires the last static raw corner in chat |
| C5 | any share-failure snackbar | `CopyRules.COULD_NOT_SHARE` (== `failureLine("share")`) | grep found no `"Failed to share"` left on this base; row kept so the constant has a named consumer |
| C6 | `ChatErrorCopy.kt:107` | the one `e.message`-class pass-through left in `ui/**` (in-stream provider error on a 200) | chat decision; listed, not ruled |

### home-settings

| # | File:line | Change | Notes |
|---|---|---|---|
| H1 | `ui/screens/insights/InsightsScreen.kt:149` | `FilterChip(` -> `ExpressiveFilterChip(`, drop `shape =` | **visible**: 32 -> 40dp, press morph, haptic |
| H2 | `ui/screens/settings/ModelAssignmentsScreen.kt:674, 704` | same | same |
| H3 | `ui/screens/settings/InteractionSettingsScreen.kt:208, 237` | same | same |
| H4 | `ui/screens/settings/SettingsScreen.kt:2568, 2670` | same | same |
| H5 | `ui/screens/personas/components/PersonaEditorSheet.kt:169` | same | same |
| H6 | `ui/screens/bookmarks/components/BookmarkFilterBar.kt:81, 136` | same | same |
| H7 | `ui/screens/bookmarks/components/AddBookmarkSheet.kt:121, 163` | same | same |
| H8 | `ui/screens/arena/components/ArenaModelPicker.kt:136` | same | same |
| H9 | `ui/screens/bookmarks/components/BookmarkCard.kt:155`; `ArenaModelPicker.kt:76` | `AssistChip(` -> `ExpressiveAssistChip(`, drop `shape =` | same |
| H10 | `ui/screens/personas/components/ConversationStarterChips.kt:44`; `PersonaCard.kt:180` | `SuggestionChip(` -> `ExpressiveSuggestionChip(`, drop `shape =` (`:54` `CustomShapes.Pill` goes with it) | same |
| H11 | `ui/screens/arena/components/ArenaPromptInput.kt:34` | `RoundedCornerShape(28.dp)` -> chat's `ComposerFieldRadius` or `ExpressiveShapes.Card` per the W7 chat note; `:32` KDoc no longer needs to mention the deleted token | value-identical if round is kept |
| H12 | `util/AttachmentImporter.kt:61, 68, 82, 101`; `data/repository/UpdateManager.kt:97, 105` | `CopyRules.failureLine("read this image")` etc.; no `e.message` pass-through | outside `ui/**`; assign (home-settings shows both) |

H1–H10 are the 17 M3 chip sites (audit F4). Every one passes `shape = ExpressiveShapes.Chip` today; the wrapper fixes that, so the only diff per site is the composable name and the dropped `shape` line.

### assistant

| # | File:line | Change | Notes |
|---|---|---|---|
| A1 | `assistant/ui/AssistantOverlay.kt:1554-1580` `AssistantActionChip` | `ExpressiveButton(text = label, leadingIcon = icon, style = FilledTonal, colors = ExpressiveButtonColors(palette.chip.container, palette.chip.content), size = ExpressiveButtonSize.Chip, shape = shape)` and drop the `:1544-1552` "deliberately raw" KDoc | **behaviour diff to weigh before taking it**: the raw chip presses on M3 `ButtonShapes(shape, pressedShape = ConnectedPressed)`; `ExpressiveButton` with a non-null `shape` does not morph the corner (scale dip only). Icon would be 18dp (`Chip.Icon`) not 16 (`AssistantSizes.ChipIcon`, ruling) unless a custom `ExpressiveButtonSize(icon = 16.dp)` is passed. Label weight is `labelLarge` Bold, not `AssistantTypography.action`. Optional; listed, not required |
| A2 | `assistant/ui/AssistantCopy.kt:54, 56` | `const val NO_CONNECTION = CopyRules.NO_CONNECTION`; `const val RATE_LIMITED = CopyRules.RATE_LIMITED` | value-identical; equality pinned in `CopyRulesTest:35` |
| A3 | `AssistantExpressiveStyle.kt:92, 115` | no change required; `ExpressiveChipDefaults.PressedRadius` (12dp) is the same value as `ConnectedPressedCorner` if the two are meant to agree — pin or alias is the assistant's call | — |

### orchestrator / unowned

- `domain/usecase/GetBuiltinPersonasUseCase.kt:104` ASCII ellipsis -> `CopyRules.ELLIPSIS` (third wave unassigned).
- `assistant/voice/TextToSpeechManager.kt:118, 128` "Failed to initialize text-to-speech" (`:128` with `error.message` pass-through) — assistant or voice owner.
- `data/remote/**` "Failed to parse / fetch" strings are exception messages; whether any reaches a snackbar unmapped is a per-ViewModel question (`ChatErrorCopy` / `AssistantCopy` map typed errors, not these strings).

## Deliberately left alone

- `ExpressiveShapes.Chip` / `CustomShapes.Pill`, `expressiveControlShape` on `shapeMorph()`, assistant `ChipIcon` 16, the empty-state card on all six screens: rulings.
- Pressable round-vs-squircle (`ExpressiveButton.kt:125, 386, 453, 528, 639`, audit F7): not in this brief. The new `shape` parameter lets a site pass a squircle without deciding it for everyone.
- `CustomShapes.Button / ButtonPressed / ConversationItem / Dropdown / ModelPicker / Pill / TopAppBar / SearchBar`: live consumers.
- `ChatErrorCopy.kt:107` pass-through: chat-owned decision (C6).
- `SmoothStreamingText.kt`: untouched.

## Needs another owner

- **chat**: C1–C2 (move the last cross-owner pins), C3 (**the one visible fix**: tertiary Start fusion), C4, C5, C6.
- **home-settings**: H1–H10 (17 chip sites, visible 32 -> 40dp), H11, H12.
- **assistant**: A2 (alias), A1 optional with the three behaviour diffs noted.
- **orchestrator**: the three unowned rows above; whether `ExpressiveButton` with a fixed `shape` should also animate a pressed shape (would need a `pressedShape` parameter; not added because no current site asked for it).

## Not verifiable without a device

- Part 2: `ExpressiveButton` with `colors` / `size = Chip` / a fixed `shape` was never rendered; the defaults are pinned equal to the old literals but the three new branches (`ButtonDefaults.buttonColors(container, content)`, `filledTonalButtonColors(...)`, `shape ?: RoundedCornerShape(...)`) have no caller on this branch, so nothing draws them yet. Disabled-state colours of a custom pair are M3's derived ones, unchecked against any fill.
- Part 3: the chip wrappers' 40dp height inside `FilterChip`'s own min-height, the capsule -> 12dp press morph, the scale dip, and the haptic were not rendered or pressed; the resting-shape identity (`== ExpressiveShapes.Chip`) is pinned by value only. `RoundedCornerShape(50)` (percent) vs the animated `RoundedCornerShape(20.dp)` are the same outline at 40dp height; at a different measured height the resting capsule is the token, the pressed one is dp-based.
- Part 5: `ContentBlockHeader` draws the same strip as `MarkdownText.kt:1727` only once chat consumes it (C4); value pinned, not rendered.
- Parts 1, 4: additive constants and deletions with zero consumers; nothing renders differently.
- No screenshot, emulator or hosted run was made.
