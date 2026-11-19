# Pass W8 — home + settings: report

Branch `pass/w8-home-settings`, base `6ade255c` (v2.41.7), rebased onto `origin/main` at `2ae059d5` (W8 theme merge `3b79c36c`) after Parts 1–3. Static work only: no device, no emulator, no screenshot, no push. `./gradlew --stop` run at the end.

## Commits (oldest first, post-rebase hashes)

| Commit | Part | Summary |
|---|---|---|
| `b2f2455a` | 1, 2, 3 | Voice-settings notices pinned by a scanner test against `CopyRules.isNotice`; `SettingsToggleRow` gains `enabled` (disabled content at `Alphas.Disabled`, gated in `HomeSettingsContrastTest`); the assistant toggle group switches from hidden to disabled rows; persona starter ASCII ellipsis → `…`; `HomeSettingsCopyRulingsTest` scans `domain/usecase/**` and `data/**` |
| `15d079dc` | theme rows H1–H11 | 17 M3 chip sites onto `ExpressiveFilterChip` / `ExpressiveAssistChip` / `ExpressiveSuggestionChip` (hand-passed `shape =` dropped; **visible: 32 → 40dp**, press morph, click haptic); `ArenaPromptInput` KDoc off the deleted `CustomShapes.MessageInputContainer` |
| (this) | — | REPORT.md |

## Gates

`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` rc=0 and `:app:testGithubDebugUnitTest` 0 failures before every commit:

| After | Unit tests |
|---|---|
| Parts 1–3 (pre-rebase, base 6ade255c) | 832 (+6: 3 `AssistantVoiceSettingsNoticeRulesTest`, 2 `HomeSettingsCopyRulingsTest`, 1 `HomeSettingsContrastTest`) |
| rebase onto W8 theme + chip rows | **839** (theme merge +7), 0 failures, 0 errors, 0 skipped |

