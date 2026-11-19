# W5 theme pass report (branch `pass/w5-theme`, base `ec82b40` v2.41.4)

Ownership in this pass: `app/src/main/java/com/materialchat/ui/theme/**` and its tests, plus `ui/components/ExpressiveSurfaces.kt`, `ui/components/ExpressiveSnackbar.kt`, the new `ui/components/ErrorContent.kt`, and (alias only) `ui/components/SheetHeader.kt` for the `SheetGutter` re-point the brief asked for. `MainActivity.kt:320-356` and `ui/navigation/**` were claimed read-only for part 6. No emulator, AVD or device. Nothing pushed.

## Commits

| Hash | Parts | Summary |
|---|---|---|
| `6300c74` | 1, 2, 3 | Per-corner `SquircleShape`; `ExpressiveShapes.Notice/AnswerSurface/NoticeSurface/ThinkingSurface/TileBadge/Chip`; `CustomShapes.Snackbar/ExtendedFab/FabPressed` removed, `Fab` deprecated alias; `ExpressiveSizes` + `ExpressiveSpacing`; `ExpressiveSnackbar` on `Notice` + `Inset`; `SheetGutter` aliases the token |
| `f43c101` | 4, 6 | `ContrastSlot` / `ContrastSlotSet` / `ContrastSlotResolver`; `ThemeContrastPair` implements it; `componentContrastPairs()` (snackbar, dialog, loading row, error content); `dialogActionColor()` / `dialogDestructiveColor()`; `Durations.FusionOrbit = 3000`; `ThemeContrastTest` enumerates every registered surface |
| `538b61d` | 5 | `ui/components/ErrorContent.kt` (+ `ErrorContentCopy`, `ExpressiveStateCard`); `ExpressiveEmptyState` on `Card` + headlineSmall Bold / bodyMedium / 24dp; disabled-content alpha decided and documented |
| (this commit) | 7 | REPORT.md |

## Gates (at `538b61d`)

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 -q :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0 before each commit.
- `:app:testGithubDebugUnitTest` -> exit 0; `app/build/test-results/testGithubDebugUnitTest/*.xml`: **729 tests, 0 failures, 0 errors, 0 skipped** (722 after parts 1-3, 726 after 4+6, 729 after 5). 21 unit tests added, 0 androidTests added (so `scripts/run-connected-tests.sh` and the judge minimum are untouched).
- One transient compile failure on the first part 1-3 run left no diagnostic and did not reproduce on the immediate re-run (identical sources); recorded, not explained.
- No test, testTag, contentDescription, script or workflow weakened or renamed. No `FontStyle.Italic`. `SmoothStreamingText.kt` untouched.

## What changed (file:line)

### Part 1 - notice shape, dead tokens
- `ui/theme/Shapes.kt:186` `ExpressiveShapes.Notice = SquircleShape(20.dp)`.
- `ui/components/ExpressiveSnackbar.kt:88,91`: `padding(ExpressiveSpacing.Inset)`, `shape = ExpressiveShapes.Notice` (the private `SnackbarShape` is gone; geometry unchanged).
- `Shapes.kt:500`: `CustomShapes.Snackbar` deleted (grep of `app/src`: zero consumers). `ExtendedFab` and `FabPressed` deleted (zero consumers). `CustomShapes.Fab` (`:423`) has one consumer (`SystemPromptField.kt:122`, a 40dp inline button, not a FAB) so it is a `@Deprecated` getter aliasing `MaterialChatShapes.medium` (value-identical 16dp round) with `ReplaceWith(ExpressiveShapes.Tile)`.
- `ShapeTokensTest.kt:184,204` pin `Notice` and the `Fab` alias.

