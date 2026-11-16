# MaterialChat — Pass 2 report: home + settings + shared sheets (M3 Expressive)

Branch `feat/p2-home-settings` (worktree `.worktrees/p2-home-settings`), based on main `7f56f27` (v2.37.0).
Material 3 `1.5.0-alpha15` (`material3-android`), `androidx.graphics.shapes` 1.1.0. No emulator or device was used; everything below is compile + JVM evidence only.

## Commits

| Hash | Part | Summary |
|---|---|---|
| `d1b140c` | research | `docs/m3e-pass2-notes.md` — reference notes (catalog, 1.5 alpha release notes, M3E shape/motion guidelines, Read You / Metrolist / VerveDo / PixelPlayer) |
| `23b7608` | 1 — home | per-position swipe action shapes, row tile press morph, group chevron spring, new shape/motion tokens, JVM test |
| `aeecc49` | 3 — shared | `ExpressiveSlider`, spring-thumb `ExpressiveSwitch`, `dialogEntrance`, `ExpressiveSnackbar`; wired into home dialogs/snackbar |
| `2bbfc82` | 2 — settings | `SplitButton` on provider cards, expressive sliders, dialog entrance, `LoadingIndicator`/`LinearWavyProgressIndicator`, tonal snackbars |
| (this commit) | report | `dialogEntrance` now uses the `dialogEnter()` token; this report |

## New theme tokens (added only, nothing existing changed)

- `ui/theme/Shapes.kt:100` `ExpressiveShapes.SwipeActionPosition { Outer=24dp, Inner=10dp, Armed=28dp }`
- `ui/theme/Shapes.kt:112` `ExpressiveShapes.RowTile { Resting=18dp, Pressed=24dp }`
- `ui/theme/Motion.kt` `ExpressiveMotion.Spatial.thresholdPop()` (0.45/700), `disclosurePulse()` (0.55/650), `dialogEnter()` (0.7/520)

## Part 1 — Home / conversation list

| Change | Where |
|---|---|
| Per-position corner radii for swipe action pills: the edge facing the screen edge keeps 24dp, the edge shared with the sibling action is 10dp so Archive+Delete read as one connected group; every corner springs to 28dp once an action is armed (shape-morph spring). Pure function `swipeActionCorners(slot, outer, inner)` | `conversations/components/SwipeActionShapes.kt:28`; applied in `SwipeToDeleteBox.kt:412-417` |
| Icon pops on the `thresholdPop()` spring when the delete/edit threshold is crossed; threshold haptic kept (`SWIPE_THRESHOLD` at `SwipeToDeleteBox.kt:143`, reveal tick at `:146`, new `SEGMENT_TICK` on edit arm at `:150`). Existing order (Archive, then Delete; right swipe = Edit), release-to-delete and all labels/`contentDescription`s unchanged | `SwipeToDeleteBox.kt` |
| Row leading squircle tile morphs 18dp → 24dp while the row is pressed (shape-morph spring), on top of the existing per-position corner press morph + 0.98 scale | `ConversationItem.kt:98-103`, `ExpandableConversationGroup.kt:133-138` |
| Group disclosure: chevron rotation stays on the spatial spring, chevron tile now pulses (scale, `disclosurePulse()`) when the group opens/closes; branch list keeps `ExpressiveMotion.Disclosure` expand/shrink physics | `ExpandableConversationGroup.kt:111-129` |
| Rename and Chat-info dialogs get the `dialogEntrance()` spatial scale+fade; snackbar host uses `ExpressiveSnackbar` | `ConversationsScreen.kt:274, 334, 867` |
| Regression test for the pure corner function (only / leading / trailing / armed) | `test/.../conversations/SwipeActionShapesTest.kt` (4 tests) |

The one-line date on the row (`RowDateLabel`, `softWrap=false`, weighted model-name sibling) was not touched in either row composable; the metadata `Row` structure is unchanged.

## Part 2 — Settings

