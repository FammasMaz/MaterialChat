# W6 — theme pass report

Branch `pass/w6-theme`, base `9ef37001` (v2.41.5). Static work only: no device, no emulator, no screenshot. Every file:line below was taken from grep/sed on this branch after the edits.

Gates run before each commit: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` (764 tests, 0 failures, 0 errors, 0 skipped at `a74a08d8`).

## Commits

| Commit | Part |
|---|---|
| `a6b0fc2d` | 1 — `ShapeTokensTest` pins the three assistant-surface tokens directly |
| `90e474f5` | 2, 3, 4 — `ThemeContrastTest` resolver collapse + value-based component presence; `ExpressiveTopBarTitle` RTL origin (+ `ExpressiveTopBarTitleOriginTest`) |
| `a74a08d8` | `ThemeContrast.kt` KDoc no longer names the deletable `HomeSettingsSlot` members |
| (this) | REPORT.md |

Part 4 landed inside `90e474f5` rather than its own commit: the edit and the `git add -A` of parts 2–3 ran in one batch, the commit was amended to say so. No code difference.

## Part 1 — `ShapeTokensTest` (`a6b0fc2d`)

- `app/src/test/java/com/materialchat/ui/theme/ShapeTokensTest.kt:170-186` `assistantSurfaceTokensCarryTheAssistantRadiiExactly`: the three `RoundedCornerShape(...) == AssistantShapes.X` lines are gone; the test now pins `ExpressiveShapes.AnswerSurface / NoticeSurface / ThinkingSurface` against `SquircleShape` built with the recorded radii in the assistant's own `topStart / topEnd / bottomStart / bottomEnd` order (36/36/28/28, 24/24/18/18, 20/20/8/20). Added: a positional negative for each asymmetric token (swapped corners are a different shape), a smoothing negative (`smoothing = 0f` is not the token), and the third pairwise inequality. The `com.materialchat.assistant.ui.AssistantShapes` import is removed; the theme tests no longer reference the assistant package for shapes.
- `app/src/main/java/com/materialchat/ui/theme/Shapes.kt:189-193, 199-204` KDoc: the tokens are described as the record of the radii; `AssistantShapes.Answer/Thinking` are named only as the former record. Doc only.

## Parts 2–3 — `ThemeContrastTest` (`90e474f5`)

- `ThemeContrastTest.kt:54-62` `surfaces`: `"home-settings"` is `HomeSettingsContrastSlots.SURFACE to HomeSettingsContrastSlots` (the object already implements `ContrastSlotResolver`, W5 `17e37b2f`); `"assistant"` is `AssistantSurfacePalette.SURFACE to ContrastSlotResolver(AssistantSurfacePalette::resolve)`. No adapter lambdas remain; the KDoc at `:46-52` says so. Both surface names are now the palettes' own constants, so a rename on either side fails this test instead of silently registering a second name. Import added: `com.materialchat.ui.components.HomeSettingsContrastSlots`.
- `ThemeContrastTest.kt:119-132` `componentPairsGateTheSharedSnackbarDialogLoadingRowAndErrorContentEverywhere`: the five by-name pins (`HomeSettingsSlot.SnackbarBody/SnackbarAction/DialogBody/DialogLabel/DialogDestructive`, `fill`/`preferred` compared to roles) are replaced by a value assertion across all 39 appearances: for each of `snackbar body`, `snackbar action`, `dialog body`, `dialog label`, `dialog destructive` in `componentContrastPairs()`, the home-settings slot set must contain a slot with the same fill, the same resolved content and the same minimum. Not weaker: the old pins compared *preferred* roles only; this compares the resolved colour the palette actually paints, and does it on every theme (the old loop did too). Snackbar dismiss / dialog supporting / dialog icon / loading rows / error content are not required of home-settings (they never were) and stay gated through the theme set.
- `ThemeContrastTest.kt:157-167` `dialogActionColourKeepsPrimaryExceptWhereItCannotRead`: `home[HomeSettingsSlot.DialogLabel] == action` / `home[DialogDestructive] == destructive` became "the home-settings slot set has a body-text slot on `surfaceContainerHigh` whose content is `action` / `destructive`". Same guarantee (the two gates resolve the same colour), no slot name.
- `ThemeContrastTest.kt:11-12` still imports `HomeSettingsPalette` / `HomeSettingsSlot`: `homeSettingsPaletteReadsOnEveryFillInEveryTheme` (`:466-495`) walks `HomeSettingsSlot.values()` generically (`resolved[slot]`, `drawn[slot]`, `slot.fill`, `slot.minimum`) and names no member, so deleting the five slots does not touch it.
- `app/src/main/java/com/materialchat/ui/theme/ThemeContrast.kt:238-239, 283-284, 293-294` (`a74a08d8`): KDoc no longer names `HomeSettingsSlot.*`. Doc only.

## Part 4 — `ExpressiveTopBarTitle` RTL origin (`90e474f5`)

- `app/src/main/java/com/materialchat/ui/components/ExpressiveSurfaces.kt:140` reads `expressiveTitleTransformOrigin(LocalLayoutDirection.current)` instead of the literal `TransformOrigin(0f, 0.5f)`; `:175-176` `internal fun expressiveTitleTransformOrigin(direction: LayoutDirection): TransformOrigin = TransformOrigin(if Rtl 1f else 0f, 0.5f)`. Same rule as the masthead's `titleOriginX` (`ConversationsScreen.kt:499`). Imports `LocalLayoutDirection`, `LayoutDirection`, `TransformOrigin` added (`:41, :45`), the fully qualified `androidx.compose.ui.graphics.TransformOrigin` in the lambda is gone.
- `app/src/test/java/com/materialchat/ui/components/ExpressiveTopBarTitleOriginTest.kt` (new, 3 tests): LTR pivots `(0, 0.5)`, RTL pivots `(1, 0.5)`, the two mirror horizontally only. Pure function, no Compose runtime.
- Consumers of `ExpressiveTopBarTitle` need no change; the origin is resolved inside the composable.

## Orchestrator rulings honoured (not re-decided)

- `ExpressiveSurfaces.kt:284-313` `ExpressiveEmptyState` still draws the card on all five screens.
- Assistant `ChipIcon` 16 vs `ExpressiveSizes.Chip.Icon` 18: neither touched.
- `expressiveControlShape` still on `shapeMorph()`.
- Error verb / period rules: no copy changed in my files (`ErrorContent.kt`, `ExpressiveSnackbar.kt` already comply per W5).

## Migration table — what each owner consumes from this pass

### assistant

| # | File:line | Change | Why it is now safe |
|---|---|---|---|
| A1 | `assistant/ui/AssistantExpressiveStyle.kt:88-108` | Delete `AssistantShapes.Answer`, `Notice`, `Thinking` and the W5 "kept as the radii record" comment (`:88-92`). | `ShapeTokensTest` no longer references them (`a6b0fc2d`); grep of `app/src` finds no other code reference — only the three comments in A2. `AssistantContrastSlotTest.kt:57` uses `AssistantShapes.Chip`, unaffected. |
| A2 | `assistant/ui/AssistantOverlay.kt:1242`, `:1659`; `assistant/ui/components/AssistantResponseCard.kt:252` | Reword the three `// W5: the theme's per-corner squircle with AssistantShapes.X's radii.` comments to name `ExpressiveShapes.AnswerSurface / NoticeSurface / ThinkingSurface`. | Comment only. |
| A3 | `assistant/ui/AssistantContrast.kt:251` `SURFACE = "assistant"`, `:253 resolve(scheme)` | Contract: `ThemeContrastTest.kt:57` registers the palette by `AssistantSurfacePalette.SURFACE` and `AssistantSurfacePalette::resolve`. Renaming either fails the theme test. | No change required. |

