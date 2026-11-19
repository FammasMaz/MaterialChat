# Pass W4 — Home + Settings + drawer screens: report

Branch `pass/w4-home-settings`. Started on `bb91963` (v2.41.3); rebased once onto `origin/main` at `a5f16d1` (W4 theme merge) as steered — four commits replayed with no conflicts. Merge-base at the time of this report: `a5f16d1`. Not pushed.

## Commits (oldest first)

| Commit | Part |
|---|---|
| `878a89f` | feat(ui): part 1 — typed load errors with retry for Memories, Workflows, GeneratedImages, Arena leaderboard |
| `3e054b4` | feat(ui): part 2 — DeviceTilt/DeviceShake gated on lifecycle RESUMED + decoration motion gate |
| `4fa73a5` | fix(ui): part 3 — mind-map node labels in sp, measured against node width |
| `89c633a` | feat(canvas): part 4 — colorScheme injected as CSS variables into every artifact template |
| `aff77a8` | refactor(ui): part 5 — raw 8dp badges → `ExpressiveShapes.Badge`; settings selection cards → `Card`/`DisclosurePanel`; pill/fast-scroll label off raw shapes |
| `667fa91` | refactor(ui): part 6 — drawer sheets on `SheetHeader`/`SheetGutter`; arena snackbar on `ExpressiveSnackbar`; MainActivity/nav-bar/canvas-input motion+tint on tokens; Unicode ellipsis; no raw exception text in snackbars |
| (this file) | docs: REPORT.md |

## Gates (at `667fa91`)

- `:app:compileGithubDebugKotlin` rc=0 and `:app:compileGithubDebugAndroidTestKotlin` rc=0 before every commit.
- `:app:testGithubDebugUnitTest`: **690 tests, 0 failures, 0 errors, 0 skipped** (664 on `a5f16d1` + 26 added here). No existing test touched; no androidTest added; `scripts/`, `.github/workflows/` untouched.
- `git diff a5f16d1` over `testTag(` / `contentDescription =` removals: none. `FontStyle.Italic` in the diff: none. 48 files changed, 1218+/178−.
- Preference keys, nav graph, swipe archive/delete order, launcher icon, `SmoothStreamingText.kt`, `AssistantKeyboardSpacing`, assistant pill geometry: untouched.

## Tests added (26)

| File | Count | What it pins |
|---|---|---|
| `ui/screens/memories/MemoriesViewModelTest.kt` | 3 | failing `observeActiveMemories()` → `error = Unavailable`; `retry()` re-subscribes and clears; search still filters after a retry |
| `ui/screens/workflows/WorkflowsViewModelTest.kt` | 3 | failing `observeAll()` → `WorkflowsUiState.Error`; retry → `Success`; delete remains best-effort |
| `ui/screens/generatedimages/GeneratedImagesViewModelTest.kt` | 2 | failing `observeGeneratedImages()` → error; retry clears |
| `ui/screens/arena/ArenaLeaderboardViewModelTest.kt` | 2 | failing `observeRatings()` → error; retry clears |
| `ui/components/SensorMotionGateTest.kt` | 4 | `shouldSample` false unless RESUMED **and** motion allowed; event overload uses `targetState` (ON_PAUSE → false, ON_RESUME → true) |
| `ui/screens/mindmap/MindMapLabelLayoutTest.kt` | 7 | `fitToWidth` keeps short titles, appends `…` when trimmed, never exceeds width, returns the ellipsis alone when nothing fits; static overlap audit at fontScale 1.3 (title baseline vs badge top) |
| `ui/screens/canvas/ArtifactHtmlTemplatesTest.kt` | 5 | injected `:root` block carries `--bg/--fg/--surface/--primary/--outline` as `#RRGGBB`; appears in all four wrappers; absent (fallback only) when `colors == null`; author stylesheet still wins |

## Part 1 — typed load errors (`878a89f`)