| Change | Where |
|---|---|
| Provider card actions: `SplitButtonLayout` with a tonal leading button (primary action: "Set Active", or "Test" when already active) and a tonal trailing dropdown button whose chevron rotates on the spatial spring; the dropdown holds the remaining actions (Test / Delete) so every previous action is still reachable. `contentDescription = "More provider actions"` added | `settings/components/ProviderCard.kt:226-270` |
| Font-size slider and web-search max-results slider use `ExpressiveSlider` (M3 1.5 wide track + stop indicator from `SliderDefaults.Track`, thumb grows 44→52dp while pressed/dragged on the shape-morph spring). Per-stop `SEGMENT_TICK` haptics kept | `SettingsScreen.kt:2065, 3423`; `AssistantVoiceSettingsPanel.kt:169, 217` |
| Switch thumb icon (check/close) now swaps with a spatial scale-in and compresses while pressed, instead of a 100 ms tween crossfade; haptic kept | `ui/components/ExpressiveSwitch.kt` |
| `AlertDialog`s (backup password, restore, delete provider, delete model, voice configuration) keep the 28dp squircle and gain the `dialogEntrance()` spatial scale + effects fade | `SettingsScreen.kt:2703, 2754, 2804`; `OnDeviceModelsScreen.kt:191`; `AssistantVoiceSettingsPanel.kt:66` |
| `M3ExpressiveCircularProgress` (custom canvas arc) → M3E `LoadingIndicator` morphing-shape loader in the model refresh button, model dropdown loading row, update check/download states, model-assignments refresh | `SettingsScreen.kt:2323, 2431, 3026, 3032`; `ModelAssignmentsScreen.kt:438` |
| `LinearProgressIndicator` → `LinearWavyProgressIndicator` (determinate during download, indeterminate during verify/install) | `AssistantVoiceSettingsPanel.kt:195, 198`; `OnDeviceModelsScreen.kt:530, 538` |
| Snackbar hosts use `ExpressiveSnackbar` | `SettingsScreen.kt:450`; `OnDeviceModelsScreen.kt:133` |

`SettingsScreen.kt` got a file-level `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` for `LoadingIndicator`.

## Part 3 — Shared components (`ui/components/**`, non-chat)

- `ExpressiveSlider.kt` — wraps the M3 1.5 `Slider(thumb=, track=)` overload; thumb is `SliderDefaults.Thumb(thumbSize = DpSize(4dp, animated height))`; track is the library default (wide track, gap, stop indicator).
- `ExpressiveDialog.kt` — `Modifier.dialogEntrance()`: `Animatable` scale 0.92→1 on `Spatial.dialogEnter()`, alpha 0→1 on `Effects.reveal()`, applied through `graphicsLayer` (no outside-bounds drawing, so API 26 safe).
- `ExpressiveSnackbar.kt` — `inverseSurface` tonal container, 24dp shape, action `TextButton` morphs 20→12dp on press via `ShapeMorphing.buttonShape`, haptic on action, dismiss icon honoured.
- `ExpressiveSwitch.kt` — as above.

## material3 APIs used and where verified (javap on `material3-android/1.5.0-alpha15/.../material3.aar` → `classes.jar`)

| API | AAR class |
|---|---|
| `SplitButtonLayout` | `SplitButtonKt` (`SplitButtonLayout--jt2gSs`) |
| `SplitButtonDefaults.TonalLeadingButton` / `TonalTrailingButton(checked, onCheckedChange, …)` / `TrailingIconSize` | `SplitButtonDefaults` |
| `LoadingIndicator(modifier, color)` | `LoadingIndicatorKt` (`LoadingIndicator-3IgeMak`) |
| `LinearWavyProgressIndicator` (determinate `progress: () -> Float` and indeterminate) | `WavyProgressIndicatorKt` (`-1YwxWKA`, `-hvuEXSk`) |
| `Slider(value, onValueChange, …, thumb, track, valueRange)` | `SliderKt` (second `Slider` overload) |
| `SliderDefaults.Thumb(interactionSource, modifier, colors, enabled, thumbSize)` | `SliderDefaults` (`Thumb-9LiSoMs`, `long` = packed `DpSize`) |
| `SliderDefaults.Track` wide track + `drawStopIndicator` / `TrackStopIndicatorSize` | `SliderDefaults` |
| `ListItemDefaults.segmentedShapes/segmentedColors/SegmentedGap` (already in use, re-checked) | `ListItemDefaults`, `ListItemShapes` |
| `ButtonGroup`, `ButtonGroupDefaults.connected*ButtonShapes`, `ToggleButton` (already in use) | `ButtonGroupKt`, `ButtonGroupDefaults`, `ToggleButtonKt` |
| `MaterialShapes.*.toShape()` (already in use) | `MaterialShapes`, `ShapesKt` |
| `androidx.graphics.shapes.Morph` — verified present (`Morph.class`, `MorphKt`) but **not used**: no owned surface needed polygon-to-polygon morphing; all morphs here are corner-radius springs on `RoundedCornerShape`/`SquircleShape` |
| `HorizontalFloatingToolbar`, `FloatingActionButtonMenu` — verified present (`FloatingToolbarKt`, `FloatingActionButtonMenuKt`) but **not used** (see below) |

