# Pass W2 — Theme owner (`ui/theme/**` only)

Branch `pass/w2-theme`, based on main `be7adbb` (v2.41.1). Not pushed. No file outside `app/src/main/java/com/materialchat/ui/theme/**` and `app/src/test/java/com/materialchat/ui/theme/**` was edited. `particles/**`, `ParticleMotionGate`, pitch-black ramp, palette hex values untouched. No test, testTag, contentDescription, script or workflow file touched. No androidTest added.

## Commits

| Commit | Part |
|---|---|
| `8c6e3ea` | feat(theme): one dialog shape and an in-bubble ContentBlock token |
| `7ae8fdf` | feat(theme): list-fill contrast pick, Bob/decorativeSpin motion tokens, press morph on Press tokens, typography weight gate |
| (this file) | docs(theme): REPORT.md |

## Gates (at `7ae8fdf`)

- `:app:compileGithubDebugKotlin` rc=0, `:app:compileGithubDebugAndroidTestKotlin` rc=0.
- `:app:testGithubDebugUnitTest`: **626 tests, 0 failures, 0 errors, 0 skipped**. `ThemeContrastTest` 14 (was 10), `AssistantContrastTest` 10, `ChatContrastTest` 5, `HomeSettingsContrastTest` 5 all green. New: `ShapeTokensTest` 4, `MotionTokensTest` 3, `TypographyWeightsTest` 6, `ThemeContrastTest` +4 = 17 new tests.

## What was audited

`Shapes.kt`, `ShapeMorphing.kt`, `ExpressiveControlShapes.kt`, `Motion.kt`, `ThemeContrast.kt`, `Typography.kt`, `ThemeContrastTest.kt`; `assistant/ui/AssistantContrast.kt` (read-only, for the `pick(fills)` semantics); the four W1 reports and `docs/audit-color-contrast.md` §4/§5; read-only grep of every consumer of `CustomShapes.*`, `ExpressiveShapes.*`, `ShapeMorphing.*`, `expressiveControlShape`, `Durations.*`, the 16 s / 48 s tweens, and every raw `RoundedCornerShape(16/20/24/28/32.dp)` outside the theme (81 sites) for the vocabulary table.

## What changed (file:line at `7ae8fdf`)

### 1. One dialog shape (`Shapes.kt`)
- `:75` `ExpressiveShapes.Dialog` = `SquircleShape(28.dp)` is the one dialog token (M3E squircle, matches `Card`).
- `:340-344` `CustomShapes.Dialog` is now `@Deprecated(ReplaceWith("ExpressiveShapes.Dialog"))` and a `Shape` getter delegating to it. Type widened from `RoundedCornerShape` to `Shape`; grep found **zero** consumers of `CustomShapes.Dialog` in `app/src` (main, test, androidTest), so nothing can break. Pinned by `ShapeTokensTest.customDialogIsTheExpressiveDialog`.

### 2. In-bubble content block token (`Shapes.kt`)
- `:79-87` `ExpressiveShapes.ContentBlock = SquircleShape(16.dp)`. Rationale: same squircle family as `DisclosurePanel` (20dp) so a code block and a sources panel in one bubble never show different corner geometry; 16dp keeps chat's existing 28 -> 16 -> 8dp (bubble -> block -> inner header) ladder, one step inside the panel so a block reads as content *inside* the bubble, not a sibling panel.
- `:264-274` `CustomShapes.CodeBlock` (was `RoundedCornerShape(20.dp)`, zero consumers) deprecated, delegates to `ContentBlock`.

### 3. Multi-fill contrast (`ThemeContrast.kt`)
- `:70` `contrastRatio(foreground, fills: List<Color>)`: lowest ratio across fills; `require(fills.isNotEmpty())`.
- `:84` `pick(fills: List<Color>, candidates, minimum)`: first candidate (white/black appended) that reads on EVERY fill, else the candidate whose worst ratio is highest. Byte-for-byte the semantics of `AssistantContrast.pick(fills, ...)` including the `require`.
- `:97` single-fill `pick` now delegates to `pick(listOf(fill), ...)` (behaviour unchanged; asserted).
- `:137` `ColorScheme.readableOn(fills: List<Color>, minimum, preferred)`; `:151` `readableMutedOn(fills)`; `:158` `readableAccentOn(fills)`. The single-fill extensions (`:126`, `:148`, `:155`) delegate to the list forms.
- Tests (`ThemeContrastTest.kt`): `:140` single vs singleton-list agree on every role of every one of the 39 appearances; `:154` a two-fill case (light + dark) where `onSurface` reads on one fill only, asserting the pick reads on both and equals the assistant copy's pick for the same inputs; `:183` empty fills throws; `:188` list extensions match single-fill ones and keep the preferred role.