- New `ui/components/LoadError.kt`: `sealed interface LoadError { data object Unavailable }` (`:14-16`) and `LoadErrorContent(onRetry)` (`:25`) on `ExpressiveEmptyState` with sentence-case copy and one "Try again" action — the shared empty-state family, no snackbar host needed.
- `MemoriesUiState.kt:12` `error: LoadError?`; `MemoriesViewModel.kt` wraps the repository flow in `.catch { emit(error) }` behind a `retryKey` that `retry()` bumps so the flow re-subscribes. `MemoriesScreen.kt:120` draws `LoadErrorContent` before the empty/list branches.
- `WorkflowsUiState.kt:31` new `Error(val error: LoadError)` branch; `WorkflowsViewModel.kt` replaces the silent `.catch {}` with `Error`, `retry()` re-observes; `WorkflowsScreen.kt:119` branch added.
- `GeneratedImagesViewModel.kt:37,46,55` and `ArenaLeaderboardViewModel.kt:33,56,65` (now expose `ArenaLeaderboardUiState` instead of a bare list; `ArenaLeaderboard.kt:108` consumes it). Screens: `GeneratedImagesScreen.kt:109`, `ArenaLeaderboard.kt:108`.
- The `catch` is on the repository flow only; a one-shot `seedTemplatesIfNeeded()` failure still falls through to the observed list (unchanged behaviour).

## Part 2 — sensor gating (`3e054b4`)

- New `ui/components/SensorMotionGate.kt:21-26`: the one pure decision `shouldSample(lifecycleState | event, motionAllowed) = motionAllowed && state.isAtLeast(RESUMED)`.
- `DeviceTilt.kt:57` reads `rememberParticleMotionGate().motionAllowed`; `:152` applies the current state explicitly on effect start (observers do not replay `ON_RESUME`, project constraint), `:154` re-applies on every lifecycle event; the gate flag is a key of the `DisposableEffect`, so a power-saver/animator-scale change re-evaluates without a lifecycle event. Pose resets to `Neutral` when sampling stops.
- `DeviceShake.kt:41,90,95` same shape; the drain loop keeps its idle `delay(50)` and is untouched otherwise.
- Resolves the W3 open question (input-driven parallax under the decoration gate): gated, as the W4 brief rules.

## Part 3 — mind-map labels (`4fa73a5`)

- New `mindmap/components/MindMapLabelLayout.kt`: `TITLE_SP = 13`, `BADGE_SP = 10`, baseline constants, `fitToWidth(text, maxWidth, measure)` with `ELLIPSIS = \u2026` (`:12-44`).
- `MindMapCanvas.kt:161-162` title/badge sizes via `sp.toPx()` (fontScale honoured) instead of `dp.toPx()`; `:197-203` titles are fitted once per layout against `nodeWidth − 2·textXPad` using the real `Paint.measureText`, replacing the fixed 12-character cut; draw path `:527` unchanged otherwise.
- Static audit at 1.3: title baseline at `top + 30dp − 2dp`, descent `0.25 × 13sp × 1.3 ≈ 4.2dp` → bottom ≈ 32.2dp; badge ascent `0.93 × 10sp × 1.3 ≈ 12.1dp` above its baseline at `60 − 8 = 52dp` → top ≈ 39.9dp. Gap ≈ 7.7dp, no overlap; pinned in `MindMapLabelLayoutTest`. Node height stays 60dp (unchanged layout).

## Part 4 — canvas theme CSS variables (`89c633a`)

- `ArtifactHtmlTemplates.kt:9` `CanvasThemeColors(surface, onSurface, primary, outline)`; `:46` `themeStyleBlock()` emits a second `<style>` with `:root { --bg; --fg; --surface; --primary; --outline }` **after** the fixed-hex `prefers-color-scheme` block (`:83-91`), so the theme wins by cascade and the hex block remains the fallback when `colors == null` or when a template defines its own variables later in the document. All four wrappers take `colors: CanvasThemeColors? = null` (`:73,141,209,269`).
- `SmartCanvasViewModel.kt:43,85` caches the last colours and re-renders only when they change; `SmartCanvasScreen.kt:45,76` maps `MaterialTheme.colorScheme` and pushes it on every recomposition where it differs (theme switch re-renders the WebView once).
- Mermaid still picks `dark`/`default` from `prefers-color-scheme`; only its page colours follow the app theme.

## Part 5 — shapes after the theme merge (`aff77a8`)