### Part 2 - per-corner squircle, assistant surface tokens, tile badge
- `Shapes.kt:22` `const val SquircleSmoothing = 0.65f` (the one G2 value; `TopSquircleShape` reads it too).
- `Shapes.kt:41-116` `SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)` primary constructor, `RoundedCornerShape` positional order; secondary one-radius constructor (`:49`); 0dp corner left sharp (`CornerRounding.Unrounded`); start/end corners mirror under RTL like `RoundedCornerShape`; symmetric path keeps the old single-`rounding` call so existing tokens produce byte-identical polygons; `equals`/`hashCode`/`toString` over the five fields; `internal fun roundedPolygon()` exposed so the JVM tests compare `cubics` without `android.graphics.Path`.
- `Shapes.kt:193,196,203`: `AnswerSurface` (36/36/28/28), `NoticeSurface` (24/24/18/18), `ThinkingSurface` (20/20/8/20 in assistant order = `bottomEnd 20, bottomStart 8`), radii identical to `AssistantExpressiveStyle.kt:74,77,86`.
- `Shapes.kt:175` `ExpressiveShapes.TileBadge = SquircleShape(12.dp)` for the 32dp step badge (`StepEditor.kt:65`, today 14dp round). Justification in KDoc: keeps the `Tile` proportion (18/48 = 0.375; 32 x 0.375 = 12), is the M3 shape-scale medium step (= `MaterialChatShapes.small`), one step above `Badge` (8), and at 12dp on 32dp the G2 ease is still visible (it is not at 8dp, which is why `Badge` stays round).
- `ShapeTokensTest.kt:114-200`: four-equal-radii per-corner == symmetric (equals, hashCode, identical `cubics` under LTR and RTL at two densities); asymmetric != symmetric and draws different corner cubics; start corners follow layout direction (LTR vs RTL outlines differ for an asymmetric shape, equal for a symmetric one); degenerate sizes outline as rectangles; the three assistant tokens pin their exact radii; `TileBadge` proportion.

### Part 3 - sizes, spacing, chip shape
- `ui/theme/ExpressiveDimensions.kt` (new): `ExpressiveSizes.Chip { Height 40, Icon 18, Inset 14 }`, `Target 48`, `CompactTarget 44`, `Control 40`, `Compact 32`, `Tile 48`, `TileIcon 26`, `IndicatorInline 20`, `IndicatorHero 48`; `ExpressiveSpacing { Gutter 16, SheetGutter 24, Inset 12 }`.
- `ui/components/SheetHeader.kt:25` `val SheetGutter: Dp = ExpressiveSpacing.SheetGutter` (seven consumers keep compiling; new sites read the token).
- `ExpressiveDimensionsTest.kt` (5 tests) pins every value and the alias.
- `Shapes.kt:213` `ExpressiveShapes.Chip: Shape get() = CustomShapes.Pill` (capsule). **Not** applied as a theme-level `ChipDefaults` override: in material3 1.5.0-alpha15 `AssistChipDefaults/FilterChipDefaults/SuggestionChipDefaults.shape` read `ShapeKeyTokens.CornerSmall` = `MaterialTheme.shapes.small`, and the same key shapes `ButtonSmallTokens`, `ButtonXSmallTokens`, `InputChipTokens`, `ListTokens`, `SegmentedMenuTokens`, `SmallIconButtonTokens`, `XSmallIconButtonTokens`, `TimeInputTokens`, `TimePickerTokens` (AAR class list). Turning `MaterialChatShapes.small` into a capsule would reshape surfaces no pass has audited, so chip sites pass `shape = ExpressiveShapes.Chip` explicitly (migration table).