### 4. Motion tokens (`Motion.kt`)
- `:575` `MaterialChatMotion.Durations.Bob = ExtraLong + Quick` (800 ms; exactly what `ScrollToBottomFab.kt:124` computes today).
- `:578` `Durations.DecorativeSpin = 16_000`, `:581` `Durations.BackdropSpin = 48_000`.
- `:238` `ExpressiveMotion.Spatial.decorativeSpin(periodMillis = DecorativeSpin): InfiniteRepeatableSpec<Float>` = `infiniteRepeatable(tween(period, LinearEasing), Restart)`, i.e. the exact spec at `ConversationsScreen.kt:516` / `HomeEmptyState.kt:168` / `ExploreScreen.kt:232`. KDoc states it is the one sanctioned infinite tween for ornaments and must only run while `rememberParticleMotionGate().motionAllowed`.
- `:251` `Spatial.bob(halfPeriodMillis = Bob)` = `infiniteRepeatable(tween(Bob), Reverse)`, the FAB's current spec.
- `MotionTokensTest.kt`: `:15` `Press.*` are 0.6/800 (M3E `fastSpatialSpec`, verified by W1 against the 1.5.0-alpha15 AAR; unchanged here); `:23` spin is linear, restart, period as given; `:35` bob half period is 800 ms, reversing.

### 5. ShapeMorphing on the Press tokens (`ShapeMorphing.kt`)
Spec math: `Press.Shape` is 0.6/800; the old specs were `shapeMorph` 0.6/500, `scale` 0.6/600, `playful` 0.5/400. Same damping ratio but 1.6x / 1.33x / 2x the stiffness, so settle time drops by ~21% / ~13% / ~29% (settle ∝ 1/√k at equal ζ) and `playful` also loses bounce (ζ 0.5 -> 0.6). Not identical feel by any of them, so the rule applied was: migrate where no consumer can notice, document where one can.
- Migrated to `Press.Shape` (**zero consumers outside the theme** for each, verified by grep of `ShapeMorphing.` across `app/src`): `fabShape` `:75` (was `playful`), `iconButtonShape` `:99`, `cardShape` `:122` (were `shapeMorph`), `chipShape` `:146` (was `scale`).
- `buttonShape` `:39-51`: one consumer, `ui/components/ExpressiveSnackbar.kt:55`. Kept its default on `Spatial.shapeMorph()` and gained an `animationSpec: SpringSpec<Dp>` parameter defaulting to it, so the snackbar owner opts in with `animationSpec = ExpressiveMotion.Press.Shape` after checking the feel. Additive; existing call compiles unchanged.
- `searchBarShape` `:165`, `textFieldShape` `:184`: focus morphs, not presses; left on `shapeMorph` deliberately (KDoc `:19-28` says so).
- `expressiveControlShape` (`ExpressiveControlShapes.kt:61,73,83`): **not migrated**. Five consumers (`FusionModeToggle.kt:76`, `MessageInput.kt:454,730`, `ConversationsScreen.kt:681`, `ExpressiveLoadingIndicator.kt:667`) drive the send/mic/FAB-class controls, including the `EXPRESSIVE` Morph progress between a `MaterialShapes` polygon and a square. 0.6/500 -> 0.6/800 is a ~21% faster settle on the most-pressed controls in the app and cannot be verified without a device; the W1 chat/home passes already moved their *scale* on these controls to `Press.Scale`, so shape and scale currently settle at 500 vs 800. Recommended next wave: add `animationSpec: SpringSpec<Float>/<Dp>` parameter defaulting to `shapeMorph()` as done for `buttonShape`, then switch the default once a device pass confirms. Not done this wave to keep the consumer owners' baseline stable while they work.

### 6. Typography weight gate (`TypographyWeightsTest.kt`)
No production change. The test enumerates the shipped families: `MaterialChatExpressiveTitleFontFamily` (one instance, Bold) and `MaterialChatRoundedFontFamily` (Light, Normal, Medium, SemiBold, Bold), plus the user-selectable `createFontFamily` set (Light..Black), and asserts for every `TextStyle` in `buildTypography()` (default and with `MaterialChatRoundedFontFamily` / `RobotoFlexFontFamily`) and for `TimestampTypography` that the declared `fontWeight` is one of the weights its `fontFamily` actually ships (`:48-78`), that the title family ships exactly one Bold instance (`:60`), and that nothing in the scale is `FontStyle.Italic` (`:80`). It would have caught W1's `headlineSmall` SemiBold-on-Bold-only finding. `CodeTypography`/`InlineCodeTypography` use `FontFamily.Monospace` (system; weights not enumerable on the JVM) and are excluded with a comment.

