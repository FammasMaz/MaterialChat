# W4 cross-surface coherence audit (read-only)

Repo `bb91963` (v2.41.3). Static reading only: no build, no device, no emulator. Every file:line was taken from grep/sed on this commit; anything derived by offset or not re-opened is marked *unverified*. Inputs: `docs/reports/pass-w{1,2,3}-*.md`, `docs/audit-color-contrast.md`, `ui/theme/{Motion,Shapes,ThemeContrast,ExpressiveControlShapes}.kt`, and the surfaces named below.

Ranking = impact (how visible the seam is) x spread (how many surfaces carry a different answer). Owners: **theme** (`ui/theme`, `ui/components`), **chat** (`ui/screens/chat`, markdown), **home-settings** (home, settings, drawer screens, nav bar), **assistant** (`assistant/**`).

## Ranked findings

### 1. Snackbar / notice family: three shapes, two insets, three screens with no host

| Surface | Component | Shape | Fill / content / action | Outer inset | File:line |
|---|---|---|---|---|---|
| Home, Settings, Personas, OnDeviceModels | `ExpressiveSnackbar` | `SquircleShape(20.dp)` | inverseSurface / inverseOnSurface / inversePrimary | 12dp | `ui/components/ExpressiveSnackbar.kt:26,66,89,92-94`; hosts `ConversationsScreen.kt:272`, `SettingsScreen.kt:448-449`, `OnDeviceModelsScreen.kt:131-132`, `PersonaStudioScreen.kt:125` |
| Chat | raw `Snackbar` | `CustomShapes.Snackbar` = `RoundedCornerShape(24.dp)` (`Shapes.kt:377`) | same roles | none | `ChatScreen.kt:403-410` |
| Arena | raw `Snackbar` | *unverified* | *unverified* | *unverified* | `ArenaScreen.kt:133` |
| Assistant | notice surface | `AssistantShapes.Notice` = `RoundedCornerShape(24/24/18/18)` | notice pair via `AssistantContrast` | `AssistantSpacing.Inset` 12dp | `assistant/ui/AssistantExpressiveStyle.kt:77`; `AssistantOverlay.kt:1570-1680` |
| Home fast-scroll date label | `Surface` | `CustomShapes.Snackbar` (24dp round) | secondaryContainer | - | `ui/components/ExpressiveFastScrollBar.kt:483` (W3 chose the token the real snackbar does not use) |
| Bookmarks, Workflows, Memories | no `SnackbarHost` at all | - | - | - | grep: 0 hosts in `BookmarksScreen.kt`, `WorkflowsScreen.kt`, `MemoriesScreen.kt` |

Mismatch: the token named `Snackbar` (24dp round) is not what the shared snackbar draws (20dp squircle), and chat bypasses the shared component entirely (no 12dp inset, round corners). The assistant notice is the fourth answer (24/18 round).

Proposed: `ExpressiveShapes.Notice = SquircleShape(20.dp)`; `ExpressiveSnackbar` and the assistant notice both clip to it; delete/repoint `CustomShapes.Snackbar`; chat and arena hosts call `ExpressiveSnackbar`; fast-scroll label gets its own token (`ExpressiveShapes.Tile` or `DisclosurePanel`). Owners: theme (token), chat (`ChatScreen.kt:403-410`), home-settings (arena host, fast-scroll label, missing hosts), assistant (`Notice` shape).

### 2. Assistant is the only surface on circular corners; chat composer is round next to squircle cards

| Surface | Container | Shape class | Radius | File:line |
|---|---|---|---|---|
| Chat / home / settings cards, sheets, dialogs, tiles, blocks | `ExpressiveShapes.*` | `SquircleShape` (G2 0.65) | 28 / 32 / 18 / 20 / 16 | `ui/theme/Shapes.kt:94-130` |
| Assistant pill | `AssistantShapes.Pill` | `RoundedCornerShape` | 38 | `AssistantExpressiveStyle.kt:68-71` |
| Assistant answer surface | `AssistantShapes.Answer` | `RoundedCornerShape` | 36 top / 28 bottom | `:74` |
| Assistant notice | `AssistantShapes.Notice` | `RoundedCornerShape` | 24 / 18 | `:77` |
| Assistant thinking surface | `AssistantShapes.Thinking` | `RoundedCornerShape` | 20 / 8 | `:86` |
| Assistant connected toolbar | `ConnectedOuter/Inner/Pressed` | `RoundedCornerShape` | 20 / 8 / 12 | `:92-101` |
| Chat composer | `CustomShapes.MessageInputContainer` | `RoundedCornerShape` | 28 | `Shapes.kt:342`; `MessageInput.kt` (consumer, line *unverified*); also `ArenaPromptInput.kt:60` |
| Chat bubbles | `MessageBubbleShapes.*` | `RoundedCornerShape` | 28 / 8 | `Shapes.kt:214-241` (deliberate per W3) |
| In-bubble generated-image card / loading card | `ExpressiveShapes.Card` / `Tile` | squircle | 28 / 18 | `MessageBubble.kt:1503,1631,1645` |