## Verification

- `:app:compileGithubDebugKotlin` — EXIT 0 after each part.
- `:app:testGithubDebugUnitTest` — **528 tests, 0 failures, 0 errors, 0 skipped** (tallied from the JUnit XML in `app/build/test-results/testGithubDebugUnitTest/`). Includes the 4 new `SwipeActionShapesTest` cases.
- `:app:compileGithubDebugAndroidTestKotlin` — EXIT 0.
- `./gradlew --stop` run at the end.
- No `testTag`, `contentDescription`, test, `scripts/`, or `.github/workflows/` content was removed or weakened. One `contentDescription` was added ("More provider actions").

## Not verifiable without a device (hypotheses, not confirmed behaviour)

- Visible feel of every spring (swipe pill corner morph, icon threshold pop, chevron pulse, tile press morph, switch thumb spring, slider thumb growth, dialog entrance). Compile proves the wiring, not the motion.
- `SplitButtonLayout` sizing inside the provider card at small widths; the dropdown anchoring under the trailing button.
- `LoadingIndicator` / `LinearWavyProgressIndicator` rendering on API 26–30 (they are pure Compose drawing, no shader/blur, so they should be fine, but unverified).
- `dialogEntrance` on API 26: `graphicsLayer` scale stays inside bounds so the known old-API clip should not bite; unverified.
- Swipe archive/delete order and the one-line row date were not re-tested on a device; only the untouched layout structure guarantees them.

## Deliberately not changed, and why

- **Floating `+` / `HorizontalFloatingToolbar` / `FloatingActionButtonMenu`**: the home toolbar and its "+" are composed in `MainActivity` (overlay Box, `navigationBarsPadding()`), which is outside the owned directories. The list's bottom clearance (`HomeListInsets`, with its JVM test) already accounts for it and still passes. Replacing the toolbar would have meant editing MainActivity.
- **Search field / filter `ButtonGroup`**: `ConversationFilterBar` already is a connected `ButtonGroup` of `ToggleButton`s with `animateWidth`; `ChatSearchBar` lives in `screens/search/**` (not owned).
- **Pull-to-refresh**: not present on any owned screen; nothing to convert. The existing `LoadingContent`/search loading already used `LoadingIndicator`.
- **Settings group per-position shapes / pressed morph**: `SettingsToggleRow`/`SettingsNavRow`/provider-type picker already use `ListItemDefaults.segmentedShapes(index, count)` which carries first/middle/last and pressed shapes; adding a second morph on top would double-animate.
- **Two standalone title-toggle cards** (`AiGeneratedTitlesToggle`, `PreferOnDeviceTitleModelToggle` in `SettingsScreen.kt:2126/2178`): left as cards. Folding them into a `SettingsGroup` is a layout change in a 3.5k-line file I could not visually check, and they already use `ExpressiveSwitch` (so they get the new thumb spring).
- **Bottom sheets**: `AddProviderSheet` already has the drag handle, the top-rounded 32dp `SettingsSheetShape`, `animateContentSize(Spatial.contentSize())`, and a segmented provider-type picker. The unused `CircularProgressIndicator` import there was left alone (import only, no call site).
- **`M3ExpressiveCircularProgress` itself** (`ExpressiveLoadingIndicator.kt`) was kept because chat files (not owned) may call it; only owned call sites were migrated.
- **Snackbar container**: kept `inverseSurface` (M3 spec) rather than a tonal `primaryContainer`, since the existing hosts already used inverse colours and changing the colour role would alter readability I cannot check.
- No existing token values in `Shapes.kt` / `Motion.kt` were modified; only additions.
- No italic text introduced anywhere.

## Incident during the pass

The first 10-block edit batch on `SettingsScreen.kt` left the file empty on disk (the harness reported success). Detected by the next compile (`SettingsScreen` unresolved in `MaterialChatNavHost`), restored with `git checkout -- SettingsScreen.kt`, and the identical batch re-applied; the resulting diff was the intended 14-line net change (3489 lines) and compiled. Worth knowing for anyone editing that file with large batched replacements.