## Deliberately left alone
- `CustomShapes.BottomSheet` (32dp top-only) vs `ExpressiveShapes.Sheet` (32dp squircle): different jobs (bottom sheet vs modal card-sheet), W1 §4 already says so; no alias.
- `MaterialChatMotion`, `IntSprings`, `SpringSpecs`: 14 consumers; kept verbatim.
- `ExpressiveShapeDefaults` (unreferenced constants): left; removing is not additive.
- `Spatial.shapeMorph/scale/playful` themselves: ~55 consumer sites; untouched.
- Pitch-black ramp, palette hex, `OutlineDark`, `particles/**`.

## Shape vocabulary — the one token per class, and current drift (consumer owners)

| Class | Token | Value | Drift today (file:line) |
|---|---|---|---|
| Dialog | `ExpressiveShapes.Dialog` | 28dp squircle | 7 sites already on it. `WorkflowExecutionScreen.kt:173` `AlertDialog` on `RoundedCornerShape(24.dp)`; `ExpressiveDialog.kt` 2 raw shapes; `ConversationsScreen.kt` 2 raw dialog/sheet shapes (see §27 grep) |
| Bottom sheet | `CustomShapes.BottomSheet` | 32dp top corners | `PersonaPickerSheet.kt:64`, `PersonaEditorSheet.kt:72`, `AddBookmarkSheet.kt:78` on raw 28dp top corners; `AddProviderSheet.kt:121` private `SettingsSheetShape` |
| Modal card-sheet | `ExpressiveShapes.Sheet` | 32dp squircle | `ExpressiveSurfaces.kt:283`, `ExploreScreen.kt:250`, `OnboardingScreen.kt:218,263` raw 32dp round |
| Card / grouped list | `ExpressiveShapes.Card` | 28dp squircle | 23 sites on it. Raw 28dp: `InteractionSettingsScreen.kt:147`, `MessageBubble.kt:1608`, `ArenaLeaderboard.kt:178`, `ArenaPromptInput.kt:60`, `GeneratedImagesScreen.kt:152`, `MemoriesScreen.kt:258,327`, `OnboardingScreen.kt:369,424`. Raw 24dp cards (insights x5 `:65/:66/:80/:50/:64`, `ModelAssignmentsScreen.kt:246`, `FusionProgressView.kt:80`, `WorkflowCard.kt:52`, `WorkflowExecutionScreen.kt:173`, `BookmarkCard.kt:70`, `ArenaBattleView.kt:138`, `MemoriesScreen.kt:237`, `PersonaStudioScreen.kt:276`, `ExpressiveSurfaces.kt:313`) and 20dp (`StepEditor.kt:51`, `StepResultCard.kt:60`, `WorkflowExecutionScreen.kt:530,584`, `NodePreviewTooltip.kt:46`) are the same class at a different radius |
| Disclosure panel in a bubble | `ExpressiveShapes.DisclosurePanel` | 20dp squircle | `WebSearchSourcesCarousel.kt:88`, `MessageBubble.kt:1296` on it; `FusionSourcesSection.kt:125` raw 16dp; `QuoteReplyPreview.kt:83` raw 20dp |
| Content block in a bubble | `ExpressiveShapes.ContentBlock` (new) | 16dp squircle | `MarkdownText.kt:639` private `MarkdownBlockOuterShape` used at `:744,:883,:940,:1710` |
| Row leading tile | `ExpressiveShapes.Tile` / `RowTile.Resting->Pressed` | 18 -> 24dp squircle | 4 + 2 + 2 sites on it; `ModelAssignmentsScreen.kt:468,527,560,656` raw 16dp tiles |
| Chip / badge / pill | `CustomShapes.Pill` | 50% | `BeautifulModelBadges.kt:267`, `FusionModelSelector.kt:261`, `ConversationStarterChips.kt:51`, `FusionBadge.kt:33` raw 24/20/16dp |
| FAB | `CustomShapes.Fab` (+ `ShapeMorphing.fabShape` for press) | 16dp | 1 site on it; `ScrollToBottomFab` uses its own corner constants + `Press.Shape` (fine) |
| Button | `CustomShapes.Button -> ButtonPressed` / `ShapeMorphing.buttonShape` | 24 -> 12dp | `ExpressiveSnackbar.kt:55` on `buttonShape` with 20/12; text fields `RoundedCornerShape(16.dp)` inside settings cards are M3 text-field shapes, not buttons |
| Expressive control | `expressiveControlShape(token, pressed)` | per `ControlShapeStyle` | 5 sites, all on it |
| Assistant pill | fixed 76dp / 38dp | not a token | unchanged |