| File:line | Was | Now |
|---|---|---|
| `workflows/components/StepResultCard.kt:77`, `workflows/WorkflowExecutionScreen.kt:491,543`, `personas/components/PersonaCard.kt:197` | `RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` |
| `settings/SettingsScreen.kt:1686,1787,1892` | `RoundedCornerShape(if (selected) 28 else 20)` | `if (selected) ExpressiveShapes.Card else ExpressiveShapes.DisclosurePanel` (W4 theme table; radii not animated, so two tokens suffice) |
| `generatedimages/GeneratedImagesScreen.kt:276` | `RoundedCornerShape(999.dp)` | `CustomShapes.Pill` |
| `ui/components/ExpressiveFastScrollBar.kt:483` | `CustomShapes.Snackbar` | `ExpressiveShapes.DisclosurePanel` (cross-surface F1: the label is not a snackbar) |

Insights charts (`ActivityHeatmap:56`, `ModelComparisonCard:53`, `ResponseTimeChart:53`, `StatCard:43`, `ModelUsageChart:72`) were already on `Spatial.dataReveal()` at `bb91963`; the theme pass pinned 0.8/50 as the ruled spec, so no consumer change (brief task 5 closes with nothing to edit).

Remaining raw `RoundedCornerShape` in owned files: `SettingsScreen.kt:1971-1984` (bubble-style preview enum — deliberate), `ActivityHeatmap.kt:156` (3dp heat cell; no token below 8dp), `StepEditor.kt:65` (14dp step badge on a 32dp tile — `Badge` 8dp would read too square, `MenuSelection` 16dp too round; left, listed below), `OnboardingScreen.kt:478` (44dp decorative blob behind a 132dp box, not a surface).

## Part 6 — cross-surface audit items (`667fa91`)

| Finding | File:line | Change |
|---|---|---|
| F9 sheets | `PersonaPickerSheet.kt:77-80`, `PersonaEditorSheet.kt:83,88`, `AddBookmarkSheet.kt:86,90` | `SheetHeader(title, onClose)` + `SheetGutter`; AddBookmark's private close button and `titleLarge` title removed in favour of the shared header (`contentDescription = "Close"` preserved by `SheetHeader`) |
| F1 arena snackbar | `ArenaScreen.kt:51,133` | raw `Snackbar` on `CustomShapes.Snackbar` → `ExpressiveSnackbar`; `CustomShapes` import dropped |
| F8 dialog | `MemoriesScreen.kt:158` | already `ExpressiveShapes.Dialog` + `dialogEntrance()` on this branch (W3 fixed it; audit line 130 is pre-W3) — verified, no change |
| F15 alpha on icon | `ui/navigation/MaterialChatNavBar.kt:234` | unselected tint `onPrimaryContainer@0.72` → `onPrimaryContainer` (sits on `surfaceContainerHighest@0.28` over the bar fill; full role) |
| F15 alpha on icon | `canvas/components/CanvasRefinementInput.kt:78` | disabled send icon `onSurfaceVariant@0.38` → `onSurfaceVariant` on `surfaceContainerHighest` |
| F10 raw springs | `MainActivity.kt:322-356` | ad banner and floating-toolbar `slideIn/Out` springs (500/0.8, 500/1.0, 500/0.7, 500/1.0) → `Spatial.container()` in / `Spatial.collapse()` out; `spring` import dropped |
| F6 ellipsis | `SettingsScreen.kt:1495,1498,1553,1556,3104,3107,3109`, `AddProviderSheet.kt:359,547` (`:331` `"sk-..."` kept), `SettingsViewModel.kt:447,576`, `OnboardingViewModel.kt:50`, `ConversationsViewModel.kt:561`, `ArenaScreen.kt:159`, `UpdateBanner.kt:228`, `StepEditor.kt:113`, `WorkflowExecutionScreen.kt:197`, `CanvasRefinementInput.kt:125`, `search/components/SearchBar.kt:74`, `BookmarkSearchBar.kt:41`, `MindMapCanvas` (via `MindMapLabelLayout.ELLIPSIS`) | ASCII `...` → `…` |
| F6 exception text | `OnDeviceModelsViewModel.kt:56,93,103,113` | `it.message ?: "…"` → fixed sentence-case copy |
| F6 exception text | `SettingsViewModel.kt:606` | snackbar shows `"Authentication failed"`; the form's `authStatus`/`apiKeyError` (`:603-604`) keep the provider's message because it is the inline diagnostic the user reads to fix the key — not a snackbar |
| F6 exception text | `ArenaViewModel.kt:141,274,313` (source of `ArenaScreen.kt:89`) | `"Failed to load models: ${e.message}"` etc. → `"Could not load models"`, `"The battle failed"`, `"Could not record the vote"` |

