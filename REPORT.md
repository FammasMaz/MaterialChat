# Pass W2 — Home + Settings + shared components: report

Branch `pass/w2-home-settings`, rebased onto origin/main `3024e2a` (W2 theme + W2 chat merged). Base at start: `be7adbb` (v2.41.1). Not pushed.

Commits (oldest first, post-rebase hashes):

- `0228a5a` refactor(ui): W2 part 1 — shared components on one shape/motion/colour vocabulary
- `28836a5` refactor(ui): W2 part 2a — drawer screens (memories, bookmarks, images, workflows, personas) on the shared top bar/row/empty-state/sheet vocabulary and Motion tokens
- `a955fa4` refactor(ui): W2 part 2b — insights/arena/canvas/mind map/workflows on ScreenContainer, Card/Dialog/Tile tokens and ListItem springs; theme migrations
- `da78253` refactor(ui): W2 part 2c — search rows/bar on Press and Disclosure tokens, arena reveal on Disclosure physics, onboarding cards on Sheet/Card/Tile tokens
- `4b1224f` refactor(ui): W2 parts 3+4 — home snackbar clears the floating toolbar from toolbar height + nav inset, explore tile text on full-opacity on* roles, settings cards on ExpressiveShapes.Card; speculative contrast slots pruned

Gates at HEAD after the final rebase: `:app:compileGithubDebugKotlin` OK; `:app:testGithubDebugUnitTest` 630 tests, 0 failures, 0 errors, 0 skipped (`HomeSettingsContrastTest` 6, `HomeListInsetsTest` 6); `:app:compileGithubDebugAndroidTestKotlin` OK. No androidTest added; `scripts/`, `.github/workflows/`, testTags, contentDescriptions untouched (verified by `git diff origin/main` over those paths and over `testTag(`/`contentDescription =` removals: the only two removed lines are `contentDescription = null` on decorative icons inside hand-rolled memories empty states that were replaced by `ExpressiveEmptyState`, whose icon is also `null`). No `FontStyle.Italic` in owned files.

Rebase conflict rule honoured: `ui/components/ImageViewerDialog.kt` is byte-identical to origin/main (chat pass owns it). My part-1 edits to it, the `ImageViewerAction` contrast slot and its `imageViewerActionsReadOnTheirTintedFill` test were dropped during the rebase; `HomeSettingsContrast.kt:123` carries a comment pointing to `ChatContrastTest`.

## What was audited