## Consumer migration table (do not edit from the theme; per owner)

| Owner | File:line | Today | Replace with |
|---|---|---|---|
| chat | `ui/components/MarkdownText.kt:639` (+ `:744,:883,:940,:1710`) | private `MarkdownBlockOuterShape = RoundedCornerShape(16.dp)` | delete the private val; clip to `ExpressiveShapes.ContentBlock` (16dp squircle; radius unchanged, corner curve becomes G2) |
| chat | `ui/screens/chat/components/ScrollToBottomFab.kt:124` | `tween(MaterialChatMotion.Durations.ExtraLong + MaterialChatMotion.Durations.Quick)` inside `infiniteRepeatable(..., Reverse)` | `animationSpec = ExpressiveMotion.Spatial.bob()` (identical spec; keep the gate) |
| chat | `ui/screens/chat/components/FusionSourcesSection.kt:125` | `RoundedCornerShape(16.dp)` | `ExpressiveShapes.DisclosurePanel` if it is a sibling panel, `ContentBlock` if it is inside the bubble body |
| home | `ui/screens/conversations/ConversationsScreen.kt:516` | `infiniteRepeatable(tween(16_000, LinearEasing))` | `ExpressiveMotion.Spatial.decorativeSpin()` (identical spec; keep the gate) |
| home | `ui/screens/conversations/HomeEmptyState.kt:168` | `infiniteRepeatable(tween(48_000, LinearEasing))` | `ExpressiveMotion.Spatial.decorativeSpin(MaterialChatMotion.Durations.BackdropSpin)` |
| home/components | `ui/components/ExpressiveSnackbar.kt:55` | `ShapeMorphing.buttonShape(isPressed, 20.dp, 12.dp)` | add `animationSpec = ExpressiveMotion.Press.Shape` once the ~21% faster settle is checked on a device |
| explore (no owner this wave) | `ui/screens/explore/ExploreScreen.kt:232` | `tween(48_000, LinearEasing)` | `Spatial.decorativeSpin(Durations.BackdropSpin)` |
| assistant | `assistant/ui/AssistantContrast.kt` | private `relativeLuminance / compositeOver / contrastRatio(fg, fills) / pick(fills, ...) / pick(fill, ...)` | delegate every member to `ThemeContrast` (same names, same signatures, same `require`); keep `SCRIM_ALPHA`, `AssistantContrastPair`, `assistantFills`, the palette types. `ThemeContrastTest.themeAndAssistantContrastAgree` and the new `pickOnSeveralFillsRequiresTheCandidateToReadOnEveryOne` already assert the two agree, so the collapse is a pure deletion |
| any | `CustomShapes.Dialog`, `CustomShapes.CodeBlock` | none in `app/src` | nothing to do; both are deprecated aliases |

## Needs another owner
- Everything in the migration table above.
- `ExpressiveControlShapes.kt` default spring: theme owner next wave, after a device check of `Press.Shape` on the send/mic controls (see §5).

## Not verifiable without a device
- Rendered look of `ExpressiveShapes.Dialog` squircle vs the old round `CustomShapes.Dialog` (no consumer used the old one, so no visible change is expected).
- `ContentBlock` G2 corner on code/table/math blocks and against KaTeX WebView content, once chat adopts it.
- Press feel of `fabShape` / `chipShape` / `iconButtonShape` / `cardShape` on `Press.Shape` (no consumers today), and of `buttonShape` on the snackbar if that owner opts in.
- `decorativeSpin` / `bob` are spec-identical to what the three consumers draw today; identity is asserted on the JVM, the rendered motion is not.
- Multi-fill `pick` on the assistant's feathered pill under real wallpaper-dynamic colour (the JVM gate uses three synthetic seeds).
- The typography weight test proves declared weights exist in the family descriptors; it cannot prove the variable-font instance Android selects at runtime (that is the font loader's job).