### home-settings

| # | File:line | Change | Why it is now safe / what must stay |
|---|---|---|---|
| H1 | `ui/components/HomeSettingsContrast.kt:80-84` | Delete `DialogBody`, `DialogLabel`, `DialogDestructive`, `SnackbarBody`, `SnackbarAction` from `HomeSettingsSlot`. | `ThemeContrastTest` no longer names them (`90e474f5`); grep of `app/src/main` finds no `rememberHomeSettingsPalette()[HomeSettingsSlot.Snackbar*/Dialog*]` consumer. |
| H2 | `ui/components/HomeSettingsContrast.kt:170` `override val slots: List<ContrastSlot> get() = pairs()` | Must become `pairs() + scheme.componentContrastPairs()` (the chat pattern, `ChatSurfacePalette`), **or** keep the five slots. | `ThemeContrastTest.kt:119-132` and `:157-167` assert by value that a slot equal to each of the five component pairs is present in the home-settings slot set on every theme. With H1 alone and no H2 the theme test fails. Keep `pairs()` itself as the enum walk: `homeSettingsPaletteReadsOnEveryFillInEveryTheme` (`ThemeContrastTest.kt:479-482`) asserts `HomeSettingsSlot.values().size == resolved.pairs().size`. |
| H3 | `ui/components/HomeSettingsContrast.kt:196-200` KDoc of `HomeSettingsContrastSlots` | Drop the sentence saying the theme test pins the slots by name. | Doc only. |
| H4 | `app/src/test/.../ui/components/HomeSettingsContrastSlotsTest.kt`, `HomeSettingsContrastTest.kt` | Theirs to update if they pin the five names (not read here; not theme-owned). | — |