Snackbar period rule: not applied. The audit's "with period" examples (`SettingsViewModel.kt:666,681`) are two-sentence notices; the one-phrase snackbars have no period on home/settings and chat. Making every single-phrase snackbar end with a period would diverge from the chat owner's copy; left for the orchestrator to rule once (listed below).

## Deliberately left alone
- `WorkflowExecutionViewModel`/`OnboardingViewModel` `error: String?` fields: already have an error path; typed-error migration is a larger refactor than the brief's four view-models.
- `SmartCanvasViewModel` has no load error state: its input is a navigation argument, there is no repository call to fail.
- `ProviderCard` credential-rejected state, persona HSL fills, RTL in drawing code, `DraggableStepList.kt:142` px/dp: carried from W3, unchanged.
- `ExpressiveSurfaces.kt:193` disabled-content alpha (M3 convention) and `MaterialChatNavBar.kt:220` `surfaceContainerHighest@0.28` (a fill, not text): outside the text/icon ban.
- Insights chart specs: already on the ruled token.
- `MindMapCanvas` node width/height: unchanged; only the label fitting scales with font, so very large fontScale (>1.6) will still trim titles hard.

## Needs another owner
- **Orchestrator**: rule the snackbar period convention once (one-phrase notices with or without a period) across chat/home/assistant; decide "Retry" vs "Try again" (this pass used "Try again" in `LoadErrorContent`, matching the assistant; home/bookmarks/chat still say "Retry").
- **Theme**: a ~12–14dp badge token or a per-size `SquircleShape` for `StepEditor.kt:65` (32dp tile at 14dp) — `Badge` (8) and `MenuSelection` (16) both misread at that size.
- **Theme**: `ExpressiveShapes.Notice` (F1) so `ExpressiveSnackbar`'s private `SquircleShape(20.dp)` and the assistant notice share a token; `CustomShapes.Snackbar` now has zero consumers in `app/src` and can be deleted after a repo grep.
- **Chat**: `ChatScreen.kt:403-410` raw `Snackbar` → `ExpressiveSnackbar`, and the three `FusionSourcesSection`/`FusionProgressView` 8dp sites → `Badge` (theme table rows).
- **Theme / next pass**: `ExpressiveEmptyState` shape/type pair (F11) — `LoadErrorContent` inherits whatever it draws.
- **Data**: `NativeAuthManager.kt:185,481` auth-status copy still uses ASCII `...`.

## Not verifiable without a device
- The four new error states: JVM tests prove the state machine; the rendered `LoadErrorContent` inside each screen's `ExpressiveContentSurface` was not seen.
- Sensor gating: registration/unregistration is reasoned from `SensorMotionGate` and the lifecycle contract; that the tilt parallax actually stops under battery saver / animator scale 0 and resumes cleanly was not exercised on hardware.
- Mind-map labels at fontScale 0.85/1.3 (baseline math above, not rasterised); emoji-prefixed titles measured with the default typeface may differ slightly from the colour-emoji fallback width.
- Canvas: that the WebView reloads once on theme switch and that `--primary/--outline` reach author CSS; KaTeX/Mermaid CDN pages not loaded here.
- Settings selection cards: `Card`/`DisclosurePanel` squircles replacing 28/20dp circular corners (static swap, no animation path existed).
- Fast-scroll label on `DisclosurePanel` (20dp squircle vs 24dp round).
- Nav-bar unselected tint at full opacity against the 0.28 wash on every palette (role pair, not slot-tested — `HomeSettingsContrastTest` has no nav-bar slot).
- `Spatial.container()/collapse()` feel on the toolbar and ad-banner slides vs the previous 500-stiffness springs.
- Sheet headers: `SheetHeader`'s 16dp top padding under the drag handle on the three drawer sheets.
- All copy changes are string-level; no screenshot of any ellipsis site.