Every file under the ownership list grepped for `spring(`/`tween(`, `RoundedCornerShape(`, `AlertDialog`, `ModalBottomSheet`, `TopAppBar`/`CenterAlignedTopAppBar`, `rememberInfiniteTransition`, `Color.Black/White`, `.copy(alpha` on text, `FontStyle.Italic`, Title-Case titles and trailing periods, hand-rolled empty states. Material3 1.5.0-alpha15 token availability checked in `ui/theme/Motion.kt` and `Shapes.kt` (theme owner's W2 additions: `Spatial.decorativeSpin`, `Spatial.bob`, `Durations.DecorativeSpin/BackdropSpin/Bob`, `ShapeMorphing.buttonShape(animationSpec)`, `ExpressiveShapes.Dialog` as the one dialog token).

## Inventory — secondary screens (state at HEAD)

| Screen | Top bar | Row / card | Empty state | Dialog / sheet | FAB | Drift fixed this wave |
|---|---|---|---|---|---|---|
| Memories | `TopAppBar` + `ExpressiveTopBarTitle` + `ExpressiveFilledIconButton` | `ExpressiveShapes.Card` (`MemoriesScreen.kt:260`), tile `ExpressiveShapes.Tile` (`:272`) | `ExpressiveEmptyState` (`:321`, `:331`) | `ExpressiveShapes.Dialog` (`:151`) | none | card 28dp round → Card squircle, tile 18dp round → Tile, two hand-rolled empty states → shared, `animateItem` → `ListItem.*` (`:208-210`) |
| Bookmarks | same pattern (`BookmarksScreen.kt:73-90`) | `CustomShapes.ConversationItem` (`BookmarkCard.kt:72`), same 24dp as home rows | `ExpressiveEmptyState` (`:260`) | `AddBookmarkSheet.kt:79` `SettingsSheetShape` | none | content-size spring → `Spatial.contentSize()` (`:190`), `animateItem` → `ListItem.*` (`:240-242`), chip colour springs → `Effects.color()` (`BookmarkFilterBar.kt:76,130`, `AddBookmarkSheet.kt:116`), sheet raw 28dp top → settings sheet contour |
| Generated images | same pattern | `ExpressiveShapes.Card` (`GeneratedImagesScreen.kt:153`) | `ExpressiveEmptyState` (`:269`) | — | none | card 28dp round → Card |
| Workflows | same pattern | `CustomShapes.ConversationItem` (`WorkflowCard.kt:55`), tile `ExpressiveShapes.Tile` (`:65`); swipe background on `ConversationItem` (`WorkflowsScreen.kt:211`) | `ExpressiveEmptyState` (`WorkflowsScreen.kt:157`) | `WorkflowExecutionScreen.kt:174` `ExpressiveShapes.Dialog` (was 24dp round) | `ExpressiveFab` | `animateItem` → `ListItem.*` (`WorkflowsScreen.kt:225-227`, `WorkflowExecutionScreen.kt:339-432`; per-item `remember`ed springs → singleton tokens `:314-315`), tiles `WorkflowBuilderScreen.kt:129,147` / `WorkflowExecutionScreen.kt:454` → Tile, step editor/result cards → `DisclosurePanel` (`StepEditor.kt:52`, `StepResultCard.kt:61`), menu selection → `MenuSelection` (`StepEditor.kt:116`), step pulse gated + `Spatial.bob()` (`StepProgressIndicator.kt:65-73`) |
| Personas | same pattern; snackbar → `ExpressiveSnackbar` (`PersonaStudioScreen.kt:126`) | `PersonaCard.kt:83,88` press on `Press.Scale`/`Press.Shape`; onboarding card `ExpressiveShapes.Card` (`PersonaStudioScreen.kt:266`) | n/a (grid always has the tip card) | `PersonaPickerSheet.kt:65`, `PersonaEditorSheet.kt:73` `SettingsSheetShape` | `ExpressiveFab` | `animateItem` → `ListItem.*` (`:170-172`), stock `Snackbar` → shared, sheets raw 28dp top → settings sheet contour |
| Search (home overlay) | `ChatSearchBar` | `SearchResultItem.kt:75,85` `Press.Scale`/`Press.Tint` (was legacy `MaterialChatMotion.Springs.ScalePress` + bare `spring()`) | — | — | — | match disclosure `:190-193` → `Disclosure.*`; clear-button fade `SearchBar.kt:94-95` → `Disclosure.FadeIn/FadeOut` |
| Onboarding | none (hero) | hero/Codex card `ExpressiveShapes.Sheet` (`OnboardingScreen.kt:220,265`), provider/assistive cards `ExpressiveShapes.Card` (`:371,426`) | — | — | — | raw 32/28dp rounds → tokens |
| Explore | `TopAppBar` + title | feature tiles `ExpressiveShapes.Card` (`ExploreScreen.kt:405`), icon tile `Tile` (`:428`) | — | — | — | content surface → `ScreenContainer`; backdrop spin gated and on `decorativeSpin(BackdropSpin)` (`:226-233`); eyebrow/description text 78 %/72 % alpha → full-opacity on* role (`:441,451,458`) |
| Insights | `CenterAlignedTopAppBar` (`InsightsTopBar.kt`) | five cards `ExpressiveShapes.Card` (`StatCard.kt:51`, `ActivityHeatmap.kt:67`, `ModelComparisonCard.kt:65`, `ModelUsageChart.kt:80`, `ResponseTimeChart.kt:64`) | — | — | — | content surface `InsightsScreen.kt:82` → `ScreenContainer`; `StatCardLabel/Value` slots added |
| Arena | `TopAppBar`; leaderboard `TopAppBar` | battle card `ExpressiveShapes.Card` (`ArenaBattleView.kt:134`), leaderboard rows `Card` (`ArenaLeaderboard.kt:166`) | — | snackbar `CustomShapes.Snackbar` (`ArenaScreen.kt:135`) | — | content surface `:148` → `ScreenContainer`; content-size/enter springs → `contentSize()`/`Effects.reveal()`/`Spatial.container()` (`:226,256-290`); reveal tweens → `Disclosure.FadeIn/FadeOut` + `Spatial.expand()` (`ArenaBattleView.kt:155-160`); leaderboard `animateItem` → `ListItem.*` (`:134-138`) |
| Canvas | `TopAppBar` (`CanvasTopBar.kt`) | refinement input `ExpressiveShapes.Card` (`CanvasRefinementInput.kt:88`) | — | — | — | 24dp round → Card |
| Mind map | `CenterAlignedTopAppBar` | tooltip `DisclosurePanel` (`NodePreviewTooltip.kt:46`) | — | — | — | content surface `MindMapScreen.kt:56` → `ScreenContainer`; node glow gated + `Durations.StreamingIndicator` (`MindMapCanvas.kt:128-136`) |

## What changed and why (file:line at HEAD)

### Part 1 — shared components
- `ui/components/ExpressiveLoadingIndicator.kt:79-98` — the six raw `tween(` durations named in a private `IndicatorCycle` object (`MorphRotationMillis` 4000, `MorphShapeMillis` 3000, `ArcRotationMillis` 1332 = M3 circular spec, `ArcSweepMillis` = half, `LinearPassMillis`, `LinearWidthMillis`) and consumed at `:133,148,325,335,441,451`. Every loop already behind `rememberParticleMotionGate` (W1); confirmed each null-transition branch holds a static frame (rotation 0, morph 0, sweep 0.5, bar centred). No Motion token models an indicator cycle; the W2 theme `decorativeSpin` is for ornaments, not indicators, so these stay as named constants (see Needs another owner).
- `ui/components/ExpressiveSurfaces.kt:114` — `ExpressiveContentSurface` 32dp top round → `ExpressiveShapes.ScreenContainer` (28dp, same as home list and settings). `:285` `ExpressiveEmptyState` now delegates to `HomeEmptyState` (shape cluster, headline, supporting line, one action, `accent` param) so every secondary empty state is the home one. `:306` `ExpressiveListItemContainer` 24dp → `CustomShapes.ConversationItem`.
- `ui/components/HomeSettingsContrast.kt:122-126` — `StatCardLabel` / `StatCardValue` slots (insights cards on `surfaceContainerHigh`). Test `newSharedComponentSlotsArePresent` asserts them.

### Part 2a/2b/2c — secondary screens: see inventory table.

### Theme migrations (from `docs/reports/pass-w2-theme.md`)
- `ui/screens/conversations/ConversationsScreen.kt:517` — header gear spin `infiniteRepeatable(tween(16_000))` → `ExpressiveMotion.Spatial.decorativeSpin()`; gate kept (`:510`).
- `ui/screens/conversations/HomeEmptyState.kt:166` — cluster drift → `Spatial.decorativeSpin(MaterialChatMotion.Durations.BackdropSpin)`; gate kept (`:162`).
- `ui/screens/explore/ExploreScreen.kt:226-233` — hero backdrop spin was ungated; now `if (rememberParticleMotionGate().motionAllowed)` with `decorativeSpin(BackdropSpin)`, 0f otherwise.
- `ui/screens/workflows/WorkflowExecutionScreen.kt:174` — `AlertDialog` 24dp round → `ExpressiveShapes.Dialog`.
- `ui/components/ExpressiveSnackbar.kt:56-62` — action button `ShapeMorphing.buttonShape(..., animationSpec = ExpressiveMotion.Press.Shape)`: opted in. Feel change: 0.6/500 → 0.6/800, settles ~21 % sooner, matching every other pressable on home/settings. Unverified on a device.
- Sheets on raw 28dp tops in my files (`PersonaPickerSheet.kt:65`, `PersonaEditorSheet.kt:73`, `AddBookmarkSheet.kt:79`) → `SettingsSheetShape` (32dp squircle top corners, the settings `AddProviderSheet` contour). `CustomShapes.BottomSheet` was not used because the settings sheet is the one already on home/settings.
- Extra continuous loops found and gated the same way: `MindMapCanvas.kt:128` node glow, `StepProgressIndicator.kt:65` step pulse (on `Spatial.bob()`).

### Part 3 — home
- `ui/screens/conversations/HomeListInsets.kt:43-61` — `snackbarClearance(navigationBarInset)` = inset + `ToolbarBottomMargin` + `ToolbarHeight` (no `RowGap`: the snackbar carries its own 12dp padding) and the composable `snackbarBottomClearance()`. `ConversationsScreen.kt:270-276` — `SnackbarHost` padded by it, so a snackbar floats above the MainActivity-overlaid toolbar instead of under it. Two new JVM tests in `HomeListInsetsTest`.
- `+` FAB vs list bottom inset: `listBottomClearance()` already feeds every home list (`:1140,1224`) and the fast-scroll bar; left as is.
- Decorative header tint wash / empty-state drift: both on `decorativeSpin` now (above).

### Part 4 — settings
- `InteractionSettingsScreen.kt:147`, `ModelAssignmentsScreen.kt:206,246`, `OnDeviceModelsScreen.kt:228,258,318,398,457` — raw 24/28dp rounds and `CustomShapes.ProviderCard` → `ExpressiveShapes.Card`, the same squircle the main settings list cards use (23 sites in `SettingsScreen.kt`).
- Settings content surface vs collapsed `LargeTopAppBar`: both already `surfaceContainer` (`SettingsScreen.kt:443-446`) over `ScreenContainer` on `surfaceContainerLow` (`:714-720`), identical to home. The seam is the deliberate 28dp contour; nothing changed, needs a device look (below).

### Contrast
- Resolver pass over the new slots: `StatCardValue` is drawn with `onSurface` (`StatCard.kt:70`), which reads everywhere, so the slot prefers `onSurface` (an earlier draft on `primary` failed 4.07/4.13 on two light palettes and was corrected before commit). Speculative slots for fills no call site draws (`EmptyHeroOnSurfaceLow`, `MemoryKindChip`, `SettingsBadgeOnTertiary`, `ExploreTileOn*`) were pruned rather than asserted.
- Explore tile eyebrow/description now full-opacity role (`ExploreScreen.kt:441,451,458`) per the theme audit's alpha-on-text ban; previously 78 %/72 %.

## Deliberately left alone
- Insights data-reveal counters/bars (`StatCard.kt:43`, `ActivityHeatmap.kt:56`, `ModelComparisonCard.kt:53`, `ModelUsageChart.kt:72`, `ResponseTimeChart.kt:53`): very slow springs (stiffness 40–60) that animate a count-up on data arrival; no Motion token models that and replacing with `Spatial.slow()` (200) would make them snap. Listed for the theme owner.
- Text-field shapes (`RoundedCornerShape(16/20/24.dp)` on `OutlinedTextField`s in bookmarks/personas/arena/memories search) are M3 text-field shapes, not cards; kept, as in W1.
- Small badges at 8dp (`StepResultCard.kt:78`, `PersonaCard.kt:196`), onboarding decorative blob 44dp and avatar 24dp tiles: component-specific, not in the card/dialog/sheet classes.
- `WorkflowsScreen.kt:105` "New Workflow", `PersonaStudioScreen.kt:121` "New Persona", screen titles "Knowledge Base" / "Generated Images" / "Persona Studio" / "Mind Map" / "Arena Leaderboard": proper-noun feature names; sentence case was applied in W1 to settings labels only, and changing these risks the explore tile ↔ screen title pairing. Flagged for the orchestrator rather than changed unilaterally.
- Swipe archive/delete order, preference keys, nav graph, launcher icon.
- `assistant/**`, `ui/screens/chat/**`, `ui/theme/**`, `MarkdownText.kt`, `MathWebViewTouch.kt`, `TapThroughScroll.kt`, `SmoothStreamingText.kt`, `Assistant*SettingsPanel.kt`, `ImageViewerDialog.kt` (chat pass).

## Needs another owner
- Theme: an indicator-cycle token family (`IndicatorCycle` in `ExpressiveLoadingIndicator.kt:79-98` lists the values) and a slow data-reveal spring for the insights counters.
- Theme/orchestrator: `CustomShapes.ProviderCard` (28dp round) now has no consumer in my files; candidate for deprecation to `ExpressiveShapes.Card` like `CustomShapes.Dialog`.
- Chat: `ImageViewerDialog.kt` — nothing; it is on main's version.

## Not verifiable without a device
- Snackbar position above the floating toolbar on home (arithmetic tested; placement not rendered).
- Snackbar action press morph on `Press.Shape` (~21 % faster settle).
- Persona card / search row press morph on `Press.*` (were `Spatial.scale`/`shapeMorph`, legacy `ScalePress`).
- Static frames of the newly gated explore backdrop spin, mind-map glow and step pulse under battery saver / animator scale 0.
- Visual match of the secondary-screen empty states now drawn by `HomeEmptyState` (cluster size on narrower content surfaces).
- Sheet contour change on persona/bookmark sheets (28dp round top → 32dp squircle top).
- Settings content surface seam against the collapsed `LargeTopAppBar`.
- All contrast claims are computed over 39 generated schemes; dynamic colour on real wallpaper may differ.