### chat

Nothing to consume from parts 1–4. `ChatSurfacePalette` was already registered through its own resolver. Part 5 rows below.

## Part 5 — final theme inventory (static grep of `app/src`, this branch)

Rule: raw `spring(` / `tween(` outside `ui/theme`; `RoundedCornerShape(` outside `ui/theme`; `.copy(alpha =` on text/icon colours; `FontStyle.Italic`; ASCII `...` in user-visible strings. No edits outside my ownership were made.

**Raw `spring(` outside `ui/theme`: none.** **`FontStyle.Italic`: none.** **`...` in `strings.xml`: none.**

### `tween(` outside `ui/theme`

| Owner | File:line | Today | Token / action |
|---|---|---|---|
| chat | `ui/screens/chat/components/StreamingIndicator.kt:481` | `tween(durationMillis = durationMillis, easing = easing)` inside a parameterised helper | Pass-through; acceptable if every caller passes `MaterialChatMotion.Durations.*`. Chat to confirm callers; no theme token needed. |
| home-settings (drawer screen) | `ui/screens/mindmap/components/MindMapCanvas.kt:135` | `tween(Durations.StreamingIndicator, LinearEasing)` | Already on the duration token; only the wrapper is raw. Leave, or theme adds `MaterialChatMotion.Tweens.linear(duration)` in a later wave. |
| theme (`ui/components`, not in this brief's list) | `ui/components/ExpressiveLoadingIndicator.kt:111, 126, 303, 313, 419, 429` | `tween(Durations.Indicator.*, Linear/EaseInOutCubic)` | On duration tokens with explicit easing; left as is. Candidate for `Tweens.Indicator.*` if the orchestrator wants zero raw `tween(` calls. |

### `RoundedCornerShape(` outside `ui/theme`

Animated corners (`RoundedCornerShape(animatedDp)`) are structural: a `Dp` state must be wrapped per frame and no token can replace the constructor. Listed so the count is honest, with "animated" in the action column.

| Owner | File:line | Today | Token / action |
|---|---|---|---|
| assistant | `AssistantExpressiveStyle.kt:86` | `Pill = RoundedCornerShape(38.dp)` | Geometry contract (76dp capsule). Keep. |
| assistant | `AssistantExpressiveStyle.kt:95, 98, 108` | `Answer / Notice / Thinking` | Delete (A1). |
| assistant | `AssistantExpressiveStyle.kt:111` | `Toolbar = RoundedCornerShape(50)` | Value-identical to `ExpressiveShapes.Chip` (`CustomShapes.Pill`); alias it or document the toolbar as its own capsule. |
| assistant | `AssistantExpressiveStyle.kt:123, 135` | `ConnectedPressed(12dp)`, `connectedShape(index, count)` | Per-position radii; no token (W5 decision). Keep. |
| assistant | `AssistantOverlay.kt:874, 1063, 1125, 1749` | animated corner | animated; keep. |
| assistant | `AssistantOverlay.kt:1582, 1629` | `ThumbnailCorner`, `GlyphRoundCorner` constants | Fixed values with no theme token; keep unless a thumbnail token is added. |
| home-settings | `ui/navigation/MaterialChatNavBar.kt:259` | `RoundedCornerShape(999.dp)` | `ExpressiveShapes.Chip` (capsule). |
| home-settings | `ui/screens/settings/OnDeviceModelsScreen.kt:509` | `RoundedCornerShape(50)` | `ExpressiveShapes.Chip`. |
| home-settings | `ui/screens/conversations/components/ConversationItem.kt:194, 219`; `ExpandableConversationGroup.kt:252, 276, 289`; `BranchConversationItem.kt:207` | `RoundedCornerShape(50)` ("M3: full (pill)") | `ExpressiveShapes.Chip`. |
| home-settings | `ExpandableConversationGroup.kt:315`; `BranchConversationItem.kt:132`; `ui/screens/personas/components/PersonaCard.kt:104`; `ui/screens/settings/SettingsScreen.kt:1951`; `ui/screens/conversations/components/SwipeToDeleteBox.kt:232, 434`; `SegmentedRowShapes.kt:64` | animated / lerped / per-position corners | animated; keep. |
| home-settings | `ui/screens/settings/SettingsScreen.kt:1977-1990` | bubble-style preview: `24/6/24/24`, `28`, `16/6/16/16`, `18/4/18/10` literals | Duplicates the chat bubble family (`MessageBubble.kt:927-970`). Should derive from one chat-exposed function (needs chat to expose it) so the preview cannot drift from the real bubble. |
| home-settings | `ui/screens/insights/components/ActivityHeatmap.kt:156` | `RoundedCornerShape(3.dp)` on a 12dp cell | No token fits (Badge is 8dp). Keep. |
| home-settings | `ui/screens/onboarding/OnboardingScreen.kt:478` | `RoundedCornerShape(44.dp)` on a 132dp hero | No token; candidate for `SquircleShape(44.dp)` for family consistency. Rendering decision. |
| chat | `ui/screens/chat/components/MessageBubble.kt:927, 928, 948` | `COMPACT 20`, `GEOMETRIC 16`, `ROUNDED 34` | Fixed bubble radii with no theme token. If the orchestrator wants them tokenised, theme adds `ExpressiveShapes.Bubble.{Compact,Geometric,Rounded}` next wave; chat consumes. Composer stays round per W4 ruling. |
| chat | `MessageBubble.kt:950, 956, 964, 970, 1098, 1375`; `MessageActions.kt:428`; `ChatTopBar.kt:532`; `ModelPickerDropdown.kt:171`; `MessageInput.kt:565, 576, 584`; `ScrollToBottomFab.kt:181`; `ExpressivePositionShapes.kt:33, 64` | animated / per-position corners | animated; keep. |
| chat | `ui/screens/chat/components/DateSeparator.kt:81` | `RoundedCornerShape(1.dp)` hairline | Keep. |
| theme (`ui/components`, not in this brief's list) | `ui/components/ExpressiveButton.kt:105, 293, 360, 435, 546` | animated `cornerRadius` | animated; keep. |
| theme (`ui/components`, not in this brief's list) | `ui/components/MarkdownText.kt:1720` | `RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)` table header | Bottom-only 8dp; no token. Keep. |

### `.copy(alpha =` on text / icon colours

Every hit was opened. Fills, washes, tracks, hairlines and decorative canvas dots are exempt by the audit rule. Text/icon-colour hits:

| Owner | File:line | Today | Action |
|---|---|---|---|
| chat | `MessageBubble.kt:716` | `primary.copy(alpha = swipeProgress)` swipe-reveal icon | Progress-driven reveal, reaches 1.0; exempt (W4 audit). |
| theme | `ui/components/ExpressiveSurfaces.kt:196` | `disabledContentColor @ Alphas.Disabled` | W5 decision (F15), documented in the file. |
| home-settings (drawer screen) | `ui/screens/workflows/components/StepProgressIndicator.kt:106, 172` | inactive connector / ring `outline @ 0.3 / 0.4` | Non-text indicator; at 0.3–0.4 on a surface tier it is unlikely to reach 3:1 (WCAG 1.4.11). Not measured; home-settings to either lift to `outlineVariant` at full alpha or document as decorative. |
| home-settings (drawer screen) | `ui/screens/arena/components/ArenaLeaderboard.kt:250` | `LinearProgressIndicator` colour `contentColor @ 0.6`, track `@ 0.12` | Active track is a meaningful indicator; 0.6 of `onSurface` on the card may miss 3:1. Not measured; same choice as above. |

Exempt (fill / wash / track / hairline / decorative): `AssistantContrast.kt:303`; `SettingsScreen.kt:3366`; `FusionAnimation.kt:93, 103`; `FusionProgressView.kt:139`; `StreamingIndicator.kt:187, 427, 428`; `FusionModelSelector.kt:170`; `MessageBubble.kt:1084, 1634`; `ExploreScreen.kt:281, 292, 430`; `ConversationsScreen.kt:571`; `ArenaLeaderboard.kt:206`; `MindMapCanvas.kt:497`; `MemoriesScreen.kt:302`; `OnboardingScreen.kt:477`; `ExpressiveSurfaces.kt:68-70`; `ExpressiveLoadingIndicator.kt:390`; `MarkdownText.kt:397, 494`.

### ASCII `...` in user-visible strings

| Owner | File:line | Today | Action |
|---|---|---|---|
| orchestrator (domain, no UI owner) | `domain/usecase/GetBuiltinPersonasUseCase.kt:104` | `"Challenge my argument that..."` persona starter | `…` (U+2026). |
| orchestrator (domain) | `domain/usecase/SendMessageUseCase.kt:825, 1064` | `take(n - 3) + "..."` title / preview truncation | `take(n - 1) + "…"`. |
| orchestrator (domain) | `domain/usecase/GenerateConversationTitleUseCase.kt:324, 339` | same pattern | same; has JVM tests on title length, adjust the `- 3`. |
| orchestrator (data) | `data/repository/ConversationRepositoryImpl.kt:498, 500` | search-snippet `"..."` | `…`; the snippet reaches the home search results. |
| home-settings | `ui/screens/settings/components/AddProviderSheet.kt:331` | placeholder `"sk-..."` | `"sk-…"`. |

Not user-visible, left: `AssistantCopy.kt:14`, `AssistantExpressiveStyle.kt:250`, `AssistantAction.kt:51, 197`, `ModelNameParser.kt:7`, `SseEventParser.kt:107` (comments / KDoc); `ExtractMemoriesUseCase.kt:375` (model-facing prompt template). The assistant's phase word is not a `"Thinking..."` literal (grep of string literals: none).

## Deliberately left alone

- `ExpressiveSnackbar.kt`, `ErrorContent.kt`: re-read against the copy rulings; already "Try again", no period on one-phrase snackbars. No change.
- `ExpressiveSurfaces.kt:68-70` ambient washes and `:196` disabled alpha: documented decisions.
- `ExpressiveLoadingIndicator.kt` / `ExpressiveButton.kt` / `MarkdownText.kt` are in `ui/components` but not in this brief's ownership list; inventory rows only.

## Needs another owner

- **assistant**: A1–A2 (delete the three vals, reword three comments). Optional: `Toolbar` alias (inventory).
- **home-settings**: H1–H4 (delete the five slots **and** append `componentContrastPairs()` to `slots`, else the theme gate fails); `MaterialChatNavBar.kt:259`, `OnDeviceModelsScreen.kt:509`, the six `RoundedCornerShape(50)` row chips → `ExpressiveShapes.Chip`; `SettingsScreen.kt:1977-1990` bubble preview duplication; `AddProviderSheet.kt:331`; the two indicator-alpha reviews.
- **chat**: expose the bubble-family shape function for the settings preview; confirm `StreamingIndicator.kt:481` callers; bubble radii tokenisation is a theme decision pending the orchestrator.
- **orchestrator**: assign the five domain/data `...` sites (no UI owner); decide whether `ExpressiveShapes.Bubble.*` tokens are wanted; decide whether zero raw `tween(` in `ui/components` is a goal.

## Not verifiable without a device

- `ExpressiveTopBarTitle` under `LayoutDirection.Rtl`: the pivot is unit-tested as a pure function; the rendered scale-toward-the-trailing-edge was not seen. Every screen that passes `collapsedFraction` to it (home, settings, insights, mindmap) is affected only in RTL.
- The Part 3 value assertion proves the home-settings palette *resolves* the same colours as `componentContrastPairs()`; whether its dialog and snackbar hosts *paint* those resolved colours is the home-settings owner's device check, unchanged by this pass.
- The inventory's "likely <3:1" notes on `StepProgressIndicator` and `ArenaLeaderboard` are not measured; they are flagged for the owner, not asserted.
- Dynamic (Monet) schemes remain the `dynamicLike` approximation in `ThemeContrastTest`.
- No screenshot, emulator or hosted run was made.