Mismatch: the ChatScreen -> MessageInput -> MessageBubble seam stacks a 28dp round composer under 28dp round bubbles that contain 28dp squircle cards; the assistant answer surface is a card by every other definition but is the only card in the app with circular corners. The pill (38dp capsule) is a geometry contract and is fine; the answer/notice/thinking surfaces are not.

Proposed: `AssistantShapes.Answer/Notice/Thinking` become per-corner squircles (theme adds a `SquircleShape` with per-corner radii or the assistant uses `ExpressiveShapes.Card`/`Notice`); `MessageInputContainer` stays round only if the bubble family stays round (document the decision in `Shapes.kt`). Owners: theme (per-corner squircle), assistant (`:74,:77,:86`), chat (composer decision).

### 3. Press-morph and disclosure physics: three press specs, two disclosure specs

| Control class | Surface | Spec | File:line |
|---|---|---|---|
| Press shape/scale/tint | chat, home, settings (`ExpressiveMotion.Press.*`) | `Spatial.pressMorph()` / `Effects.pressTint()` | `ui/theme/Motion.kt:362-368`; consumer e.g. `MessageBubble.kt:1348,1353` |
| Press morph | assistant (`AssistantMotion.morph()`) | `Spatial.fast()` | `AssistantExpressiveStyle.kt:54` |
| Press morph | expressive control shapes (all surfaces via `expressiveControlShape`) | `Spatial.shapeMorph()` | `ui/theme/ExpressiveControlShapes.kt:61,73,83` |
| Disclosure expand/shrink | chat, home (`Disclosure.Expand/Shrink/FadeIn/FadeOut`) | `Spatial.expand()/collapse()` + `Effects.reveal()` | `Motion.kt:374-379`; `MessageBubble.kt:1128-1131,1147-1150,1286-1294`; 9 sites outside chat |
| Disclosure expand/shrink | assistant (`surfaceReveal()`) | `Spatial.default()` | `AssistantExpressiveStyle.kt:37`; `AssistantOverlay.kt:789-795,1419-1421,1450` |
| Dialog entrance | all | `dialogEntrance()` | consumers `MemoriesScreen.kt:132`, `AssistantVoiceSettingsPanel.kt:87`; missing on `AssistantVoiceSettingsPanel.kt:77` |

Spring values of `pressMorph`, `fast`, `shapeMorph`, `default`, `expand`, `collapse` live in `Motion.kt` above line 250 and were not re-opened here: *unverified* whether any pair is value-identical. W2/W3 theme reports already flagged `shapeMorph` vs `Press.Shape` and left it.

Proposed: `AssistantMotion.morph()` -> `ExpressiveMotion.Press.Shape`; `AssistantMotion.surfaceReveal()` for `expandVertically/shrinkVertically` -> `Disclosure.Expand/Shrink`; `expressiveControlShape` -> `Press.Shape`/`Press.Progress`. Owners: theme (control shapes), assistant.

### 4. Chip class: four heights, four icon sizes, three shapes

| Chip | Height | Icon | Shape | H-inset | Type | File:line |
|---|---|---|---|---|---|---|
| Assistant labelled/screen/continue chip | 40 | 16 | `RoundedCornerShape(50)` | 14 | `AssistantTypography.action` | `AssistantExpressiveStyle.kt:80,159,178,181`; `AssistantOverlay.kt:1493-1510,1524-1549,1704-1717` |
| Chat memory-reference chip | 48 | 18 | 20 -> 16 (open) -> 14 (pressed) round | *unverified* | *unverified* | `MessageBubble.kt:1340-1363,1387,1398` |
| Chat compact model badge | min 40x28 | 18 | `CustomShapes.Pill` (W3) | - | - | `BeautifulModelBadges.kt:374,411,672,709` |
| Chat sources tile / fusion badge | min 48 / - | 18 / 16 | `CustomShapes.Pill` | - | - | `WebSearchSourcesCarousel.kt:123,137`; `FusionBadge.kt:33`, `FusionSourcesSection.kt:76` |
| M3 `FilterChip`/`AssistChip`/`SuggestionChip` (settings, insights, bookmarks, personas, arena) | 32 (M3 default) | 18 (M3 default) | `MaterialTheme.shapes.small` = 12dp (`Shapes.kt:192`), except `ConversationStarterChips.kt:54` `CustomShapes.Pill` | M3 default | labelLarge | `SettingsScreen.kt:2563,2664`, `ModelAssignmentsScreen.kt:667,696`, `InteractionSettingsScreen.kt:208,236`, `InsightsScreen.kt:148`, `BookmarkFilterBar.kt:80,134`, `BookmarkCard.kt:153`, `AddBookmarkSheet.kt:120,161`, `PersonaEditorSheet.kt:169`, `PersonaCard.kt:184`, `ArenaModelPicker.kt:75,134` |
| Explore feature tile (chip-like) | min 144/156 | 26/22 in `Tile` | `ExpressiveShapes.Card` | 18/16 | - | `ExploreScreen.kt:406,411,420,429,436` |

