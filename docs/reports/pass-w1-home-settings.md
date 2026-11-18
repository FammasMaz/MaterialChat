# Pass W1 — Home (conversation list) + Settings: polish report

Branch `pass/w1-home-settings`, rebased onto main 979189b (theme pass merged). Base v2.41.0 (574b5c3).

Commits (oldest first):

- `e6348bb` refactor(ui): part 1 — italic wordmark dropped, dead CircularProgressIndicator imports, hand-rolled springs → Motion tokens, shared Dialog shape on memories delete-all
- `4d81c41` feat(ui): part 2 — contrast resolver + 39-theme JVM contrast test; call sites repainted against their actual fills; fast-scroll tweens → Motion tokens
- `be138d5` style(ui): part 3 — sentence-case settings labels, no trailing periods, settings content surface on `ExpressiveShapes.ScreenContainer`, group row tile matches plain row
- `9fad204` refactor(ui): part 4 — audit (`docs/audit-color-contrast.md`) sites onto theme tokens: `ExpressiveMotion.Press.*`, plain `on*` roles for text, resolver on `ThemeContrast.readableOn`, memories/loading-indicator springs → tokens, indicator loops gated by `ParticleMotionGate`

Gates run at the final commit: `:app:compileGithubDebugKotlin` OK; `:app:testGithubDebugUnitTest` 602 tests, 0 failures, 0 errors; `:app:compileGithubDebugAndroidTestKotlin` OK. Not pushed.

## What was audited

Every file under `ui/screens/conversations/**`, `ui/screens/settings/**` (minus `Assistant*SettingsPanel.kt`), `ui/screens/memories/**`, and `ui/components/Expressive*.kt`, `ExpressiveFastScrollBar.kt`, `FastScrollDateLabels.kt`, `UpdateBanner.kt`, grepped for: `FontStyle.Italic`, `CircularProgressIndicator`, raw `spring(`/`tween(`, `.copy(alpha` on `on*` roles, `AlertDialog` shapes, tile sizes, section-header styles, Title Case labels, trailing periods, snackbar hosts, top-bar treatment.

## What changed and why (file:line at HEAD 9fad204)

### Part 1 — consistency / dead code / motion tokens
- `ui/screens/conversations/ConversationsScreen.kt:597` — wordmark "material" no longer `FontStyle.Italic` (banned); keeps Bold + primary. `:529` gear pop-in uses `ExpressiveMotion.Spatial.playful()` instead of `spring(0.5f, 140f)`.
- `ui/screens/settings/ModelAssignmentsScreen.kt:30`, `SettingsScreen.kt:74`, `components/AddProviderSheet.kt:37` — dead `CircularProgressIndicator` imports removed (no call sites; every loader already is `LoadingIndicator`). `SettingsScreen.kt:101` dead `M3ExpressiveCircularProgress` import removed.
- `ui/screens/settings/components/SystemPromptField.kt` — eleven hand-rolled `spring(...)` replaced: `animateContentSize` → `Spatial.contentSize()`, edit/display `AnimatedVisibility` → `Disclosure.Expand/Shrink/FadeIn/FadeOut`, edit-button scale → `Press.Scale`.
- `ui/screens/settings/OnDeviceModelsScreen.kt:148` — `animateContentSize` spring → `Spatial.contentSize()`.
- `ui/components/UpdateBanner.kt:62-72` — banner slide-in/out springs → `Spatial.container()` / `Spatial.collapse()`.
- `ui/screens/memories/MemoriesScreen.kt:130,150` — delete-all dialog gets `Modifier.dialogEntrance()` and `ExpressiveShapes.Dialog` (was raw `RoundedCornerShape(28.dp)`), matching every other dialog on home/settings.

