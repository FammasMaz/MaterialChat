# Pass W3 — Home + Settings + drawer screens: report

Branch `pass/w3-home-settings`. Started on main `ba3f38b` (v2.41.2); rebased twice onto origin/main as steered: first onto `5d1d17a` (W3 theme merge `9f95be2`), then onto `be82f9c` (W3 chat merge `8c17a8d`). Merge-base at the time of this report: `be82f9c`. origin/main has since moved to `2081ed7` (W3 assistant merge, `assistant/**` + one assistant test only; disjoint from every path on this branch — verified with `git diff be82f9c` restricted to owned paths). Not pushed.

## Commits (oldest first)

| Commit | Part |
|---|---|
| `f7303ec` | refactor(ui): W3 part 4 — shape tokens and W3 theme migrations across home/settings/drawer (+ group-row tile parity) |
| `a64ad14` | refactor(settings): W3 part 1 — AddProviderSheet on the shared SheetHeader/SheetGutter; SettingsSheetShape deleted |
| `a99dec1` | fix(ui): W3 part 2 — state walk fixes across home/settings/drawer |
| `bd46ce8` | refactor(ui): W3 part 4 tail — onboarding icon tiles and ad banner on Tile, fast-scroll date label on Snackbar |
| (this file) | docs: W3 home/settings REPORT.md |

## Gates (at `bd46ce8`)

- `:app:compileGithubDebugKotlin` rc=0, `:app:compileGithubDebugAndroidTestKotlin` rc=0 — run before every commit.
- `:app:testGithubDebugUnitTest`: **652 tests, 0 failures, 0 errors, 0 skipped** (`HomeSettingsContrastTest` 6, `HomeListInsetsTest` 6). No test file touched; no androidTest added; `scripts/`, `.github/workflows/` untouched.
- `git diff be82f9c` over `testTag(` / `contentDescription =` removals: one removed line, `contentDescription = "Close"` inside the deleted private `SheetHeader` in `AddProviderSheet.kt`; the shared `ui/components/SheetHeader.kt` draws the same icon with the same `contentDescription = "Close"`. No `FontStyle.Italic` in owned files (grep = 0).
- Preference keys, nav graph, swipe archive/delete order, launcher icon: untouched.

## What was audited

Every file under the ownership list was read in full by three read-only audit passes (home list + search + shared components; settings + its four component files; the ten drawer screens + onboarding), each producing file:line findings for hierarchy, crush/overlap at 360dp and font scale 1.3/0.85, RTL, colour resolution, motion tokens and copy case. Those findings were triaged into (a) fixed in part 2, (b) deliberately left, (c) needs another owner, (d) device-only. The layout math for part 3 was done by hand from the current source.

## Part 1 — shared sheet header (`a64ad14`)

- `settings/components/AddProviderSheet.kt:65-66` imports `SheetGutter`/`SheetHeader` from `ui/components`; `:174` content gutter `padding(horizontal = SheetGutter)` (was raw 24dp, same value); `:182` calls the shared `SheetHeader(title, onClose = onDismiss)`. The private `SheetHeader` composable and its `IconButton`/`HapticPattern`/`rememberHapticFeedback` imports are deleted. No second copy exists.
- `settings/components/SettingsExpressive.kt`: `SettingsSheetShape` deleted (it had been made a deprecated delegate to `ExpressiveShapes.BottomSheet` in `f7303ec` while chat still imported it). Grep across `app/src` after chat's merge: zero consumers; the only remaining mention is a KDoc line in `ui/theme/Shapes.kt:107` (theme owner's file, see Needs another owner). The polygon/outline imports that only served the old object are removed.

## Part 2 — state walk (`a99dec1`)

### State table