Mismatch: the same "pill with a leading glyph" is 28, 32, 40 or 48dp tall depending on who built it, and the only M3 chips in the app sit on 12dp corners beside 50% capsules.

Proposed: `ExpressiveSizes.Chip { Height = 40, Icon = 18, Inset = 14 }` in `ui/theme`, `ExpressiveShapes.Chip = CustomShapes.Pill`; `ChipDefaults.shape` override in the theme so M3 chips pick the capsule; chat memory chip drops to 40 or is documented as a row (48) not a chip; `AssistantSizes.ChipHeight/ChipIcon` alias the theme values. Owners: theme (token + chip defaults), chat, assistant, home-settings (M3 chip sites).

### 5. Contrast resolvers: chat depends on the assistant package, three slot vocabularies, pairs gated on one surface only

| Resolver | Delegates to | Slot naming | File:line |
|---|---|---|---|
| `ThemeContrast` | - | `ThemeContrastPair(name)` strings | `ui/theme/ThemeContrast.kt:18-117,184-226` |
| `HomeSettingsContrast` / `HomeSettingsSlot` | `ThemeContrast` | enum PascalCase (`SnackbarBody`, `DialogBody`, ...) | `ui/components/HomeSettingsContrast.kt:24-152` |
| `AssistantContrast` / `AssistantSurfacePalette` | `ThemeContrast` | lowercase strings (`"error notice"`) | `assistant/ui/AssistantContrast.kt:35-60,184-233` |
| `ChatContrast` / `ChatSurfacePalette` | **`AssistantContrast`** (not `ThemeContrast`) | camelCase vals (`screenError`, `fusionPanel`) | `ui/screens/chat/components/ChatContrast.kt:43-46` and the `BODY_TEXT = AssistantContrast.BODY_TEXT` constants above them |

Pairs gated on only one surface (the same fill/content is painted elsewhere ungated):
- Snackbar body/action (`inverseSurface`/`inverseOnSurface`/`inversePrimary`): gated `HomeSettingsSlot:80-81`; chat paints the same roles raw at `ChatScreen.kt:407-409` with no chat slot (grep of `ChatContrast.kt` for `Snackbar`: 0).
- Dialog body/label/destructive: `HomeSettingsSlot:77-79`; chat only gates the image-viewer scrim (`ChatContrast.kt:352-356`); `MemoriesScreen.kt:130`, `AssistantVoiceSettingsPanel.kt:77,85` dialogs gated nowhere.
- Full-screen error content: chat `screenError/screenErrorMuted` (`ChatContrast.kt:205-207,301-302`); home/bookmarks/personas/workflows `ErrorContent` draw `error`/`onSurfaceVariant` by role (`ConversationsScreen.kt:1317`, `BookmarksScreen.kt:170`, `PersonaStudioScreen.kt:256`, `WorkflowExecutionScreen.kt:280`) and `HomeSettingsSlot` has no `Error*` slot.
- Disabled content: `ExpressiveSurfaces.kt:192-193` `disabledContentColor = contentColor.copy(alpha = 0.38f)` ungated; chat gates `reasoning disabled` at 3:1 (`ChatContrast.kt:303`).

Proposed: `ChatContrast` imports `ThemeContrast` only; one `ContrastSlot` interface in `ui/theme` that the three palettes implement so `ThemeContrastTest` can enumerate every slot in the app; add snackbar, dialog and error slots to whichever palette owns the screen. Owners: theme (interface), chat (import flip + snackbar slot), home-settings (error/dialog slots).

### 6. Copy: ellipsis, period, exception text