### Part 2 — colour & contrast (JVM test)
- New `ui/components/HomeSettingsContrast.kt`: `HomeSettingsSlot` names every text/icon colour home and settings draw with the fill it sits on (rows resting/pressed/streaming/archived, chips, swipe actions at container and armed fractions, header actions, filter group, empty state, fast-scroll thumb/icon/label, dialogs, snackbar, settings rows/tiles/switches/sliders/provider cards/SplitButton, muted card descriptions, update banner, memory cards). `HomeSettingsPalette.resolve()` keeps the role where it reads and otherwise falls back through the listed alternates via `ThemeContrast.readableOn`.
- New `app/src/test/java/com/materialchat/ui/components/HomeSettingsContrastTest.kt`: same 39-appearance matrix as the assistant/theme tests (10 static palettes × light/dark/pitch-black + 3 dynamic-like seeds × 3). Asserts every resolved slot ≥ WCAG AA, that the preferred role is kept where it already reads (no needless repaint), and records the role-only baseline failures by name.
- Baseline failures found at 574b5c3 (all now resolved at the call site):
  - `Active` streaming label (`primary` on the 48 % primaryContainer blend): 4.30/4.33 on VIOLET/CITRUS light → `ConversationItem.kt:280`, `ExpandableConversationGroup.kt:433` read `RowActiveLabel`.
  - Chat-info dialog labels (`primary` on surfaceContainerHigh): 4.07/4.13 light → `ConversationsScreen.kt:907` reads `DialogLabel`.
  - System-prompt placeholder (`onSurfaceVariant` @ 60 %): 3.0–4.0 everywhere → `SystemPromptField.kt:180` reads `SystemPromptPlaceholder` (plain `onSurfaceVariant` after Part 4).
  - Provider credential error (`error` on active card primaryContainer): 4.45 dark VIOLET → `ProviderCard.kt:344-358` resolves icon+text per card fill.
  - Premium/secondary/tertiary card descriptions @ 78 %, update-banner lines @ 80 %, memory kind/date @ 78/66 %: 3.7–4.3 on dark palettes → plain `on*` roles (`SettingsScreen.kt:1430,1486,1544`, `UpdateBanner.kt:153,235,301`, `MemoriesScreen.kt:295,306,315`).
  - Branch row icon (`secondary` on surfaceContainerHigh): 1.00 on COSMIC light (the theme pass's transparent-secondary bug) → `BranchConversationItem.kt:153` reads `BranchRowIcon`.
- Deliberate exemption: slider inactive track vs its card (1.07–1.17). WCAG 1.4.11 covers the identifying parts (thumb, active track), which are the `SliderActiveTrack*` slots and pass; repainting every inactive track outline-grey would change M3 slider appearance app-wide. Noted in `HomeSettingsContrast.kt:96-99`.
- `ui/components/ExpressiveFastScrollBar.kt:113-129,282-297` — width/alpha/label tweens → `Spatial.default()`, `Effects.reveal()`, `Spatial.menuOpen()`; `:251` 70 ms tracker → `Effects.fast()`.

### Part 3 — IA coherency and home↔settings consistency
- `SettingsScreen.kt` / `InteractionSettingsScreen.kt` — every `title =` label sentence case ("AI providers", "Dynamic colour" kept as "Dynamic color" per existing US spelling, "Haptic feedback", "Beautiful model names", "Remember last model", "Always show thinking", "Token counter", "Web search", "System prompt", "System assistant", "Voice input", "Read aloud", "Premium & ads", "Interaction & motion"). Supporting strings and preference keys untouched. Trailing period removed from the Memories supporting line (`SettingsScreen.kt:1152`).
- `SettingsScreen.kt:714-720` — settings content surface uses `ExpressiveShapes.ScreenContainer` (same 28 dp top contour as home) instead of an inline `RoundedCornerShape`.
- `ExpandableConversationGroup.kt:205-207` — group parent tile 48 dp, opaque `primaryContainer`, like `ConversationItem`; provider/branch chips opaque too (slots updated to match).

### Part 4 — audit `docs/audit-color-contrast.md` sites in my files
- 6a italic: none left (the one home site was removed in Part 1).
- 6d alpha-on-text: `ModelAssignmentsScreen.kt:231` → `onPrimaryContainer`; `:483,575` disabled label → `onSurfaceVariant`; `OnDeviceModelsScreen.kt:274` → `onSecondaryContainer`; `:434` → `onTertiaryContainer`; `UpdateBanner.kt:256` wavy track → `ProgressIndicatorDefaults.linearTrackColor`; the Part-2 `mutedContentOn/mutedIconOn` helpers deleted and their sites use plain `on*` roles. Remaining two hits (`ConversationItem.kt:115`, `BranchConversationItem.kt:100`) are the M3 8 % `onSurface` *state layer* composited into a fill, not text — kept.
- 6e springs: press scale/shape/tint across `ExpressiveButton.kt`, `ExpressiveSurfaces.kt`, `ExpressiveSwitch.kt`, `ExpressiveSlider.kt`, `ConversationItem.kt`, `BranchConversationItem.kt`, `ProviderCard.kt`, `ExpandableConversationGroup.kt`, `SegmentedRowShapes.kt`, `SystemPromptField.kt`, `ConversationsScreen.kt` (TopBarShapeAction) → `ExpressiveMotion.Press.Scale/Shape/Progress/Tint`. `MemoriesScreen.kt:179,206-208` → `Spatial.contentSize()` / `ListItem.*`. `ExpressiveLoadingIndicator.kt` pulsing-dot, determinate and icon/shape alpha springs → `Spatial.default()` / `Effects.alpha()`.
- Gate: `ExpressiveLoadingIndicator.kt:96,289,351,405` — the three `rememberInfiniteTransition` loops and the pulsing-dots `while(true)` now run only when `rememberParticleMotionGate().motionAllowed`; otherwise they render a static frame. The remaining `tween(` in that file and the two decorative spins (`ConversationsScreen.kt:516`, `HomeEmptyState.kt:168`) are the audit's sanctioned infinite indicator tweens, all behind the gate.
- Resolver: `HomeSettingsContrast.kt` now built on `ThemeContrast`/`ThemeContrastPair`/`readableOn` (no dependency on the assistant's private copy).

## Deliberately left alone
- Swipe archive/delete order, preference keys, navigation graph, launcher icon (brief).
- `ExpressiveMotion.Spatial.shapeMorph()` in `ExpandableConversationGroup.kt:122,137` (chevron/tile open morph: a disclosure, not a press) and `disclosurePulse()`.
- `SettingsScreen.kt` dropdown/text-field `RoundedCornerShape(16.dp)` inside cards: these are M3 text-field shapes, not cards/dialogs.
- `ExpressiveFastScrollBar` squiggle: driven by scroll distance, not a frame clock, so no gate needed.
- "Dynamic color" spelling (existing US convention in the codebase).

## Needs another owner
- `ui/theme`: `CustomShapes.Dialog` (round) vs `ExpressiveShapes.Dialog` (squircle) duplication — audit §4 already flags it.
- `ui/theme/Motion.kt`: no token models the slow 16 s / 48 s decorative spins; if the theme agent adds one, `ConversationsScreen.kt:516` and `HomeEmptyState.kt:168` should take it.
- `assistant/ui/AssistantContrast.kt` still duplicates `ThemeContrast`; collapse is the assistant owner's call.
- Chat-owned files listed in audit 6a/6d/6e (`MessageBubble`, `MarkdownText`, `Fusion*`, `BeautifulModelBadges`, …) not touched.
- Preference-key renames: none required; no label change touched a key.

## Not verifiable without a device
- Press-morph feel after moving to `Press.*` (0.6/800 vs 0.6/500–600): settles faster; needs a hand on a phone.
- Loading-indicator static frame under battery saver / animator scale 0 (gate path).
- Fast-scroll bar handle widening/label slide on the new springs (visual overshoot amount).
- Group row tile at 48 dp vs 44 dp — row height alignment with plain rows in a mixed list.
- Settings content surface contour against the LargeTopAppBar when collapsed.
- Snackbar placement above the floating toolbar on home (unchanged code; was not re-verified visually).
- All contrast claims are computed (WCAG arithmetic over 39 generated schemes), not rendered; dynamic colour on a real wallpaper may differ from the three seed ramps.