| Surface / state | How it is drawn at HEAD | Found | Fixed (file:line) |
|---|---|---|---|
| Home empty | `HomeEmptyState` cluster + headline + action | "Go to Settings" title case | `ConversationsScreen.kt:1041,1069` → "Go to settings" |
| Home 1 / 200 rows | `ExpandableConversationGroup` per item, segmented list, fast-scroll bar | group parent tile 44dp / 12dp padding vs plain row 48dp / 14dp → 68dp vs 76dp row heights, text start 76 vs 78dp in one list | `ExpandableConversationGroup.kt:198,205,220,225` → 14dp padding, 48dp tile, 26dp glyph, 14dp gap (parity with `ConversationItem.kt:147-162`) |
| Grouped / branched | parent + animated branch column | no fix needed beyond parity | — |
| Archived / temporary | archive filter; chip + date prefix | left (see below) | — |
| Search active | `AnimatedContent` top bar swap, results list | transition on framework defaults; results reserved a fixed 88dp bottom gap unlike the home lists | `ConversationsScreen.kt:238` Disclosure/Spatial tokens; `:1145` `listBottomClearance()` |
| Selection mode | not present in owned code (long-press opens chat info) | n/a | — |
| Swipe archive/delete | `SwipeToDeleteBox` fixed 84dp actions, `maxLines = 1` label | no overflow handling | `SwipeToDeleteBox.kt:466` `TextOverflow.Ellipsis` (order, widths, haptics untouched) |
| Snackbar + undo | `ExpressiveSnackbar` above toolbar via `snackbarBottomClearance()` | fine | — |
| FAB over last row | `listBottomClearance()` on every home list | fine | — |
| Pull-to-refresh | none exists | n/a | — |
| Update banner | available/download/install | wavy progress ungated; "Install Now" | `UpdateBanner.kt:252-264` amplitude 0 while `!motionAllowed`; `:341` sentence case |
| Settings sections | always present (no collapse control); web-search/assistant toggles gate children | header text columns unweighted beside 24dp icon → trailing control starved at 360dp/1.3 | `SettingsScreen.kt:1300,1417,1477,1535,1654,1760,1865,2016,2997` `Column(Modifier.weight(1f))`; same in `InteractionSettingsScreen.kt:162`, `ModelAssignmentsScreen.kt:261,294,347` |
| Settings copy | 20+ title-case labels | — | `SettingsScreen.kt:1168,1233,1268,1302,1479,1496-1497,1555,1662,2021,2798,3081,3226,3297,3321,3350-3366,3379,3409`, `AddProviderSheet.kt:183,193,245,278,328,441,582`, `OnDeviceModelsScreen.kt:589` → sentence case; acronyms (API, URL, SearXNG, Exa, AI) kept |
| Bubble / control style pickers | fixed 128/124dp tiles, `maxLines = 1` | no overflow | `SettingsScreen.kt:1825,1921` `TextOverflow.Ellipsis` |
| Provider card active/inactive/no key | palette-resolved slots (`ProviderCard.kt:340`) | fine; rejected/expired key has no persistent card state (left) | — |
| Model assignments long names | weighted trigger text wraps | fine | — |
| On-device downloading/failed/installed | status + wavy progress | fine | — |
| Theme pickers × palettes | contrast slots unchanged; every resolved slot passes AA on all 39 schemes (`HomeSettingsContrastTest`) | — | — |
| Memories / bookmarks / personas / workflows / images / insights / arena / canvas / mind map / explore — copy | title-case titles and buttons | — | `BookmarksScreen.kt:171`, `AddBookmarkSheet.kt:89,266`, `PersonaStudioScreen.kt:121,256,281`, `PersonaEditorSheet.kt:87,136,149,189`, `PersonaPickerSheet.kt:76,114`, `WorkflowsScreen.kt:105,161`, `WorkflowBuilderScreen.kt:76,125-126,183`, `WorkflowExecutionScreen.kt:280,509,561`, `StepEditor.kt:112`, `InsightsScreen.kt:204,210`, `ModelComparisonCard.kt:75`, `ModelUsageChart.kt:90`, `ResponseTimeChart.kt:74`, `ArenaScreen.kt:296`, `ArenaLeaderboard.kt:73,245` ("ELO" → "Elo": a name, not an acronym), `ArenaPromptInput.kt:58` ("both models" → "the models"; 2–4 contenders), `CanvasTopBar.kt:58-61`, `SearchResultItem.kt:145`, `ConversationsViewModel.kt:467` |
| Drawer — alpha on text/icons (theme audit ban) | `onSurfaceVariant@0.6/0.7`, `primary@0.8`, `error@0.7`, `accentOnContainer@0.65`, `onPrimaryContainer@0.82`, native canvas text @0.7 | — | `BookmarkCard.kt:139,183,201,215`, `BookmarkSearchBar.kt:49,59`, `StepEditor.kt:99,134` (also dropped the hard-coded `11.sp`), `ArenaModelPicker.kt:91,116`, `ArenaBattleView.kt:113,176`, `ArenaLeaderboard.kt:99,114,245`, `ExploreScreen.kt:336`, `MindMapCanvas.kt:523` → full-opacity role |
| Drawer — fixed heights | onboarding provider cards 88dp, sign-in 72dp, skip 48dp; explore hero 196dp, tiles 144/156dp; workflow step badge 26dp `size` | clipping at 1.3 | `OnboardingScreen.kt:195,331,381`, `ExploreScreen.kt:259,411` → `heightIn(min = …)`; `WorkflowExecutionScreen.kt:494,546` → `defaultMinSize` |
| Drawer — motion | arena enter/exit, explore card entrance, onboarding error card on framework defaults; arena codename scramble (700ms loop) and leaderboard wavy bar ungated | — | `ArenaScreen.kt:260,277`, `ExploreScreen.kt:376`, `OnboardingScreen.kt:154-157` → Disclosure/Spatial tokens; `ArenaBattleView.kt:226-227` scramble only while `motionAllowed`; `ArenaLeaderboard.kt:216-229` amplitude 0 while gated |
| Drawer — overflow | starter chips `maxLines = 2` no overflow | — | `ConversationStarterChips.kt:51` |
| Font scale 0.85 / 1.3, 360dp, RTL | reasoned from constraints only | see device-only list | — |