ASCII `...` in user-visible strings after W3 (chat 0, assistant 0): `SettingsScreen.kt:1495,1498,1553,1556,3104,3109`; `AddProviderSheet.kt:359,547` (`:331` `"sk-..."` is a key prefix sample, leave); `SettingsViewModel.kt:447,576`; `OnboardingViewModel.kt:50`; `ConversationsViewModel.kt:561`; `ArenaScreen.kt:165`; `UpdateBanner.kt:228`; `StepEditor.kt:113`; `WorkflowExecutionScreen.kt:198`; `CanvasRefinementInput.kt:124`; `SearchBar.kt:74`; `BookmarkSearchBar.kt:41`; `MindMapCanvas.kt:512` (canvas text); `NativeAuthManager.kt:185,481` (auth status shown in the sheet). `AssistantVoiceSettingsPanel.kt:80` already uses `\u2026`.

Snackbar sentence form: with period `SettingsViewModel.kt:666,681`; without `SettingsViewModel.kt:131`, `OnDeviceModelsViewModel.kt:67,75,102,112`; assistant notices always end with a period (`AssistantCopy.kt:12-16`).

Exception text reaches the user: `OnDeviceModelsViewModel.kt:56,93,103,113` and `SettingsViewModel.kt:606` show `it.message ?: ...`; assistant forbids this (`AssistantCopy.requestFailure`, W3 report). Chat errored-bubble text: *unverified*.

Action verb: "Retry" (`ChatScreen.kt:600`, `ConversationsScreen.kt:1317`, `BookmarksScreen.kt:170`, `SettingsScreen.kt:2860`, `MessageBubble.kt:1720`) vs "Try again" (`AssistantCopy.kt:27`, `AssistantNotices.kt`, `AssistantViewModel.kt`). Title case left: only proper nouns (W3 list) and `OnDeviceModelsScreen.kt:296,347`, which are sentence case with product names; no action needed.

Proposed: one `Copy` object in `ui/components` (or promote `AssistantCopy` rules): `ELLIPSIS = '\u2026'`, phase lines = participle + ellipsis, notices = sentence(s) with period, action = "Try again", never exception text. Owner: home-settings (all sites above), chat ("Retry" -> "Try again" or the reverse, decided once), data/util for `NativeAuthManager`.

### 7. Full-screen error state: five layouts of the same two buttons

| Screen | Buttons (in order) | Style | File:line |
|---|---|---|---|
| Chat load error | Go back, Retry | `ExpressiveButton` Text | `ChatScreen.kt:599-600` |
| Bookmarks | Retry, Go back | Text | `BookmarksScreen.kt:170-171` |
| Home | Retry | Text | `ConversationsScreen.kt:1317` |
| Personas | Go back | Text | `PersonaStudioScreen.kt:256` |
| Workflow execution | Go back | Text | `WorkflowExecutionScreen.kt:280` |
| Settings (update) | Retry | *unverified style* | `SettingsScreen.kt:2860` |
| Assistant | Try again (notice chip) | 40dp capsule | `AssistantOverlay.kt:1570-1680`, `AssistantCopy.kt:27` |

Proposed: `ui/components/ErrorContent(title, body, onRetry?, onBack?)` with fixed order (primary action first), on the chat `screenError` slot pattern. Owners: theme (component), chat, home-settings.

### 8. Dialogs: two shapes, one missing entrance

`ExpressiveShapes.Dialog` (28dp squircle): `SettingsScreen.kt:2694,2745,2795`, `OnDeviceModelsScreen.kt:186`, `WorkflowExecutionScreen.kt:175`, `ConversationsScreen.kt:337,869`. Default shape (= `MaterialChatShapes.extraLarge` 32dp round, `Shapes.kt:202`): `MemoriesScreen.kt:130` (has `dialogEntrance()` `:132`), `AssistantVoiceSettingsPanel.kt:77` (no shape, no entrance) and `:85` (no shape, entrance at `:87`). `ImageViewerDialog.kt:63` is a bare `Dialog` (full-screen viewer, exempt).

Proposed: `shape = ExpressiveShapes.Dialog` + `dialogEntrance()` on all three. Owners: home-settings (`MemoriesScreen`), assistant (`AssistantVoiceSettingsPanel`).

### 9. Sheets: three sheets still on private headers, one on a different title style

All seven `ModalBottomSheet`s share `ExpressiveShapes.BottomSheet` + `surfaceContainerLow` (`AddProviderSheet.kt:116-120`, `RedoModelPickerSheet.kt:71-77`, `ExportBottomSheet.kt:70,79`, `FusionModelSelector.kt:79-85`, `PersonaPickerSheet.kt:62-66`, `PersonaEditorSheet.kt:71-75`, `AddBookmarkSheet.kt:74-79`). Headers:

| Sheet | Header | Title style | Close | Gutter | File:line |
|---|---|---|---|---|---|
| Chat x3, AddProvider | `ui/components/SheetHeader` | headlineSmall | shared | `SheetGutter` 24 | `SheetHeader.kt:22,32`; consumers `RedoModelPickerSheet.kt:83,89`, `ExportBottomSheet.kt:105,111`, `FusionModelSelector.kt:91,97`, `AddProviderSheet.kt:174,182` |
| PersonaPicker | inline `Text` | headlineSmall | none | 24 literal + vertical 8 | `PersonaPickerSheet.kt:77-79` |
| PersonaEditor | inline | headlineSmall | *unverified* | 24 literal | `PersonaEditorSheet.kt:81,88` |
| AddBookmark | inline | **titleLarge** | own `Icons.Default.Close` | 24 literal | `AddBookmarkSheet.kt:84,90,172` |

Proposed: the three drawer sheets call `SheetHeader(title, onClose)` and `SheetGutter`. Owner: home-settings.

### 10. Motion still outside the token set; one ungated infinite loop

- `ui/screens/chat/components/StreamingIndicator.kt:143-160` `M3StreamingDots`: `LaunchedEffect(Unit) { while (true) ... }` with no `rememberParticleMotionGate()`; the gate at `:467-468` belongs to the tween helper below it, not to this loop. Consumer `:262`. Whether that consumer is reachable from a live screen: *unverified*. Compare `ExpressiveLoadingIndicator.kt:355-364`, which gates the identical dot loop.
- `MainActivity.kt:322,329` (ad banner slide, 500/0.8 and 500/1.0) and `:349,356` (floating nav bar slide, 500/0.7 and 500/1.0): raw springs, no owner in the W1-W3 split (`MainActivity.kt` and `ui/navigation/**` are unlisted).
- Raw tweens: `ChatTopBar.kt:456` (1400 linear, gated), `FusionAnimation.kt:56` (3000 linear, gated via the consumer list), `StreamingIndicator.kt:474` (parametrised helper, gated at `:467`).
- Every other `rememberInfiniteTransition` (`ScrollToBottomFab.kt:115`, `StepProgressIndicator.kt:66`, `ExploreScreen.kt:228`, `ConversationsScreen.kt:512`, `HomeEmptyState.kt:162`, `MindMapCanvas.kt:129`, `ExpressiveLoadingIndicator.kt:101,294,410`, `FusionProgressView.kt:192`) is in a file that imports the gate; per-site gating of each was taken from the W2/W3 reports, not re-read here.

Proposed: `Durations.HeaderPulse = 1400`, `Durations.FusionOrbit = 3000`; `M3StreamingDots` on the gate; nav bar + ad banner on `Spatial.container()` / `Effects.slow()` and an explicit owner for `MainActivity.kt`/`ui/navigation`. Owners: chat (dots, tweens), theme (tokens, nav bar).

### 11. Empty states: three families

| Surface | Container | Headline | Body | Padding | Action | File:line |
|---|---|---|---|---|---|---|
| Chat | `ExpressiveShapes.Card` squircle 28 + `Tile` glyph | headlineSmall Bold | bodyMedium | 24 | none | `ChatEmptyState.kt:94,101,105,123,129` |
| Home | no card, shape cluster | headlineMedium | bodyLarge | 32 | `Button` | `HomeEmptyState.kt:85,95,105,113` |
| Bookmarks, Memories, Workflows, GeneratedImages | `ExpressiveEmptyState` on `CustomShapes.ConversationItem` (24dp **round**) | *unverified* | *unverified* | *unverified* | *unverified* | `ExpressiveSurfaces.kt:276` (shape line ~306, offset-derived); consumers `BookmarksScreen.kt:260`, `MemoriesScreen.kt:322,332`, `WorkflowsScreen.kt:157`, `GeneratedImagesScreen.kt:269` |

Proposed: `ExpressiveEmptyState` moves to `ExpressiveShapes.Card`, headlineSmall Bold + bodyMedium + 24dp like chat; home keeps its hero but adopts the same type pair. Owners: theme (`ExpressiveSurfaces.kt`), home-settings.

### 12. Top bars: four families