`FontStyle.Italic`: 0. `testTag(` / `contentDescription` removed: 0. `scripts/`, `.github/`, `ui/theme/**`, `ui/screens/chat/**`, `assistant/**`, `AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, `SmoothStreamingText.kt`: untouched. No androidTest added (judge minimum unchanged). `local.properties` not committed. Preference keys, nav graph, swipe order, launcher icon untouched.

## Tests added (6 in 3 files)

| File | Count | Pins |
|---|---|---|
| `ui/screens/settings/AssistantVoiceSettingsNoticeRulesTest.kt` (new) | 3 | on-disk scan of `AssistantVoiceSettingsViewModel.kt`: every literal assigned to `message.value` / `previewMessage.value` / `VoiceModelPickerState(error = …)` is found (≥ 8 sites, so a refactor that hides them fails), every one passes `CopyRules.isNotice`, and none contains the word "retry" (the "Please retry." / "Reopen to retry." regression the W7 assistant report listed) |
| `ui/components/HomeSettingsCopyRulingsTest.kt` | +2 | `domain/usecase/**` and `data/**` plain string literals carry no ASCII `...` (raw `"""` strings — model prompts and JSON templates — and `Log.*` lines are exempt); a self-check that the persona starters are scanned and the JSON template in `ExtractMemoriesUseCase` is skipped, so the exemptions cannot silently swallow the rule |
| `ui/components/HomeSettingsContrastTest.kt` | +1 | `disabledToggleRowContentUsesTheSharedAlphaAndStaysSeparable`: the disabled title / supporting / icon colours use `MaterialChatMotion.Alphas.Disabled` exactly, and composited over the row fill (surfaceContainerHigh) separate from it by ≥ 1.4:1 on all 39 appearances |

## Part 1 — voice-settings notices (`b2f2455a`)

`AssistantVoiceSettingsViewModel.kt` re-read line by line: every notice already reads "Could not … Try again." / "… and try again." (W7 row H5 was done correctly); no "Please retry." / "Reopen to retry." survivor. What was missing was the pin: the new scanner test fails on any regression. `previewMessage` also carries `sherpaBackend.unavailableReason(model)` and `TtsEvent.Error.message` (`:160, :170`) — those are `VoiceCopy` sentences owned by the assistant/voice layer and pinned by `AssistantCopyTest`; the scanner covers the literals in this file only.

## Part 2 — `SettingsToggleRow.enabled` (`b2f2455a`)

- `settings/components/SettingsExpressive.kt:142-230`: `enabled: Boolean = true`, passed to the segmented `ListItem` and to `ExpressiveSwitch`; `disabledContainerColor = surfaceContainerHigh` so the row keeps its fill and its place in the group; title / supporting / icon read `SettingsToggleRowDefaults.disabledTitle/Supporting/Icon` (the role at `Alphas.Disabled`). `SettingsToggleRowDefaults` is a pure object so the JVM gate composites without composing.
- `SettingsScreen.kt:3147-3185` (assistant group): **hidden → disabled**. The group is now a fixed three rows; "Voice input" and "Read aloud" are `enabled = assistantEnabled`. Reason: the segmented shapes no longer reflow when the parent toggle flips (the last row's corners jumped from the first row to the third), and the user can see what turning the assistant on unlocks. The dependent rows in `AssistantScreenContextSettingsPanel.kt:92-120` are the same pattern but the panel is not mine — see Needs another owner.
- WCAG 1.4.3 exempts inactive controls; the 1.4:1 separation floor is a visibility gate, not an AA claim.

## Part 3 — ellipsis below the UI layer (`b2f2455a`)

`domain/usecase/GetBuiltinPersonasUseCase.kt:104` `"Challenge my argument that..."` → `"Challenge my argument that…"`. The extended scanner found no other site: `ExtractMemoriesUseCase.kt:375` is inside a raw-string prompt (exempt, and the self-check pins that it is), `OpenAiModels.kt:308` is a comment.

## Part 4 — arena composer

Already landed in W7 (`ArenaPromptInput.kt:34` file-private `ArenaComposerShape = RoundedCornerShape(28.dp)`). The theme deleted `CustomShapes.MessageInputContainer` in W8; the rebase compiled cleanly on first try. Row H11: the KDoc at `:29-33` no longer mentions the deleted token and names `ExpressiveShapes.Card` as the equal radius; the shape stays round (the composer ruling), not a squircle, so no value change.

## Theme rows H1–H10 — chips (`15d079dc`)

Per-site diff is the composable name, the dropped `shape = ExpressiveShapes.Chip` line, and the import swap (raw `androidx.compose.material3.*Chip` import removed; `ExpressiveShapes` import removed where it had no other reader: `BookmarkFilterBar`, `ArenaModelPicker`, `BookmarkCard`, `ConversationStarterChips`). `*ChipDefaults` colour/border calls are unchanged (the wrappers take the same `colors` / `border` parameters).

| Row | Site | Wrapper |
|---|---|---|
| H1 | `insights/InsightsScreen.kt:149` | `ExpressiveFilterChip` |
| H2 | `settings/ModelAssignmentsScreen.kt:674, 704` | `ExpressiveFilterChip` |
| H3 | `settings/InteractionSettingsScreen.kt:208, 237` | `ExpressiveFilterChip` |
| H4 | `settings/SettingsScreen.kt:2568, 2670` | `ExpressiveFilterChip` |
| H5 | `personas/components/PersonaEditorSheet.kt:169` | `ExpressiveFilterChip` |
| H6 | `bookmarks/components/BookmarkFilterBar.kt:81, 136` | `ExpressiveFilterChip` |
| H7 | `bookmarks/components/AddBookmarkSheet.kt:121, 163` | `ExpressiveFilterChip` |
| H8 | `arena/components/ArenaModelPicker.kt:136` | `ExpressiveFilterChip` |
| H9 | `bookmarks/components/BookmarkCard.kt:155`; `ArenaModelPicker.kt:76` | `ExpressiveAssistChip` |
| H10 | `personas/components/ConversationStarterChips.kt:44`; `PersonaCard.kt:180` | `ExpressiveSuggestionChip` |

**Visible change**: every chip grows from the M3 32dp to the wrapper's 40dp capsule and gains the press corner morph and click haptic. Grep after the change: 0 raw `FilterChip(` / `AssistChip(` / `SuggestionChip(` calls and 0 raw chip imports under ownership; 17 wrapper calls.

H12 (`util/AttachmentImporter.kt`, `data/repository/UpdateManager.kt`) is outside my ownership list — see Needs another owner.

## Part 5 — screen walk round 4 (360dp / font scale 1.3 / RTL), static

What was checked, by reading, over Explore, Insights, Arena, Onboarding, Settings (provider cards, model assignments, on-device models):

- **Fixed widths carrying text**: `SettingsScreen.kt:1811` (bubble-style tiles, 128dp) and `:1917` (control-shape tiles, 124dp), `labelLarge`, `maxLines = 1` + ellipsis. Longest labels are `prettyName()` of the enums: "Expressive" / "Geometric" / "Balanced". At scale 1.3 (`labelLarge` 14sp → 18.2sp, ~0.5em average glyph) "Expressive" measures ≈ 90dp in a 104dp content width (128 − 2×12 padding); fits, ellipsis is the fallback. Left alone.
- **Truncation sites without `overflow`**: none — every `maxLines = 1` under ownership (`ExploreScreen.kt:448, 459`, `ModelComparisonCard.kt:169`, `ResponseTimeChart.kt:113`, `ArenaModelPicker.kt:144`, `ArenaLeaderboard.kt:226`, `SettingsExpressive.kt:321`, `ProviderCard.kt:176, 183, 202`) pairs with `TextOverflow.Ellipsis`.
- **Explore grid** (`ExploreScreen.kt:179`, `GridCells.Fixed(2)`): at 360dp each tile is ≈ 164dp; title `titleMedium` one line with ellipsis, description 3 lines (`:466`), `heightIn(min = 156.dp)` so a 1.3-scale description grows the tile rather than clipping. Left alone.
- **Arena**: `ArenaScreen.kt:271` battle pager at a fixed 420dp (W6 decision, unchanged); `ArenaLeaderboard.kt:129` 64dp is the empty-state trophy icon, not text; voting bar wrapping was reasoned in W7.
- **Onboarding**: `OnboardingScreen.kt:192, 324, 357` are `heightIn(min = …)` floors (grow with content); no fixed heights on text.
- **RTL**: no `Alignment.Absolute*` / `Arrangement.Absolute*` under ownership; no non-mirrored directional icon under ownership (the one hit, `Icons.Outlined.Notes` at `AssistantScreenContextSettingsPanel.kt:119`, is the assistant panel — listed below; `Icons.AutoMirrored.Outlined.Notes` exists in material-icons-extended 1.7.6).
- **Fixed `sp`**: `WorkflowCard.kt:72` (`22.sp` emoji inside the 48dp tile) and `StepProgressIndicator.kt:195` (`12.sp` step number over `labelSmall`). Both scale with the font setting (sp, not dp); the emoji tile is `size(Tile)` with 8dp padding so at scale 1.3 a 28.6sp emoji is drawn in a 32dp inner box and the `Surface` clips to the tile shape — the glyph is centred, so clipping would be symmetric edge loss at most. Reasoned, not seen; left alone rather than change a working tile without a render.
- **Copy**: 0 "Failed" / "Retry" / ASCII-ellipsis / `.message ?:` hits under ownership (scanner green).
- **Contrast**: no new fills introduced this pass except the disabled row (gated); the chip wrappers keep the M3 `*ChipDefaults` colours, which `componentContrastPairs()` already covers at the theme level.

No JVM seam was added for the tile widths: a label-width pin would re-implement text measurement. The decisions above are recorded here instead.

## Deliberately left alone

- `SettingsScreen.kt:1811/1917` tile widths, `ArenaScreen.kt:271` pager height, `WorkflowCard.kt:72` / `StepProgressIndicator.kt:195` sp literals (above).
- `AssistantVoiceSettingsViewModel.kt:160, 170` notice pass-through from `VoiceCopy` (assistant-owned sentences, already pinned there).
- `HomeListInsets` (H6, contract note only).
- The `ExpressiveShapes.Chip` token itself (theme-owned); the wrappers own the chip shape now.

## Needs another owner

- **assistant** (`ui/screens/settings/AssistantScreenContextSettingsPanel.kt`): `:92-120` the three dependent rows are hidden (`if (enabled)`, `rowCount = if (enabled) 4 else 1`) — `SettingsToggleRow` now takes `enabled`; pass `count = 4` and `enabled = enabled` for the same hidden → disabled switch Settings made. `:119` `Icons.Outlined.Notes` → `Icons.AutoMirrored.Outlined.Notes` for RTL.
- **orchestrator / data** (W8 theme row H12): `data/repository/UpdateManager.kt:97, 105, 176, 226` `e.message ?: "Failed to check for updates" / "Download failed" / "Installation failed"` → `CopyRules.failureLine("check for updates" / "download the update" / "install the update")` with no exception pass-through; `:199` "APK file not found. Please download again." → "The update file was not found. Download it again." (`isNotice`). `util/AttachmentImporter.kt:100` is a `Log.e` line only — no user copy there at this commit. Neither file is in my ownership list; `HomeSettingsCopyRulingsTest`'s new `data/**` scan covers ASCII ellipsis only, by design, so these do not fail it.
- **chat / theme**: nothing new.

## Not verifiable without a device

- The 17 chips at 40dp: wrapping of the `FlowRow` groups at 360dp / scale 1.3 (`ArenaModelPicker` model list, `BookmarkFilterBar` tags, `PersonaEditorSheet` tags, `AddBookmarkSheet` tag chips) and whether a two-row chip group now pushes content below the fold; the press corner morph and click haptic.
- The disabled "Voice input" / "Read aloud" rows: that `Alphas.Disabled` content over surfaceContainerHigh reads as off rather than broken on the dark and pitch-black palettes (numerically ≥ 1.4:1 everywhere; not seen), and that the segmented group no longer reflows when "System assistant" toggles.
- The persona starter chip now ending in `…` (one glyph narrower than `...`).
- Every Part 5 conclusion: label fit in the 128/124dp tiles, Explore tile growth at scale 1.3, the emoji tile clip, RTL layout of the swipe rows and the chip rows.
- No RTL composition, no font-scale run, no screenshot, no hosted run.
