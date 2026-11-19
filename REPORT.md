# Wave 3 — theme (ui/theme/** only)

Branch `pass/w3-theme`, base `ba3f38b` (v2.41.2). Only `app/src/main/java/com/materialchat/ui/theme/**` and `app/src/test/java/com/materialchat/ui/theme/**` were edited. No test, testTag, contentDescription, script or workflow file was touched; no `FontStyle.Italic` anywhere under the owned paths.

## Audited
- `ui/theme/Shapes.kt` (whole file), `ui/theme/Motion.kt` (whole file), `ui/theme/ThemeContrast.kt` (whole file), `ui/theme/ExpressiveControlShapes.kt:56-96`.
- Tests: `ShapeTokensTest.kt`, `MotionTokensTest.kt`, `ThemeContrastTest.kt` (whole files).
- Consumers read, not edited: `settings/components/SettingsExpressive.kt:58-80`, `ui/components/ExpressiveLoadingIndicator.kt:79-98,133,148,325,335,441,451`, `chat/components/MessageBubble.kt:627`, `insights/components/{StatCard.kt:43-46, ModelUsageChart.kt:72-75, ActivityHeatmap.kt:56-59, ModelComparisonCard.kt:53-56, ResponseTimeChart.kt:53-56}`, `assistant/ui/AssistantContrast.kt` (public API of `AssistantSurfacePalette`).
- W2 handoffs: `docs/reports/pass-w2-{theme,chat,home-settings,assistant}.md` “Needs another owner”.

## Changed (file:line, after commit d4d2b37)

### Part 1 — bottom-sheet token
- `Shapes.kt:64-88` new `TopSquircleShape(cornerRadius, smoothing = 0.65f)`: G2 squircle on the two top corners, square bottom. Same `RoundedPolygon.rectangle(perVertexRounding = [Unrounded, Unrounded, rounded, rounded])` construction as `SettingsSheetShape`, so a consumer swap is geometry-identical. `equals`/`hashCode` by radius+smoothing (hash offset by 1 so it never collides with `SquircleShape` at the same radius).
- `Shapes.kt:109` `ExpressiveShapes.BottomSheet: Shape = TopSquircleShape(32.dp)` — the one bottom-sheet contour, same family as `Dialog`/`Card`.
- `Shapes.kt:102` `ExpressiveShapes.Sheet` doc narrowed to “floating sheet rounded on every corner”; value unchanged.
- `Shapes.kt:303-307` `CustomShapes.BottomSheet` is now `@Deprecated(ReplaceWith("ExpressiveShapes.BottomSheet"))` and delegates. Visible change for its one consumer: 32dp circular top corners -> 32dp G2 top corners.

### Part 2 — ProviderCard
- `Shapes.kt:353-357` `CustomShapes.ProviderCard` `@Deprecated(ReplaceWith("ExpressiveShapes.Card"))`, delegates to the 28dp squircle (same treatment as `CustomShapes.Dialog`). Visible change for its one consumer: 28dp round -> 28dp squircle.

### Part 3 — motion tokens
- `Motion.kt:606-624` `MaterialChatMotion.Durations.Indicator { MorphRotation = 4000, MorphShape = 3000, ArcRotation = 1332, ArcSweep = ArcRotation / 2, LinearPass = 1500, LinearWidth = LinearPass / 2 }` — exactly the private `IndicatorCycle` values.
- `Motion.kt:599` `Durations.StatsPillHold = 3_500`.
- `Motion.kt:236-239` `ExpressiveMotion.Spatial.dataReveal()` = `spring(0.8f, 50f)`, the spec `StatCard` and `ModelUsageChart` draw today. Deliberately slower than `slow()` (200).