`LargeTopAppBar` + `exitUntilCollapsed`: `ConversationsScreen.kt:228`, `SettingsScreen.kt:425,431`. Plain `TopAppBar`: 13 drawer/sub-settings screens (`BookmarksScreen.kt:72`, `MemoriesScreen.kt:83`, `WorkflowsScreen.kt:80`, `ExploreScreen.kt:155`, `PersonaStudioScreen.kt:96`, `ArenaScreen.kt:104`, `ModelAssignmentsScreen.kt:89`, `InteractionSettingsScreen.kt:57`, `OnDeviceModelsScreen.kt:103`, ...). `CenterAlignedTopAppBar`: `InsightsTopBar.kt:29`, `MindMapTopBar.kt:29`. Custom masthead with animated `containerColor`: `ChatTopBar.kt:175-202` (`pinnedScrollBehavior`, `ChatScreen.kt:335`). Scaffold `containerColor = surfaceContainer` on all 15 screens checked (consistent). Settings/home collapsed-bar seam is byte-identical (W3 home report, `SettingsScreen.kt:425-442,712,718` vs `ConversationsScreen.kt:233,246,759-760`); the drawer screens reach the same `ScreenContainer` contour from a 64dp bar (`ExploreScreen.kt:172-173`), so the only drift is bar height/alignment, not colour.

Proposed: decide Large for top-level destinations and small for everything pushed; the two center-aligned bars join the small family. Owner: home-settings.

### 13. Size ladder for the same control class

| Class | Values seen | File:line |
|---|---|---|
| Touch target | 48 (`ExpressiveButton.kt:344`, `ComposerSendButton.kt:179`, `MessageInput.kt:465,1000`, `ContextWindowIndicator.kt:63`, `ProviderCard.kt:293`, `AssistantSizes.Target`), 44 (`ConversationsScreen.kt:580`, `HomeEmptyState.kt:221`, `SettingsExpressive.kt:70`), 40 (`AssistantSizes.Control`, `SettingsExpressive.kt:227`, `SystemPromptField.kt:121`), 32 (`ExpandableConversationGroup.kt:322`, `AssistantSizes.CompactTarget`), 56 (`BranchConversationItem.kt:112,123`, `AssistantSizes.PrimaryButton`) | as listed |
| Leading tile | 48/26 (`ConversationItem.kt:147,162`, `ExpandableConversationGroup.kt:205,220`, `ProviderCard.kt:293,310`), 44/22 (`SettingsExpressive.kt:70,79`), 40/22 (`SettingsExpressive.kt:227,236`), 40/32 (assistant glyph container), `Tile` 26/22 (`ExploreScreen.kt:436`) | as listed |
| Icon in control | 16, 18, 20, 22, 24 | `AssistantSizes.ChipIcon` 16; 18 `MessageBubble.kt:1387,1614,1701`, `ProviderCard.kt:335`; 20 `ExpressiveButton.kt:187,203`, `MessageInput.kt:644`; 22 `ComposerSendButton.kt:216`, `ScrollToBottomFab.kt:172`, `ConversationItem.kt:272`; 24 `UpdateBanner.kt:141` |
| Loading indicator | 16 (`SettingsScreen.kt:2421`), 18 (`:2313`), 20 (`ModelAssignmentsScreen.kt:437`, `ModelPickerDropdown.kt:260`, `BeautifulModelBadges.kt:469,739`), 24 (`ExportBottomSheet.kt:130`, `RedoModelPickerSheet.kt:106`), 48 (`ChatScreen.kt:551`, `ConversationsScreen.kt:1016,1405`, `ChatWorkingIndicator.kt:72`) | as listed |
| Screen gutter | chat 8/10/12/14, home 6/16, settings 16, bookmarks 16, workflows 16/20, sheets 24, assistant 12/18/20 (`AssistantSpacing.Inset/PillInset/SurfaceInset`), empty states 24/32, snackbar 12 | counts in grep of `horizontal = N` per dir |

Proposed: `ExpressiveSizes { Target 48, Control 40, Compact 32, Tile 48, TileGlyph 26, Icon 20, IconSmall 18, IndicatorInline 20, IndicatorHero 48 }` and `ExpressiveSpacing { Gutter 16, SheetGutter 24, Inset 12 }` in `ui/theme`; `AssistantSizes`/`AssistantSpacing` alias them where the value already matches. Owner: theme first, then each surface.

### 14. FAB tokens

`CustomShapes.Fab/ExtendedFab/FabPressed` (`Shapes.kt:286-297`) have one consumer, and it is a 40dp inline button (`SystemPromptField.kt:121-122`). The real FABs are M3 defaults: `MaterialChatNavBar.kt:147` `VibrantFloatingActionButton`, `ScrollToBottomFab.kt:160` `SmallExtendedFloatingActionButton`. Proposed: delete the three tokens or point the two FABs at them. Owner: theme.