### Contrast
No new fill was introduced; every colour change moves text/icons from an alpha-faded role to the full-opacity role on the same fill, which is already covered by existing slots or is a role pair (`on*` on its container). No slot added; `HomeSettingsContrastTest` still 6 tests, all green on every scheme.

## Part 3 — settings contour and tile parity (layout math)

- **Content contour vs collapsed `LargeTopAppBar`.** `SettingsScreen.kt:425` `exitUntilCollapsedScrollBehavior`; `:428` Scaffold `containerColor = surfaceContainer`; `:440-442` top bar `containerColor` and `scrolledContainerColor` both `surfaceContainer`; `:712` content Box `padding(top = paddingValues.calculateTopPadding())`; `:718` content Surface `ScreenContainer` (28dp top corners) on `surfaceContainerLow`. Collapsed: the bar's bottom edge y equals the Box's top; the Surface's 28dp corner reveals `surfaceContainer` (the Scaffold backdrop, identical to the bar fill) in the two corner wedges — so the visible seam is bar-colour → rounded `surfaceContainerLow`, with no colour discontinuity between bar and backdrop. Home is byte-for-byte the same construction (`ConversationsScreen.kt:233,246,759-760`). Nothing is provably wrong; what remains device-only is whether the 28dp wedge reads as intended against a collapsed 64dp bar (vs the expanded 152dp one) — the geometry is identical in both states.
- **Group-tile 48dp parity.** Proven drift and fixed (table above): `ExpandableConversationGroup` parent row was 44dp tile + 12dp vertical padding = 68dp min row vs `ConversationItem` 48dp + 14dp = 76dp; glyph 24 vs 26dp; gap 16 vs 14dp. Now identical. The 0.7-alpha `primaryContainer` on the group tile/chips (`:207,:253,:290`) is deliberate (W1) and its slots `GroupTileIcon`/`GroupProviderChip`/`BranchChip` model the composite; unchanged.
- Settings leading tiles remain intentionally three sizes: section cookie 44dp/22dp (`SettingsExpressive.kt:106,115`), nav row 40dp/22dp (`:263,:272`), provider card 48dp/26dp (`ProviderCard.kt:293,310`). These are three different row classes (landmark header, nav list row, card), not one list, so no parity requirement applies; recorded here so the next wave does not re-audit it.

