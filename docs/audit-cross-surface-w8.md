# W8 cross-surface audit (read-only)

Repo `6ade255c` (v2.41.7), main checkout. Static reading only: no build, no device, no emulator, no edits to source, no commits. Every file:line is from grep/sed on this commit; anything taken from a W7 report and not re-opened here is marked *per W7*. Inputs: `docs/audit-cross-surface-w{4,6}.md`, `docs/reports/pass-w7-{theme,chat,home-settings,assistant}.md`. Owners as before: **theme** (`ui/theme`, `ui/components`, `ui/navigation`, `MainActivity`), **chat** (`ui/screens/chat`, `Markdown*`), **home-settings** (home, settings, drawer screens, arena, onboarding, `MainViewModel`), **assistant** (`assistant/**`, the two assistant settings panels).

## 1. W6 -> W8 status table

| W6 # | Finding | Status | Evidence at `6ade255c` |
|---|---|---|---|
| 1 | Settings bubble preview draws a different shape table than the bubble | **closed** | `SettingsScreen.kt:1981-1991` `previewBubbleShape` reads `ExpressiveShapes.Bubble.Rounded/Compact.*/Geometric.*`; `MessageBubble.kt:939-940,960-964` `bubbleShapeFor` reads the same tokens; record at `Shapes.kt:267-282`; pinned by `ShapeTokensTest.bubbleFamilyTokensCarryTheChatRadiiExactly` (*per W7 theme*) |
| 2 | Exception text reaches the user from 40+ sites | **closed in `ui/**`**, **carried (narrow)** in `assistant/voice`, **new sink-side gap** | `grep 'message ?:' ui/**` -> only `ChatViewModel.kt:1265` (`?.message ?: return` on a domain `Message`, not an exception). `assistant/voice`: `TextToSpeechManager.kt:128` `error.message ?: "Failed to initialize text-to-speech"`, `:156` `error.message ?: "Voice configuration failed"`, `:118` `error("Failed to initialize ...")` (banned verb); `SherpaModelStore.kt:172` `failure.message ?: VoiceCopy.INSTALL_FAILED`. Whether these reach the pill / install row: consumer `AssistantViewModel.kt:183` (`is TtsInitResult.Error`) and `AssistantVoiceSettingsViewModel.kt:107,138` (type check only) -> *not established*. Sink gap: `MonetizationManager.kt:171` "Rewarded ad is loading. Please try again shortly." -> `SettingsViewModel.kt:420-422` `ShowSnackbar(message)` unchanged (see fresh #3). Scanners: `ChatCopyRulingsTest.kt:27-30` (chat), `HomeSettingsCopyRulingsTest.kt:24-45` (home); none over `assistant/**` or `data/**` beyond `NativeAuthManager` |
| 3 | Empty and error state: same card, two shapes | **closed** | `ErrorContent.kt:119` `shape = StateCardShape`, `:173` `= ExpressiveShapes.Card`; `ExpressiveSurfaces.kt:312` `ExpressiveStateCard(`; `ErrorContent.kt:148,155` headlineSmall Bold / bodyMedium; `HomeEmptyState` on the type pair + `ExpressiveButton` *per W7 home* (file lists `ExpressiveButton`, body not re-opened) |
| 4 | Chip class: heights 32/40/48 | **carried** | 17 M3 chip sites (`InsightsScreen`, `ModelAssignmentsScreen`, `InteractionSettingsScreen`, `SettingsScreen`, `ConversationStarterChips`, `PersonaCard`, `PersonaEditorSheet`, `BookmarkFilterBar`, `BookmarkCard`, `AddBookmarkSheet`, `ArenaModelPicker`) each pass `shape = ExpressiveShapes.Chip` by hand; 0 set a height (grep `height(` on those lines: 0) -> M3 32dp. `ExpressiveSizes.Chip.Height` has one consumer: `AssistantExpressiveStyle.kt:192`. No `ExpressiveFilterChip/AssistChip` wrapper exists. Memory chip ruled a row: `MessageBubble.kt:1322-1330` KDoc, `:1367` `height(ExpressiveSizes.Target)`. `ChipIcon` 16 ruled (`AssistantExpressiveStyle.kt:213-220` *per W7 assistant*) |
| 5 | Dead-but-pinned shape tokens; duplicated radii record | **closed** | `CustomShapes` now `InlineCode/Button/ButtonPressed/MessageInputContainer/ConversationItem/Dropdown/ModelPicker/Pill/TopAppBar/SearchBar` (`Shapes.kt:450-503`); `Fab/BottomSheet/CodeBlock/ProviderCard/Dialog/MessageActionButton/SendButton` 0 hits anywhere. `AssistantShapes` holds only `PillCorner/Pill/Toolbar` (`AssistantExpressiveStyle.kt:89-103`); `Answer/Notice/Thinking` gone (comment `:95`). **New dead tokens**: `CustomShapes.MessageInputContainer` (`Shapes.kt:465`; 0 consumers, 2 comment references `ComposerSendButton.kt:51`, `ArenaPromptInput.kt:32`); `ExpressiveSizes.TileGlyph` 0; `ExpressiveSpacing.RowGap` 0; none test-pinned (grep in `app/src/test`: 0) |
| 6 | Raw capsules / raw corners where a token exists | **closed** | `RoundedCornerShape(50|999.dp)` outside theme: 0 (only `Shapes.kt:488`). `AssistantShapes.Toolbar = CustomShapes.Pill` (`:103`). Raw `RoundedCornerShape(N.dp)` outside theme: `ArenaPromptInput.kt:34` (28dp, file-private with ruling KDoc `:29-33`), `ActivityHeatmap.kt:156` (3dp cells, exempt), `MarkdownText.kt:1727` (`bottomStart/bottomEnd = 8.dp`, one-sided, no token). `OnboardingScreen.kt:478` 44dp and `DateSeparator.kt:81`: no hit (gone). `CircleShape` outside theme: 17 sites (list in fresh #10), still undecided |
| 7 | Animated pressables round, static containers squircle | **carried; now three answers** | Round morph: `ExpressiveButton.kt:106,294,361,436,547`, `ScrollToBottomFab.kt:181`, `ChatTopBar.kt:532`, `ModelPickerDropdown.kt:171`, `MessageBubble.kt:1377`, `BranchConversationItem.kt:134`, `AssistantOverlay.kt:1778`, `ShapeMorphing.kt:54,78,102,125,149,168,187`, `ExpressiveControlShapes.kt:64,76` (9 consumers). Squircle morph: `MessageInput.kt` composer field `SquircleShape(fieldRadius)` on `Spatial.shapeMorph()` (`ComposerSendButton.kt:49-64` ruling). Static squircle: `PersonaCard` (`ExpressiveShapes.Card`, corner morph removed, *per W7 home* Part 7; `PersonaCard.kt:104` no longer in the animated-corner grep). W7 theme: "not in this brief; untouched" |
| 8 | Arena drift bundle | **closed** | `ArenaPromptInput.kt:34,68` `ArenaComposerShape`; `ArenaVotingBar.kt:50,66,73` `ExpressiveButton`; `ArenaViewModel.kt:319` "Vote recorded, identities revealed" |
| 9 | Raw M3 buttons outside `ui/components` | **closed** | Remaining raw: `AssistantOverlay.kt:1566` `FilledTonalButton` (documented exemption `:1540-1550` *per W7 assistant*), `ComposerSendButton.kt:191` `Button` (Morph contour, documented `:110-119`), dialog `TextButton`s on `dialogActionButtonColors()` (`ConversationsScreen.kt:358-424,912`, `MemoriesScreen.kt:167,179`, `AssistantVoiceSettingsPanel.kt:279,290`) per convention. `HomeEmptyState`, `OnboardingScreen`, `FusionModelSelector`, `ArenaVotingBar`: 0 raw hits |
| 10 | Search error off `ErrorContent` | **closed** | `ConversationsScreen.kt:1339` `ErrorContent(`; `:855` `LoadErrorContent(` |
| 11 | Icon size ladder | **partial** | Tokens: `ExpressiveSizes.Icon` 7 consumers, `IconSmall` 6, `IconLarge` 29; `ExpressiveButton` on tokens (*per W7 theme*). Literals `size(16|18|20|22|24.dp)` remaining: `ui/screens/chat` **32** in 13 files (`RedoModelPickerSheet.kt:106,181`, `MessageActions.kt:333`, `FusionSourcesSection.kt:75` (16), `WebSearchSourcesCarousel.kt:135,174`, `QuoteReplyPreview.kt:139`, `ExportBottomSheet.kt:130,211`, `ModelPickerDropdown.kt:202` (16),`:260,349`, `MessageInput.kt:646,1010,1019` (16), `ContextWindowIndicator.kt:101`, `ScrollToBottomFab.kt:172` (22), `BeautifulModelBadges.kt:251,264,416,451,474,559,715,745,861`, `FusionModelSelector.kt:206,228`, `MessageBubble.kt:710,1101,1667,1711`); `ui/components` 3 (`ExpressiveLoadingIndicator.kt:622`, `ExpressiveFastScrollBar.kt:443`, `MarkdownText.kt:715`); settings 5; conversations 3; navigation 0; assistant 0 |
| 12 | Motion: identical tokens, raw tween, 104dp | **closed** | `Motion.kt:211` `pressMorph() = fast()`; raw `spring(`/`tween(` outside `ui/theme`: 0 (the `StreamingIndicator.kt:466` hit is a comment); `MainActivity.kt:351` `listBottomClearance()`, `:389` `HomeListInsets.ToolbarBottomMargin`. Spring table (`Motion.kt`): default 0.8/380, shapeMorph 0.6/500, scale 0.6/600, container 0.6/380, expand 0.75/420, collapse 1.0/420, Spatial.fast 0.6/800, Effects.fast 1.0/3800 -> no other value-identical pair. `ExpressiveControlShapes.kt` spring spec: still *not re-opened* (W4 F3 residual) |
| 13 | ASCII ellipsis in domain strings | **closed** bar one | `ConversationRepositoryImpl`, `SendMessageUseCase`, `GenerateConversationTitleUseCase`: 0 hits. Left: `GetBuiltinPersonasUseCase.kt:104` "Challenge my argument that..." (unowned). `ExtractMemoriesUseCase.kt:375` is a JSON schema in a model prompt: exempt. `CopyRules.kt:71` is the rule |
| 14 | Dead composables | **partial** | `FusionBadge`, `CanvasRefinementInput`, `M3ExpressiveCircularProgress/LinearProgress`: 0 hits in `main`. Still 0 callers (1 hit = definition): `ExpressiveLoadingIndicator.kt:337` `M3ExpressiveDeterminateProgress`, `:379` `M3ExpressiveLoadingWithText`, `:421` `M3ExpressiveFullscreenLoading`, `:464` `MorphingSendButton` (+ private `:637` `calculateMorphParams`, `:672` `drawMorphingShapeFilled`); not pinned by any test. Stale KDoc: `Motion.kt:557,654` name the deleted `M3ExpressiveCircularProgress` |
| 15 | Cross-owner test pins | **closed** bar one line | `CopyRulesTest.kt:3,5,22-30,65-70` is now the designated cross-owner pin (theme owns it). `MotionTokensTest.kt:30,67-68,106` pins theme's own tokens. `AssistantMotionTest.kt:31,52,61,66` compares assistant aliases to theme specs (assistant contract). Residual: `ChatErrorCopyTest.kt:117-118` pins `AssistantCopy.RATE_LIMITED/NO_CONNECTION` because `CopyRules` has no such constants (chat W7 asked theme). `HomeSettingsContrastSlotsTest.kt:5,22` benign |
| seam | Snackbar family | closed | hosts in Bookmarks, Workflows, Memories, Arena, Chat (grep); 0 snackbar literals ending in `.` or `!` |
| seam | Top bars | closed | `HomeSettingsTopBarRuleTest` (*per W7 home*); `ExpressiveTopBarTitle` KDoc still missing (`ExpressiveSurfaces.kt:123-124`) |
| seam | Dialogs / sheets | closed | 10 `AlertDialog`s all with `shape = ExpressiveShapes.Dialog` and `dialogEntrance()` (`SettingsScreen.kt:2698,2751,2803`, `OnDeviceModelsScreen.kt:185`, `AssistantVoiceSettingsPanel.kt:84,98`, `WorkflowExecutionScreen.kt:176`, `ConversationsScreen.kt:340,884`, `MemoriesScreen.kt:160`); 7 `ModalBottomSheet`s all `ExpressiveShapes.BottomSheet` |
| seam | Italic / alpha-on-text | closed | `FontStyle.Italic` 0. `.copy(alpha` on content: only progress-/animation-driven (`MessageBubble.kt:716`, `StreamingIndicator.kt:187`, `ExpressiveLoadingIndicator.kt:325`, `FusionAnimation.kt:93,103`, `MindMapCanvas.kt:495`) or fills/washes/tracks |

Totals: **closed 10, partial/carried 5 (F2 residual, F4, F7, F11, F14), regressed 0.**

## 2. Fresh findings (ranked = impact x spread)

### 1. Pressable corner class has three answers (theme)
Round morph on every `ExpressiveButton` and the chat/assistant pressables (F7 list), squircle morph on the composer field (`MessageInput.kt`, `SquircleShape(fieldRadius)`), static squircle on `PersonaCard`. `ShapeMorphing.kt:54-187` is the one place the round morph is produced; `SquircleShape(radius: Dp)` exists (`Shapes.kt:130`). Proposed: one ruling in `Shapes.kt` KDoc ("pressables morph round; cards never morph") and `PersonaCard` either gets its morph back or the rule says cards do not morph. Cosmetic; no device risk.

### 2. Chip height 32 (home/settings/drawer) vs 40 (assistant) (theme + home-settings)
F4 carried unchanged for three waves because the theme wrappers never shipped and home refused to duplicate them 17 times. Decide: ship `ExpressiveFilterChip/AssistChip/SuggestionChip` in `ui/components` (shape + `ExpressiveSizes.Chip.Height`), or ratify the M3 32dp for M3 chips and reword the `Chip.Height` KDoc as assistant-only. This is the only remaining cross-surface size seam a user could notice.

### 3. Unowned copy reaches a settings snackbar past `CopyRules` (home-settings sink; data unowned)
- `MonetizationManager.kt:171` `"Rewarded ad is loading. Please try again shortly."` -> `SettingsViewModel.kt:420-422` emits it verbatim as `ShowSnackbar`. Fails `CopyRules.isPhrase` (trailing period, "please"). Play flavor only (rewarded ads); the GitHub release never shows it.
- `UpdateManager.kt:199` `"APK file not found. Please download again."` -> `updateState` collected at `SettingsViewModel.kt:244`; whether the message field is rendered: *not established*.
- 20 `"Failed to ..."` literals in `data/**` (`UpdateManager`, `AttachmentImporter`, `ChatApiClient`, `ModelListApiClient`, `GitHubReleaseApiClient`, `SseEventParser`); the only established user path is `SseEventParser` PARSE_ERROR as the documented unclassified diagnostic (*per W7 chat*).
- `NativeAuthManager.kt:152` `IOException("GitHub device code expired. Please try again.")`, `:474` `LoopbackResult.Failure(.., "State mismatch. Please retry sign-in.")`, `:544` loopback HTML "Please retry sign-in." -- consumer of the `Failure` second argument not found by grep: *not established*.
- `HomeSettingsCopyRulingsTest.kt:43-46` scans `MainViewModel`, `MainActivity`, `NativeAuthManager` only outside `ui/`; `data/monetization`, `data/repository` are unscanned. Proposed: map at the sink (`SettingsViewModel.kt:244,420`) through `CopyRules.failureLine`/a notice, or extend the scanner's `ownedFiles`.

### 4. Chat icon literals (chat)
32 sites (F11 row). 28 are value-identical to the ladder (18/20/24); 4 are off-ladder: `FusionSourcesSection.kt:75`, `ModelPickerDropdown.kt:202`, `MessageInput.kt:1019` (16dp), `ScrollToBottomFab.kt:172` (22dp). Home migrated its 33 in W7; chat migrated only `ComposerSendButton`. Theme's 2 (`ExpressiveLoadingIndicator.kt:622`, `ExpressiveFastScrollBar.kt:443`) and chat's `MarkdownText.kt:715` likewise. Value-identical except the four.

### 5. Assistant/voice exception pass-through, and no assistant copy scanner (assistant)
`TextToSpeechManager.kt:118` (`error("Failed to initialize text-to-speech")`), `:128`, `:156` (`error.message ?: ...`), `SherpaModelStore.kt:172` (`failure.message ?: VoiceCopy.INSTALL_FAILED`). `SherpaTtsBackend.kt:92,134` wrap into typed exceptions (chaining, not copy). Reach: `AssistantViewModel.kt:183` handles `TtsInitResult.Error` (branch body not read here); `SherpaInstallEvent.Failed(message)` presumably feeds the install row. Chat and home each have an on-disk scanner that fails on `.message ?:`; `assistant/**` has none (the only assistant test reading main sources is `AssistantKeyboardSpacingTest`).

### 6. Dead code and dead tokens (theme)
`ExpressiveLoadingIndicator.kt:337,379,421,464` + `:637,:672` (~300 lines, 0 callers, unpinned); `CustomShapes.MessageInputContainer` (`Shapes.kt:465`), `ExpressiveSizes.TileGlyph`, `ExpressiveSpacing.RowGap` (0 consumers, unpinned); `Motion.kt:557,654` KDoc names a deleted composable. Nothing here is "held alive by a pin" any more; it is plain dead.

### 7. Em-dash punctuation drift (theme rule; home-settings sites)
`HomeSettingsCopyRulingsTest` bans the em dash in snackbar literals and W7 rewrote the arena snackbar, but `CopyRules` has no em-dash rule, and six supporting/status lines keep it: `ModelAssignmentsScreen.kt:523,620`, `SettingsScreen.kt:937`, `WorkflowExecutionScreen.kt:476,528`, `ArenaModelPicker.kt:91`. (`ModelAssignmentsScreen.kt:315` "—" as an empty-value glyph is fine.) Decide once in `CopyRules`.

### 8. Copy scanner overclaims theme-owned components (home-settings)
`HomeSettingsCopyRulingsTest.kt:40` lists `ui/components` as owned, excluding only six files (`:49-56`) and `Markdown*`. That scans theme-owned `ExpressiveButton.kt`, `ExpressiveLoadingIndicator.kt`, `ExpressiveFastScrollBar.kt`, `HapticFeedbackManager.kt`, `DeviceShake.kt`, `HomeSettingsContrast.kt`, `DialogActionColors.kt`, ... Benign today (the rules are the shared `CopyRules`), but a home-owned test can now go red on a theme edit. Category 9 residual.

### 9. Last chat<->assistant copy pin (theme)
`ChatErrorCopyTest.kt:117-118`. Add `CopyRules.RATE_LIMITED` / `NO_CONNECTION` (chat and assistant lines are already identical) and move the pin into `CopyRulesTest`.

### 10. `CircleShape` never ruled (theme)
17 sites: `MaterialChatNavBar.kt:275`, `SettingsScreen.kt:1718,1724`, `ChatTopBar.kt:583`, `FusionProgressView.kt:218`, `StreamingIndicator.kt:98,368`, `MessageInput.kt:1011`, `ContextWindowIndicator.kt:58`, `ExploreScreen.kt:318,348`, `PersonaPickerSheet.kt:105,180`, `PersonaCard.kt:120`, `HomeStatusChip.kt:46`, `ArenaBattleView.kt:100`, `ExpressiveLoadingIndicator.kt:142`. All dots/avatars/glyph circles. One KDoc line in `Shapes.kt` ("`CircleShape` is allowed for glyphs and avatars; never for a pressable container") closes it.

### 11. `ExpressiveTopBarTitle` KDoc (theme)
`ExpressiveSurfaces.kt:123-124` has no KDoc; home's `HomeSettingsTopBarRuleTest` pins the rule but the shared component does not state it.

### 12. `MarkdownText.kt:1727` (chat / theme)
One-sided `RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)` for the table header; no one-sided token exists. Either a `ExpressiveShapes.TableHeader` token or a KDoc exemption.

### Clean sweeps (no finding)
- Raw `spring(` / `tween(` outside `ui/theme`: 0. `FontStyle.Italic`: 0. "Retry" literal in `ui/**`/`assistant/**`: 0 (3 comment hits). "please" in `ui/**`: 0. Snackbar literals ending in `.`/`!`: 0. `!` in user strings: 0.
- `while (true)`: `MaterialChatNavBar.kt:161,173` are gesture-event loops; `EdgeGlow.kt:241` is gated by `shouldRequestEdgeGlowFrames(visible, presence, control.motionEnabled, animationScale)` (`:233-236`); `SettingsScreen.kt:3439` is the backup-file read loop. Every `rememberInfiniteTransition` file references the motion gate (counts per file >= 1).
- Dialogs 10/10, sheets 7/7 on tokens (seam rows above). Snackbar hosts present on the five drawer/chat screens.
- `SpeechRecognitionManager.kt:109,123,211` "Please try again." are mapped away at `AssistantViewModel.kt:373` -> `AssistantCopy.recognitionError` (*per W7 assistant*, pinned by `AssistantCopyTest`).

## 3. Per-owner work list (paste into W9 briefs)

### theme
1. Rule the pressable corner class in `Shapes.kt` KDoc and `ShapeMorphing.kt`; apply to `PersonaCard` either way. [#1]
2. Chip decision: ship `ExpressiveFilterChip/AssistChip/SuggestionChip` with `ExpressiveSizes.Chip.Height`, or ratify 32dp for M3 chips and reword the token KDoc. [#2]
3. Delete `ExpressiveLoadingIndicator.kt:337,379,421,464,637,672`; delete `CustomShapes.MessageInputContainer` (`Shapes.kt:465`), `ExpressiveSizes.TileGlyph`, `ExpressiveSpacing.RowGap`; fix `Motion.kt:557,654` KDoc. [#6]
4. `CopyRules`: add `RATE_LIMITED`, `NO_CONNECTION`; add the em-dash rule; add the `CircleShape` glyph rule to `Shapes.kt`. [#7, #9, #10]
5. KDoc on `ExpressiveTopBarTitle` (`ExpressiveSurfaces.kt:123`). [#11]
6. `ExpressiveLoadingIndicator.kt:622`, `ExpressiveFastScrollBar.kt:443` -> `ExpressiveSizes.IconLarge`. [#4]
7. Optional: one-sided `ExpressiveShapes.TableHeader` for `MarkdownText.kt:1727`. [#12]

### chat
1. 32 icon literals -> `ExpressiveSizes.Icon/IconSmall/IconLarge`; rule the four off-ladder sites (`FusionSourcesSection.kt:75`, `ModelPickerDropdown.kt:202`, `MessageInput.kt:1019` 16dp; `ScrollToBottomFab.kt:172` 22dp). [#4]
2. `MarkdownText.kt:715` -> `IconSmall`; `:1727` onto the token or KDoc exemption. [#4, #12]
3. Drop `ChatErrorCopyTest.kt:117-118` once `CopyRules` carries the two lines. [#9]

### home-settings
1. Map `MonetizationManager` / `UpdateManager` text at the `SettingsViewModel` sink (`:244`, `:420-422`) through `CopyRules`, or extend `HomeSettingsCopyRulingsTest.ownedFiles` to `data/monetization/MonetizationManager.kt`, `data/repository/UpdateManager.kt`. [#3]
2. Six em-dash lines (`ModelAssignmentsScreen.kt:523,620`, `SettingsScreen.kt:937`, `WorkflowExecutionScreen.kt:476,528`, `ArenaModelPicker.kt:91`) per the theme rule. [#7]
3. Narrow `HomeSettingsCopyRulingsTest.kt:40,49-56` to the components home actually owns. [#8]
4. Remaining icon literals: settings 5, conversations 3. [#4]
5. 17 chip sites onto the wrappers once theme ships them (or nothing, if 32 is ratified). [#2]

### assistant
1. `TextToSpeechManager.kt:118,128,156`, `SherpaModelStore.kt:172`: no exception text, no "Failed to". [#5]
2. An on-disk copy scanner over `assistant/**` (pattern: `ChatCopyRulingsTest.kt:101-160`). [#5]
3. Nothing else: Part 2/4 exemptions stand.

### unowned (assign in W9 or leave)
- `domain/usecase/GetBuiltinPersonasUseCase.kt:104` ASCII ellipsis. [F13]
- `data/auth/NativeAuthManager.kt:152,474,544` "please" lines (user reach not established).

## 4. CONVERGENCE VERDICT

**Converged. A fourth full implementation wave (four owners, four worktrees, rebases) is not justified by the counts.**

- W6 -> W8: 10 of 15 findings closed, 0 regressed. The 5 carried items are two decisions (F4 chip height, F7 corner class), one value-identical token sweep (F11, chat), dead code (F14) and exception text in `assistant/voice` + unowned `data/**` sinks (F2 residual).
- Fresh list: 12 items. Could be noticed on a phone: **#2** (chips 32 vs 40 across surfaces) and **#3** (one Play-flavor snackbar). Everything else is value-identical, documentation, dead code or test-scope hygiene -- below the bar.
- Every structural seam the audits were created for (snackbar, dialog, sheet, empty/error card, bubble preview, raw buttons, raw springs, raw capsules, exception text in UI, cross-owner pins) is closed and test-pinned.

Recommendation: **one theme-only W9-lite** (one worktree, <= 1 day): theme items 1-5 above, i.e. take the four decisions, delete the dead code, add the two `CopyRules` constants and the KDocs. Chat #4 and assistant #5 are optional and can ride the next functional change in those files. Then stop the cross-surface programme and spend the next effort on the device list below.

### Not established from source
- Everything rendered (no device, no emulator, no screenshot).
- Whether `TtsInitResult.Error.message` (`AssistantViewModel.kt:183`) or `SherpaInstallEvent.Failed.message` is ever composed into a visible string.
- Whether `UpdateManager` message text is rendered by the settings update card; the consumer of `LoopbackResult.Failure`'s second argument (`NativeAuthManager.kt:474`).
- `ExpressiveControlShapes.kt:64,76` spring spec (W4 F3 residual; 9 consumers; still not re-opened).
- Whether `RuntimeParticlePainter.kt:84` (`RuntimeShader`, needs API 33) and `NeuralParticleField.kt:287` (`BlurEffect`, needs API 31; `:159` documents a feather fallback for 26-30) sit behind an explicit `SDK_INT` gate: the repo-wide `Build.VERSION.SDK_INT` grep returned no hit under `ui/theme/particles/`. Verify with `grep -rn "SDK_INT\|RequiresApi\|ChecksSdkIntAtLeast" app/src/main/java/com/materialchat/ui/theme/particles/` before any API-26 claim either way.
- `HomeEmptyState.kt` and `PersonaCard.kt` bodies at this commit (status taken from the W7 home report).
- `ExpressiveSurfaces.kt:338` `CustomShapes.ConversationItem` on the 64dp row component is intentional ("same 24 dp row shape as the home conversation rows") -- not re-checked against `ConversationItem.kt`.

## 5. Device-only questions (deduplicated across W2-W7, grouped by surface, ordered by chance of looking wrong)

### Chat
1. Edit row at font scale 1.3 on 360dp: `Cancel` + `Save & Submit` as two 56dp-min `ExpressiveButton`s inside the 82% user bubble -- ~25dp arithmetic overflow; wrap vs clip not observed (W7 chat).
2. Send/stop glyph at 24dp (was 22) inside the 48dp morphing cookie/square contour (W7 chat).
3. Fusion `Start fusion` as primary `ExpressiveButton` (lost its tertiary tint) inside the sheet at 360dp (W7 chat).
4. Memory panel and image-generation loading card on palette composites over the GEOMETRIC lerp fill (W7 chat).
5. Bubble, composer and snackbar agreement after a failed edit-and-resend (editor reopens with the edited text) (W7 chat).
6. Chat empty/error card on the 28dp squircle beside round bubbles (W6).

### Home / settings / drawer
1. Bubble-style preview at the new radii: ROUNDED 34dp on a 24dp-tall tile renders as a capsule -- is it still distinguishable from COMPACT? (W7 home).
2. `ArenaVotingBar` 56dp `ExpressiveButton`s in the `FlowRow` with 3+ contenders at 360dp (W7 home).
3. Onboarding sign-in `ExpressiveButton` at 72dp and the spinner's new position under the button while signing in (W7 home).
4. `HomeEmptyState` at headlineSmall/bodyMedium under the shape cluster; the FilledTonal action on every palette (W7 home).
5. `PersonaCard` as a static squircle beside pressables that still morph (W7 home) -- also fresh #1.
6. Search error card inside `ScreenContainer` and the retry round-trip (W7 home).
7. Part 6 contrast slots on pressed/ripple picker rows and the tilted Explore clover (W7 home).
8. Play flavor only: ad banner at `listBottomClearance()` (was 104dp) above the toolbar under three-button and gesture navigation (W7 theme).
9. Chip height 32 on settings/bookmarks/personas/arena beside the assistant's 40dp chips (fresh #2).
10. RTL and font-scale runs on every drawer screen (never done).

### Assistant
1. Container-spring (0.6/380) overshoot on the answer and thinking entrances; exit on the collapse token inside the 320ms window (W7 assistant).
2. Pill blur/glow/edge softness on API 26-30 (geometric feather) vs 31+ (`BlurEffect`) and 33+ (`RuntimeShader`) -- first thing to check per the W5/W6 risk list; outward decorations are clipped to node bounds on API 26.
3. Restored photo thumbnails reappearing in the opened pill after a pre-save failure (W7 assistant).
4. Speech actually stopping on hide for the Gemini and sherpa backends (W7 assistant).
5. Click haptic on chips and toolbar actions (needs a vibrator) (W7 assistant).
6. Rotation / locale change mid-answer: IME and keyboard-spacing continuity (W7 assistant).
7. `VoiceChoice` on `shapes.medium` beside the Settings picker; `dialogActionButtonColors()` value-identical (W7 assistant).
8. Carried W2-W6: squircle swap on answer/notice/thinking surfaces, answer-fit caps, resume in keyboard mode, settings launch paths, `StartEllipsis` at API 26, six-line notice cap, resumed pill at rest with a blank draft.
9. Recognition quality / engine selection on a real device (project memory: every recognition change is unverified until exercised on hardware).

### Theme / cross-cutting
1. `ExpressiveButton` glyphs at 20/24 token sizes (value-identical, not rendered) (W7 theme).
2. `M3ExpressiveLoadingIndicator` frames through `Tweens.linear/easeInOut` vs the old raw tweens (W7 theme).
3. `pressMorph()` -> `fast()` press feel (value-identical) (W7 theme).
4. Every `ExpressiveSnackbar` on the 20dp squircle across the nine hosts; dialog entrance on all 10 dialogs (W5/W6).
5. Contrast: numeric gates cover all 39 palette appearances, but no screen has been looked at in pitch-black or dynamic colour on hardware.