### 15. Remaining `.copy(alpha =` on text/icon colours

`ui/navigation/MaterialChatNavBar.kt:233` (`onPrimaryContainer @0.72` unselected icon tint); `ui/screens/canvas/components/CanvasRefinementInput.kt:77` (`onSurfaceVariant @0.38` send icon); `ui/components/ExpressiveSurfaces.kt:193` (`disabledContentColor @0.38`, M3 convention, decide once); `MessageBubble.kt:714` (`primary @swipeProgress`, progress-driven reveal, arguably exempt). Everything else in the grep is a fill, track, hairline or wash. Hard-coded `Color.*`: file-level grep hit `AssistantContrast.kt`, `HomeSettingsContrast.kt`, `ChatTopBar.kt`, `FusionSourcesSection.kt`, `ModelPickerDropdown.kt`, `BeautifulModelBadges.kt`, but the line-level grep returned nothing: *unverified*; most likely `Color.White/Black` fallbacks inside the resolvers and `Color.Transparent`. Owner: home-settings (nav bar, canvas), theme (`ExpressiveSurfaces`).

### 16. Seam notes (no new drift beyond the findings above)

- Chat: composer shape (finding 2); composer and assistant pill both on `surfaceContainerHigh` (`AssistantContrast.kt:306`; chat W3 Part 5) - consistent.
- Assistant pill -> answer -> notice: radii 38 / 36-28 / 24-18 / 20-8 (finding 2); spacing on `AssistantSpacing` is internally coherent.
- Home list -> toolbar -> snackbar: `HomeListInsets.kt:53,60` own the clearances, but `MainActivity.kt:320` hard-codes the ad banner's 104dp over the bar and the nav bar's slide is raw (finding 10). *Unverified* that 104 equals `listBottomClearance()`.
- Settings collapsed app bar: identical to home (finding 12).

## Per-owner work list (paste into W5 briefs)

### theme
1. `ExpressiveShapes.Notice = SquircleShape(20.dp)`; make `ExpressiveSnackbar` use it; deprecate `CustomShapes.Snackbar` (`Shapes.kt:377`). [F1]
2. Per-corner `SquircleShape` (or `ExpressiveShapes.AnswerSurface`) so the assistant can leave `RoundedCornerShape` (`AssistantExpressiveStyle.kt:74,77,86`). Document in `Shapes.kt` whether bubbles + composer stay round. [F2]
3. `expressiveControlShape` -> `Press.Shape`/`Press.Progress` (`ExpressiveControlShapes.kt:61,73,83`); state the spring values of `pressMorph/fast/shapeMorph/default/expand/collapse` in the report. [F3]
4. `ExpressiveSizes` (Chip height 40 / icon 18 / inset 14; Target 48; Control 40; Compact 32; Tile 48/26; IndicatorInline 20; IndicatorHero 48) and `ExpressiveSpacing` (Gutter 16, SheetGutter 24, Inset 12); theme-level `ChipDefaults` shape = capsule. [F4, F13]
5. `ContrastSlot` interface in `ui/theme`; `ThemeContrastTest` enumerates home + chat + assistant slots through it. [F5]
6. `ui/components/ErrorContent` with fixed button order and `screenError` slot. [F7]
7. `ExpressiveEmptyState` on `ExpressiveShapes.Card`, headlineSmall Bold + bodyMedium, 24dp (`ExpressiveSurfaces.kt:276`). [F11]
8. `Durations.HeaderPulse = 1400`, `Durations.FusionOrbit = 3000`; claim `MainActivity.kt:320-356` + `ui/navigation/**` (nav bar/ad banner springs -> tokens; `MaterialChatNavBar.kt:233` tint off alpha). [F10, F15]
9. Delete or consume `CustomShapes.Fab/ExtendedFab/FabPressed` (`Shapes.kt:286-297`). [F14]
10. Decide `ExpressiveSurfaces.kt:193` disabled-content alpha once. [F15]