## Part 4 — shape drift (`f7303ec`, `bd46ce8`) and W3 theme migrations (`f7303ec`)

| File:line | Was | Now |
|---|---|---|
| `ModelAssignmentsScreen.kt:284` | raw 16dp status tile | `ExpressiveShapes.MenuSelection` |
| `ModelAssignmentsScreen.kt:468,527,560,656`, `SettingsScreen.kt:2345,2377,2595,3357,3383`, `PersonaEditorSheet.kt:105,113,126,139,151,192`, `AddBookmarkSheet.kt:195,247` | raw 16/20dp text fields & dropdown triggers | `MaterialTheme.shapes.medium` (16dp; the app's text-field token) |
| `SettingsScreen.kt:3389` | raw 18dp inset note surface | `ExpressiveShapes.DisclosurePanel` |
| `BookmarkCard.kt:107`, `WorkflowCard.kt:107`, `ConversationStarterChips.kt:54`, `PersonaEditorSheet.kt:178` | raw 16/20dp badges/chips | `CustomShapes.Pill` |
| `BookmarkSearchBar.kt:66`, `MemoriesScreen.kt:240` | raw 24dp search fields | `CustomShapes.SearchBar` |
| `ArenaPromptInput.kt:60` | raw 28dp prompt field | `CustomShapes.MessageInputContainer` |
| `PersonaPickerSheet.kt:90,163` | raw 20dp rows | `ExpressiveShapes.MenuSelection` |
| `OnboardingScreen.kt:293,395`, `AdMobBannerAd.kt:52` | raw 24/18dp tiles | `ExpressiveShapes.Tile` |
| `ExpressiveFastScrollBar.kt:483` | raw 22dp date label | `CustomShapes.Snackbar` (24dp) |
| `PersonaPickerSheet.kt:65`, `PersonaEditorSheet.kt:74`, `AddBookmarkSheet.kt:79`, `AddProviderSheet.kt:119`, `OnDeviceModelsScreen.kt:141` | `SettingsSheetShape` / `CustomShapes.BottomSheet` | `ExpressiveShapes.BottomSheet` (geometry-identical except OnDeviceModels: round 32dp top → G2 32dp top) |
| `SystemPromptField.kt:102` | `CustomShapes.ProviderCard` | `ExpressiveShapes.Card` (28dp round → 28dp squircle) |
| `ExpressiveLoadingIndicator.kt:111,126,303,313,419,429` | private `IndicatorCycle` | `MaterialChatMotion.Durations.Indicator.*`; object deleted (value-identical) |
| `StatCard.kt:43`, `ModelUsageChart.kt:72` | `spring(0.8f, 50f)` | `Spatial.dataReveal()` (identical) |
| `ActivityHeatmap.kt:56` | `spring(0.9f, 40f)` | `dataReveal()` — slightly faster, slightly less damped |
| `ModelComparisonCard.kt:53`, `ResponseTimeChart.kt:53` | `spring(0.7f, 60f)` | `dataReveal()` — slightly slower, less bounce |

Remaining raw `RoundedCornerShape(16–32dp)` in owned files: `SettingsScreen.kt:1971-1982` only — the bubble-style preview sampler, which must draw each style's own geometry (deliberate exemption; W3 theme report agrees).

## Deliberately left alone
- Proper-noun screen titles: "Knowledge Base", "Generated Images", "Persona Studio", "Mind Map", "Model Arena", "Arena Leaderboard", "Total Messages" stat label — feature names paired with explore tiles (W2 flagged to orchestrator; unchanged pending a decision). "Start Battle" is a contentDescription (not renamed per the hard rule).
- Fill alphas (`primaryContainer@0.5` setup card, `surfaceContainerHighest@0.5/0.55` rows, `secondaryContainer@0.58` note, heatmap cells, rank badge, tile washes, decorative blobs, loading-dot alpha): fills/decoration, not text; the ban covers text/icons. `ArenaLeaderboard.kt:222` progress colour at 0.6 is a non-text indicator; left.
- `ProviderCard` has no persistent "credential rejected" state (failures surface via snackbar): a behaviour change, not polish.
- Missing error/loading states in Memories/Workflows/GeneratedImages/Leaderboard/Canvas ViewModels and the Persona retry no-op: state-machine additions outside a polish pass; listed for the orchestrator.
- Persona HSL accent fills (`PersonaCard.kt:54,193`, `PersonaPickerSheet.kt:157`): user-seeded colour is the feature; replacing with theme pairs is a redesign.
- Mind-map native canvas text (13dp/10dp px, 12-char truncation, physical anchors), insights canvas bars from physical left, `BranchConnector` physical bend, fast-scroll canvas anchors, `SwipeToDeleteBox` physical drag deltas: RTL in drawing code; no RTL locale is shipped and the swipe file is gesture-order-sensitive (hard constraint).
- `DraggableStepList.kt:142` 12px vs 12dp reorder spacing: real bug but behavioural (reorder targets), not visual; listed for the workflows owner next wave.
- Sensor tilt (`ExploreScreen.kt:221`, `OnboardingScreen.kt:105`): input-driven, not a continuous loop; `rememberDeviceTilt` already pauses on `ON_PAUSE`. Gating input-driven parallax on the particle gate is a judgment call left to the theme/orchestrator.
- `StepProgressIndicator` pulse alpha computed but unused (W2 gated it); harmless, left.
- Text-field shapes stay on `MaterialTheme.shapes.medium` rather than a squircle: M3 text fields are rounded, not squircle.

## Needs another owner
- Theme: delete the KDoc mention of `SettingsSheetShape` at `ui/theme/Shapes.kt:107` (the symbol no longer exists).
- Theme: `CustomShapes.ProviderCard` and `CustomShapes.BottomSheet` now have zero consumers in `app/src`; can be removed after a repo-wide grep.
- Orchestrator: decide proper-noun titles (list above) and whether input-driven sensor parallax falls under the decoration gate.
- Workflows (next wave): `DraggableStepList.kt:142` px/dp spacing; Workflows/Memories/GeneratedImages/Leaderboard error states; Persona retry.
- Nobody owns `ui/theme/Shapes.kt` KDoc for `ScreenContainer`'s collapsed-bar behaviour; a one-line note there would close the W1/W2 question for good.

## Not verifiable without a device
- Rendered settings/home seam against the collapsed `LargeTopAppBar` (geometry argued above; not rasterised).
- Group-row parity: 76dp rows now match by arithmetic; mixed-list rhythm not seen.
- `heightIn(min)` cards/buttons at font scale 1.3 on 360dp (onboarding, explore, workflow badges): growth is allowed now; wrap behaviour not rendered.
- Weighted header columns in settings: trailing switches/buttons no longer starved by construction; not rendered.
- `dataReveal()` feel on heatmap/comparison/response-time charts (spec change noted in the table).
- OnDeviceModels sheet G2 top corners; SystemPromptField squircle; fast-scroll label 22→24dp; persona/bookmark text fields 20→16dp.
- Static frames of the wavy indicators at amplitude 0 and the stable arena codename under animator scale 0 / battery saver.
- Tokenised search/arena/explore/onboarding transitions vs the previous framework defaults.
- All contrast claims are JVM-computed over the 39 generated schemes; dynamic colour on real wallpaper may differ.