### Part 4 — contrast tests retargeted (same or stronger)
- `ThemeContrastTest.kt:156-183` `pickOnSeveralFillsRequiresTheCandidateToReadOnEveryOne`: now asserts `pick(fills)` semantics directly — worst-of-fills is the judged number, a candidate reading on only the first fill is skipped, the least-bad fallback is the max-of-min candidate, preference order is honoured when more than one candidate passes, and the singleton list equals the single-fill overload.
- `ThemeContrastTest.kt:185-216` new `pickMatchesHandComputedExpectations`: hand-computed WCAG ratios (white/black/#777777 reference values and fixed fills) drive expected `pick` results, including the appended white/black fallback.
- `ThemeContrastTest.kt:241-270` `themeAndAssistantContrastAgree`: no longer compares `ThemeContrast` with its own delegate; it now asserts the assistant's constants equal the theme's and that `AssistantContrast` results match hand-computed numbers, so a drift in either object fails.
- `ThemeContrastTest.kt:272-300` new `assistantSurfacePaletteReadsOnEveryFillInEveryTheme`: `AssistantSurfacePalette.resolve(scheme).pairs()` (read-only, public API) must pass 4.5:1 / 3:1 on every fill across all 39 themes (13 palettes x light/dark/pitch-black + 3 dynamic seeds x 3).

### Part 5 — ExpressiveControlShapes default spring
- Left on `ExpressiveMotion.Spatial.shapeMorph()` (`ExpressiveControlShapes.kt:63,75,88`, 0.6/500). Moving to `Press.Shape` (0.6/800) needs a device check on the send/mic controls in all three `ControlShapeStyle`s. Not changed.

### Tests added
- `ShapeTokensTest.kt:33` `customBottomSheetIsTheExpressiveBottomSheet`, `:41` `providerCardIsTheExpressiveCard`, `:46` `topSquircleIsNotTheAllCornerSquircleAtTheSameRadius`, `:56` `degenerateSizesOutlineAsPlainRectangles`.
- `MotionTokensTest.kt:48` `indicatorCyclesMatchTheLoadingIndicatorAndTheM3CircularSpec`, `:61` `statsPillHoldIsWhatTheMessageBubbleDelays`, `:66` `dataRevealIsTheSlowInsightsCountUp`.

## Consumer migration table (you edit; I did not)

| Owner | File:line | Today | Migrate to | Visible change |
|---|---|---|---|---|
| settings | `settings/components/SettingsExpressive.kt:58-80` `SettingsSheetShape` | private top-squircle object | `val SettingsSheetShape: Shape get() = ExpressiveShapes.BottomSheet` (or delete and repoint) | none (identical geometry) |
| settings | `settings/components/AddProviderSheet.kt:121` | `SettingsSheetShape` | `ExpressiveShapes.BottomSheet` | none |
| settings | `settings/OnDeviceModelsScreen.kt:141` | `CustomShapes.BottomSheet` | `ExpressiveShapes.BottomSheet` | round 32dp top -> squircle 32dp top |
| settings | `settings/components/SystemPromptField.kt:101` | `CustomShapes.ProviderCard` | `ExpressiveShapes.Card` | round 28dp -> squircle 28dp |
| chat | `chat/components/RedoModelPickerSheet.kt:43,75` | imports settings `SettingsSheetShape` | `ExpressiveShapes.BottomSheet` | none |
| chat | `chat/components/ExportBottomSheet.kt:39,78` | same | `ExpressiveShapes.BottomSheet` | none |
| chat | `chat/components/FusionModelSelector.kt:39,83` | same | `ExpressiveShapes.BottomSheet` | none |
| chat | `chat/components/MessageBubble.kt:627` | `delay(3_500)` | `delay(MaterialChatMotion.Durations.StatsPillHold.toLong())` | none |
| home | `personas/components/PersonaPickerSheet.kt:23,65` | `SettingsSheetShape` | `ExpressiveShapes.BottomSheet` | none |
| home | `personas/components/PersonaEditorSheet.kt:17,73` | same | `ExpressiveShapes.BottomSheet` | none |
| home | `bookmarks/components/AddBookmarkSheet.kt:4,79` | same | `ExpressiveShapes.BottomSheet` | none |
| home (components) | `ui/components/ExpressiveLoadingIndicator.kt:79-98` + `:133,148,325,335,441,451` | private `IndicatorCycle.*Millis` | delete object; `MaterialChatMotion.Durations.Indicator.{MorphRotation,MorphShape,ArcRotation,ArcSweep,LinearPass,LinearWidth}` | none |
| home (insights) | `insights/components/StatCard.kt:43-46` | `spring(0.8f, 50f)` | `ExpressiveMotion.Spatial.dataReveal()` | none |
| home (insights) | `insights/components/ModelUsageChart.kt:72-75` | `spring(0.8f, 50f)` | `dataReveal()` | none |
| home (insights) | `insights/components/ActivityHeatmap.kt:56-59` | `spring(0.9f, 40f)` | `dataReveal()` | slightly faster (40->50), slightly less damped (0.9->0.8); decide per screen |
| home (insights) | `insights/components/ModelComparisonCard.kt:53-56` | `spring(0.7f, 60f)` | `dataReveal()` | slightly slower (60->50), less bounce (0.7->0.8) |
| home (insights) | `insights/components/ResponseTimeChart.kt:53-56` | `spring(0.7f, 60f)` | `dataReveal()` | same as above |

After chat and home migrate, `SettingsSheetShape` has one consumer (`AddProviderSheet`) and can be removed by the settings owner.

## Part 6 — shape-vocabulary inventory (current main, outside ui/theme)
Raw `RoundedCornerShape(16–32dp)`: **43** matches (W2: 81). Two are doc comments (`StatCard.kt:26`, `BookmarkCard.kt:50`) and `MessageInput.kt:964` is a bare `shape` line, so 40 real sites. Raw top-only 28/32dp sheets: **0** (W2: 3; all moved to `SettingsSheetShape`).

Per owner (real sites):
- chat (6): `FusionProgressView.kt:80 (24), :208 (16)`, `FusionBadge.kt:33 (16)`, `QuoteReplyPreview.kt:83 (20)`, `BeautifulModelBadges.kt:267 (24, pill)`, `MessageBubble.kt:924-925 (bubble style enum, keep), :1502 (28 clip), :1630 (28), :1644 (24 clip)`.
- settings (12): `ModelAssignmentsScreen.kt:284,468,527,560,656 (16)`, `SettingsScreen.kt:1972 (bubble style enum, keep), :2342,2374,2592,3354,3380 (16)`.
- home/personas (11): `ConversationStarterChips.kt:51 (20)`, `PersonaPickerSheet.kt:90,163 (20)`, `PersonaEditorSheet.kt:104,112,125,138,150,191 (20 — text fields, M3 text-field shape, keep per W2), :177 (16)`.
- home/bookmarks (4): `BookmarkCard.kt:107 (16)`, `BookmarkSearchBar.kt:65 (24)`, `AddBookmarkSheet.kt:195,247 (20, text fields)`.
- home/other (5): `WorkflowCard.kt:107 (16)`, `ArenaPromptInput.kt:60 (28)`, `MemoriesScreen.kt:239 (24)`, `OnboardingScreen.kt:282,384 (24)`.

Candidates per class: 16dp content blocks/inner cards -> `ExpressiveShapes.ContentBlock` or `MenuSelection`; 20dp panels -> `DisclosurePanel`; 24dp list cards -> `CustomShapes.ConversationItem`; 28dp cards -> `ExpressiveShapes.Card`. Text-field shapes and the bubble-style enums are deliberate and should stay.

## Deliberately left alone
- `ExpressiveControlShapes.kt` spring (Part 5).
- `ExpressiveShapes.Sheet` (32dp all-corner squircle): no consumer today; kept as the floating-sheet token rather than removed.
- `MaterialChatMotion.Springs.*`, `Tweens.*`, `IntSprings`: legacy, consumers elsewhere.
- `SettingsSheetShape` itself (settings owner's file).

## Needs another owner
- Every row in the migration table.
- Settings owner: after the chat/home migrations, delete `SettingsSheetShape` or alias it to `ExpressiveShapes.BottomSheet`.
- Home/insights owner: decide whether `ActivityHeatmap` (0.9/40) and the two 0.7/60 charts adopt `dataReveal()` or keep their own spec; the token is pinned to the two-of-five majority.
- Component owner: delete `IndicatorCycle` once on the token.

## Verified
- `:app:compileGithubDebugKotlin` and `:app:compileGithubDebugAndroidTestKotlin` rc=0.
- `:app:testGithubDebugUnitTest`: 651 tests, 0 failures, 0 errors, 0 skipped (`ThemeContrastTest` 16/16, `MotionTokensTest` 6/6, `ShapeTokensTest` 8/8).

## Not verifiable without a device
- Rendered contour of `TopSquircleShape(32.dp)` vs the old round `CustomShapes.BottomSheet` on `OnDeviceModelsScreen` (the only consumer with a visible change), and of `ExpressiveShapes.Card` on `SystemPromptField`.
- That `TopSquircleShape` and `SettingsSheetShape` render pixel-identically; construction is identical, the raster was not compared.
- `dataReveal()` feel on the three insights charts whose spec would change on migration.
- The indicator cycle tokens are value-identical; the loops' static gated frames were not observed.
- `AssistantSurfacePalette` contrast is proven on 39 JVM-generated schemes; real wallpaper-dynamic colour and the feathered pill under a live particle field were not.
- Press feel of `ExpressiveControlShapes` on `shapeMorph()` vs `Press.Shape` (Part 5, unchanged).