### chat
1. `ChatScreen.kt:403-410` -> `ExpressiveSnackbar`; add a snackbar slot to `ChatSurfacePalette` or rely on the shared slot. [F1, F5]
2. `ChatContrast.kt:43-46` + constants: delegate to `ThemeContrast`, drop the `assistant/ui` import. [F5]
3. `StreamingIndicator.kt:143-160` `M3StreamingDots` behind `rememberParticleMotionGate()` (pattern: `ExpressiveLoadingIndicator.kt:355-364`); confirm whether `:262` is live. [F10]
4. `ChatTopBar.kt:456`, `FusionAnimation.kt:56` onto the new duration tokens. [F10]
5. Memory chip `MessageBubble.kt:1340-1363`: 48 -> `ExpressiveSizes.Chip.Height` or document it as a row. [F4]
6. `ChatScreen.kt:599-600` on shared `ErrorContent`; "Retry" vs "Try again" resolved with assistant. [F6, F7]
7. Composer shape decision recorded (round 28 vs squircle 28 next to in-bubble cards). [F2]

### home-settings
1. `PersonaPickerSheet.kt:77-79`, `PersonaEditorSheet.kt:81,88`, `AddBookmarkSheet.kt:84,90,172` -> `SheetHeader` + `SheetGutter`. [F9]
2. `MemoriesScreen.kt:130` -> `ExpressiveShapes.Dialog`. [F8]
3. `ArenaScreen.kt:133` -> `ExpressiveSnackbar`; add hosts to Bookmarks/Workflows/Memories or route their errors somewhere visible; `ExpressiveFastScrollBar.kt:483` off `CustomShapes.Snackbar`. [F1]
4. Ellipsis sites: `SettingsScreen.kt:1495,1498,1553,1556,3104,3109`, `AddProviderSheet.kt:359,547`, `SettingsViewModel.kt:447,576`, `OnboardingViewModel.kt:50`, `ConversationsViewModel.kt:561`, `ArenaScreen.kt:165`, `UpdateBanner.kt:228`, `StepEditor.kt:113`, `WorkflowExecutionScreen.kt:198`, `CanvasRefinementInput.kt:124`, `SearchBar.kt:74`, `BookmarkSearchBar.kt:41`, `MindMapCanvas.kt:512`. Snackbar period rule: `SettingsViewModel.kt:131,666,681`, `OnDeviceModelsViewModel.kt:67,75,102,112`. Exception text: `OnDeviceModelsViewModel.kt:56,93,103,113`, `SettingsViewModel.kt:606`. [F6]
5. `ErrorContent` adoption: `ConversationsScreen.kt:1317`, `BookmarksScreen.kt:170-171`, `PersonaStudioScreen.kt:256`, `WorkflowExecutionScreen.kt:280`, `SettingsScreen.kt:2860`; add `Error*` slots to `HomeSettingsSlot`. [F5, F7]
6. M3 chip sites (list in F4) onto the theme chip defaults; `ConversationStarterChips.kt:54` already there. [F4]
7. `CanvasRefinementInput.kt:77` tint off alpha. [F15]
8. Top-bar family decision for the 13 small-bar screens and the two center-aligned ones. [F12]
9. 44dp targets (`ConversationsScreen.kt:580`, `HomeEmptyState.kt:221`, `SettingsExpressive.kt:70`) -> `ExpressiveSizes`. [F13]

### assistant
1. `AssistantShapes.Answer/Notice/Thinking` (`AssistantExpressiveStyle.kt:74,77,86`) onto the squircle family once theme ships it. [F2]
2. `AssistantMotion.morph()` -> `Press.Shape`; `surfaceReveal()` for expand/shrink -> `Disclosure.Expand/Shrink` (`AssistantExpressiveStyle.kt:37,54`; `AssistantOverlay.kt:789-795,1419-1421,1450`). [F3]
3. `AssistantSizes.ChipHeight/ChipIcon`, `AssistantSpacing.ChipInset` alias `ExpressiveSizes`. [F4]
4. `AssistantVoiceSettingsPanel.kt:77,85`: `ExpressiveShapes.Dialog`; `dialogEntrance()` on `:77`. [F8]
5. Agree "Try again" vs "Retry" with chat and pin it in `AssistantCopy`. [F6]

### unowned (assign in W5)
- `MainActivity.kt`, `ui/navigation/**`, `ui/screens/canvas/**`, `data/auth/NativeAuthManager.kt:185,481` (user-visible auth status copy), `util/AttachmentLimits` (carried from W3).

## Not established from source
- Spring values behind finding 3 (Motion.kt < line 250 not re-opened).
- Reachability of `M3StreamingDots` (`StreamingIndicator.kt:262`).
- Line-level `Color.*` literals (file-level grep only).
- `ExpressiveEmptyState` type styles and the exact shape line; arena snackbar styling; `PersonaEditorSheet` close control; settings update-error button style; `MainActivity.kt:320` 104dp vs `listBottomClearance()`.
- Everything rendered: no device, no emulator, no screenshots.