### Part 4 - ContrastSlot
- `ui/theme/ContrastSlot.kt` (new): `interface ContrastSlot { slotName, fill, content, minimum, ratio, passes }`, `ContrastSlotSet { surfaceName, slots }`, `fun interface ContrastSlotResolver`, `ContrastSlotList`, `ThemeContrastSlots` (the theme's own resolver = standard + component pairs), `ContrastSlot.toThemeContrastPair()`.
- `ThemeContrast.kt:112` `ThemeContrastPair : ContrastSlot` (fields unchanged, so `HomeSettingsPalette.pairs()` already yields slots).
- `ThemeContrast.kt:243-279` `ColorScheme.componentContrastPairs()`: snackbar body/action/dismiss, dialog body/supporting/label/destructive/icon, loading-row indicator+text on the three row tiers, error-content glyph/headline/supporting.
- `ThemeContrast.kt:286,294` `dialogActionColor()` / `dialogDestructiveColor()`. **Finding:** raw `primary` on `surfaceContainerHigh` (the M3 dialog container) measures 4.07:1 on VIOLET light and 4.13:1 on CITRUS light, below 4.5:1 for `labelLarge` dialog buttons. `HomeSettingsSlot.DialogLabel` already hid this by resolving to `onSurface`; every other dialog host (`MemoriesScreen.kt:136`, `AssistantVoiceSettingsPanel`, chat) draws the raw role today. The component pair gates the resolved value, and `ThemeContrastTest.dialogActionColourKeepsPrimaryExceptWhereItCannotRead` (`:141`) pins exactly those two appearances as the fallbacks (and that `error` reads everywhere for the destructive label).
- `ThemeContrastTest.kt:52-80` registers resolvers for `theme`, `home-settings`, `assistant` and `chat/<family>` (the chat and assistant palettes are wrapped through an adapter until they implement `ContrastSlotSet` themselves); `:81` `everyRegisteredSurfaceSlotReadsInEveryTheme` walks all of them across the 39 appearances (dynamic schemes judged only on the surface roles the app controls); `:105` gates the component pairs and pins that the five `HomeSettingsSlot` snackbar/dialog slots still describe the same fill/role pairs.

### Part 5 - ErrorContent, empty state, disabled alpha
- `ui/components/ErrorContent.kt` (new): `ErrorContentCopy.TRY_AGAIN = "Try again"`, `GENERIC_HEADLINE`; `ErrorContent(title, message, onRetry, modifier, secondaryLabel?, onSecondary?, icon)` - error tile (`errorContainer`/`onErrorContainer`, `ExpressiveShapes.Tile`, `ExpressiveSizes.Tile`/`TileIcon`), `headlineSmall` Bold, `bodyMedium` on `onSurfaceVariant`, primary `FilledTonal` "Try again" first, optional `Text` secondary after it; on `ExpressiveShapes.Card` over `surfaceContainer`, `StateCardPadding = 24.dp`, `widthIn(max = 360.dp)` like the chat empty state. `ExpressiveStateCard` (`:110`) is the shared card both states draw.
- `ExpressiveSurfaces.kt:284` `ExpressiveEmptyState` now draws `ExpressiveStateCard` (was `HomeEmptyState`'s hero: no card, `headlineMedium`/`bodyLarge`, 32dp, shape cluster). `accent` maps to the primary/secondary container tile pair. The home list keeps `HomeEmptyState`.
- `ExpressiveSurfaces.kt:190-196` decision (audit F15): disabled content stays at the M3 disabled alpha 0.38 (`MaterialChatMotion.Alphas.Disabled`), ungated. WCAG 1.4.3 exempts inactive controls; 0.38 is what every stock M3 disabled control draws; gating it would make a disabled icon read as enabled. Chat's `reasoning disabled` 3:1 slot is a different case (a disabled *label* that still carries information) and is chat's to keep.
- `ErrorContentCopyTest.kt` (3): `TRY_AGAIN == ChatErrorCopy.TRY_AGAIN == AssistantCopy.AFTER_ERROR`; copy rules (sentence case, no trailing period, no ASCII ellipsis); `StateCardPadding == 24.dp`.

### Part 6 - FusionOrbit, MainActivity/navigation check
- `Motion.kt:615` `Durations.FusionOrbit = 3_000` (value-equal to `Indicator.MorphShape`, pinned in `MotionTokensTest.kt:69-74`).
- `MainActivity.kt:320-356` read at `ec82b40`: ad banner (`:323,327`) and floating toolbar (`:345,349`) are on `Spatial.container()` / `Spatial.collapse()`; the 104dp banner clearance is a literal (not a theme token; unchanged). `ui/navigation/MaterialChatNavBar.kt`: every spec is a token (`fabExpand`, `playful`, `scale`, `Effects.color/alpha`); the only `.copy(alpha =` left is `:220`, a 0.28 wash *fill*, not a tint - the W4 `:233` tint is gone. `MaterialChatNavHost.kt:85-113` all on tokens. The W4 home+settings swap is complete; nothing changed.

### Spring values (audit F3; `expressiveControlShape` hold)
`ExpressiveControlShapes.kt:61,73,83` stay on `Spatial.shapeMorph()` (0.6 / 500) per the orchestrator ruling; device check pending. For the record (`Motion.kt`): `default` 0.8/380, `shapeMorph` 0.6/500, `scale` 0.6/600, `expand` 0.75/420, `collapse` 1.0/420, `pressMorph` = `Press.Shape/Progress/Scale` 0.6/800, `fast` 0.6/800 (identical to `pressMorph`), `slow` 0.8/200, `dataReveal` 0.8/50, `pressTint` 1.0/1600.

## Migration table

Everything below is outside this pass's ownership. Line numbers are at `ec82b40` unless stated.

### chat

| File:line | Today | Change |
|---|---|---|
| `ui/screens/chat/ChatScreen.kt:559-600` | private `ErrorContent` (titleLarge, "Go back" then "Try again") | `com.materialchat.ui.components.ErrorContent(title = ErrorContentCopy.GENERIC_HEADLINE, message, onRetry, secondaryLabel = "Go back", onSecondary = onNavigateBack)`; delete the private one |
| `ui/screens/chat/components/ChatContrast.kt:9-10,27-45` | imports `AssistantContrast`/`AssistantContrastPair` | import `ThemeContrast` / `ThemeContrastPair`; `ChatSurfacePalette.pairs()` returns `List<ThemeContrastPair>` and the palette implements `ContrastSlotSet` (`surfaceName = "chat"`), then drop the adapter in `ThemeContrastTest.kt:67-74` |
| `ui/screens/chat/components/FusionAnimation.kt:56` | `Durations.Indicator.MorphShape` | `Durations.FusionOrbit` |
| `ui/screens/chat/components/MessageBubble.kt:1364,1388,1399,1615` | memory chip `height(48.dp)`, icons `size(18.dp)` | `ExpressiveSizes.Chip.Height` (40) or document it as a row; icons `ExpressiveSizes.Chip.Icon` |
| `ui/screens/chat/ChatScreen.kt:551` | `LoadingIndicator` 48 | `ExpressiveSizes.IndicatorHero` |
| `ExportBottomSheet.kt:130`, `RedoModelPickerSheet.kt:106` | indicator 24 | `ExpressiveSizes.IndicatorInline` (20; size change) |
| `ModelPickerDropdown.kt:260`, `BeautifulModelBadges.kt:469,739` | indicator 20 | `ExpressiveSizes.IndicatorInline` |
| `BeautifulModelBadges.kt:375,673`, `WebSearchSourcesCarousel.kt:123,137`, `FusionBadge.kt:33` | `CustomShapes.Pill` badges | `ExpressiveShapes.Chip` (same shape; names the class) |
| `ComposerSendButton.kt:49` | `ComposerFieldShape = SquircleShape(28.dp)` beside `CustomShapes.MessageInputContainer` 28 round | chat decides one composer corner class; `Shapes.kt:333` documents that bubbles + composer stay round |
| Any `AlertDialog` TextButton in chat | raw `primary` | `ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.dialogActionColor())` |

### home-settings

| File:line | Today | Change |
|---|---|---|
| `conversations/ConversationsScreen.kt:1286-1317` | private `ErrorContent`, "Retry" | `ui/components/ErrorContent` (verb becomes "Try again") |
| `bookmarks/BookmarksScreen.kt:142-171` | "Retry" then "Go back" | `ErrorContent(..., secondaryLabel = "Go back", onSecondary = onNavigateBack)` |
| `settings/SettingsScreen.kt:2829-2860` | private `ErrorContent`, "Retry" | `ErrorContent` |
| `personas/PersonaStudioScreen.kt:222-256`, `workflows/WorkflowExecutionScreen.kt:245-279` | "Go back" only | `ErrorContent` needs `onRetry`; wire a reload if the view-model has one, else `ExpressiveEmptyState(actionText = "Go back")` |
| `ui/components/LoadError.kt:25-40` | `LoadErrorContent` -> `ExpressiveEmptyState` (secondary tile, "Try again") | delegate to `ErrorContent(title, message, onRetry)` so the error tile is `errorContainer` like every other error state |
| `memories/MemoriesScreen.kt:136` | `AlertDialog` default shape | `shape = ExpressiveShapes.Dialog`; buttons on `dialogActionColor()` / `dialogDestructiveColor()` |
| `settings/SettingsScreen.kt:2691,2742,2792`, `conversations/ConversationsScreen.kt:331,866` | dialog buttons via `HomeSettingsSlot.DialogLabel/DialogDestructive` or raw role | `dialogActionColor()` / `dialogDestructiveColor()` (same resolution; one source) |
| `workflows/components/StepEditor.kt:65-67` | `RoundedCornerShape(14.dp)`, `size(32.dp)` | `ExpressiveShapes.TileBadge`, `ExpressiveSizes.Compact` |
| `settings/components/SystemPromptField.kt:122` | `CustomShapes.Fab` (deprecated) | `ExpressiveShapes.Tile` |
| M3 chips: `SettingsScreen.kt:2563,2664`, `ModelAssignmentsScreen.kt:667,696`, `InteractionSettingsScreen.kt:208,236`, `InsightsScreen.kt:148`, `BookmarkFilterBar.kt:80,134`, `BookmarkCard.kt:153`, `AddBookmarkSheet.kt:121,162`, `PersonaEditorSheet.kt:170`, `PersonaCard.kt:185`, `ArenaModelPicker.kt:75,134` | default `shapes.small` (12dp) | `shape = ExpressiveShapes.Chip` (`ConversationStarterChips.kt:44` already capsule) |
| `SettingsScreen.kt:2313,2421`, `ModelAssignmentsScreen.kt:437` | indicators 18 / 16 / 20 | `ExpressiveSizes.IndicatorInline` |
| `ConversationsScreen.kt:1016,1405` | indicators 48 | `ExpressiveSizes.IndicatorHero` |
| `ui/components/HomeSettingsContrast.kt:140-170` | `HomeSettingsPalette.pairs()` already returns `ThemeContrastPair` | optionally implement `ContrastSlotSet` (`surfaceName = "home-settings"`) and drop the adapter at `ThemeContrastTest.kt:55-58`; keep the `Snackbar*`/`Dialog*` slots (the test pins them equal to the component pairs) |
| `arena/ArenaScreen.kt:131` | snackbar host (component *unverified*) | `ExpressiveSnackbar` if not already |
| Sheets using `SheetGutter` (`AddProviderSheet.kt:174`, `PersonaPickerSheet.kt:80`, `PersonaEditorSheet.kt:83`, `AddBookmarkSheet.kt:86`) | alias still compiles | optional: `ExpressiveSpacing.SheetGutter` |
| Empty states: `BookmarksScreen.kt:260`, `MemoriesScreen.kt:328,338`, `WorkflowsScreen.kt:166`, `GeneratedImagesScreen.kt:294`, `ArenaLeaderboard.kt:108` | `ExpressiveEmptyState` callers | no code change; they now render the card (see Not verifiable) |

### assistant

| File:line | Today | Change |
|---|---|---|
| `assistant/ui/AssistantExpressiveStyle.kt:74,77,86` | `RoundedCornerShape` Answer/Notice/Thinking | `ExpressiveShapes.AnswerSurface` / `NoticeSurface` / `ThinkingSurface` (radii pinned identical in `ShapeTokensTest.kt:171`) |
| `AssistantExpressiveStyle.kt:80` | `Chip = RoundedCornerShape(50)` | `ExpressiveShapes.Chip` |
| `AssistantExpressiveStyle.kt:178` `ChipHeight 40`, `:184` `Target 48`, `:187` `Control 40`, `:190` `Glyph 32`, `:196` `CompactTarget 32` | literals | `ExpressiveSizes.Chip.Height`, `Target`, `Control`, `Compact`, `Compact` (note: the theme's `CompactTarget` is 44 - the assistant's 32 maps to `Compact`) |
| `AssistantExpressiveStyle.kt:181` | `ChipIcon 16` | `ExpressiveSizes.Chip.Icon` is 18 (audit F4 ruled 18); size change, assistant decides |
| `AssistantExpressiveStyle.kt:156,159` | `Inset 12`, `ChipInset 14` | `ExpressiveSpacing.Inset`, `ExpressiveSizes.Chip.Inset` |
| `assistant/ui/AssistantContrast.kt:63-70,146,184` | `AssistantContrastPair`, `AssistantSurfacePalette.pairs()` | `AssistantContrastPair : ContrastSlot` (`slotName = name`, `fill = container`) and `AssistantSurfacePalette : ContrastSlotSet` (`surfaceName = "assistant"`); then drop the adapter at `ThemeContrastTest.kt:59-65` |
| `assistant/ui/AssistantVoiceSettingsPanel.kt:78-97` | dialog buttons raw role | `dialogActionColor()` |
| `assistant/ui/AssistantCopy.kt:27` | `AFTER_ERROR = "Try again"` | optionally `= ErrorContentCopy.TRY_AGAIN` (already pinned equal) |

## Deliberately left alone
- `ExpressiveControlShapes.kt:61,73,83` on `shapeMorph()` (ruling).
- `MaterialChatShapes.small` / theme-level `ChipDefaults` override (reasoned above; `ExpressiveShapes.Chip` is the per-site token instead).
- `MessageBubbleShapes` and `CustomShapes.MessageInputContainer` round corners (documented decision, `Shapes.kt:333`).
- `CustomShapes.Fab` kept as a deprecated alias (one consumer); `ExpressiveShapes.Sheet` (no consumer, floating-sheet token).
- `HomeSettingsSlot.SnackbarBody/SnackbarAction/DialogBody/DialogLabel/DialogDestructive` kept; the component pairs are the app-wide gate and the test pins the two descriptions equal.
- `HomeEmptyState` (home hero) untouched; only `ExpressiveEmptyState` moved to the card.
- `MainActivity.kt`, `ui/navigation/**`: read, confirmed complete, not edited.

## Needs another owner
- **chat**: rows in the chat table; in particular the `ChatContrast` import flip and `FusionOrbit`.
- **home-settings**: rows in the home-settings table; `LoadError.kt` is in `ui/components` but was created by the W4 home-settings pass and is not in this brief's list, so its delegation to `ErrorContent` is theirs.
- **assistant**: rows in the assistant table; the `ChipIcon` 16 -> 18 decision.
- **orchestrator**: `ExpressiveEmptyState` now draws a card on five screens that previously showed the home hero; if that is not wanted, the W4 shape (`HomeEmptyState` delegation) is one revert of `ExpressiveSurfaces.kt:284-313`.

## Not verifiable without a device
- Every rendered outcome: per-corner squircle corners on real densities, RTL mirroring of `AnswerSurface`/`ThinkingSurface` (JVM tests compare polygon cubics, not pixels), `TileBadge` at 32dp, `Notice` on the snackbar (value-identical to before).
- `ExpressiveEmptyState` / `ErrorContent` card: `widthIn(max 360)` on a 360dp screen with 16dp gutters, two-button row wrapping at font scale 1.3, tile glyph size, the `FilledTonal` primary beside a `Text` secondary.
- `dialogActionColor()` falling back to `onSurface` on VIOLET and CITRUS light: computed, not seen, and no host consumes it yet.
- Dynamic (Monet) schemes are approximated by `ThemeContrastTest.dynamicLike`, not a real extraction; elevation tint, alpha compositing and blur are outside the arithmetic.
- The transient first compile failure (no log, not reproduced).
- No screenshot, emulator or hosted run was made for any of the above.
