# W7 — theme pass report

Branch `pass/w7-theme`, base `7e6f6624` (v2.41.6). Static work only: no device, no emulator, no screenshot. Every file:line below was taken from grep/sed on this branch after the edits.

Gates run before each commit: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` (0 failures, 0 errors, 0 skipped at every commit).

## Commits

| Commit | Part | Unit tests |
|---|---|---|
| `f7303102` | 1 — `ExpressiveShapes.Bubble.*` radii record; `StateCardShape` pin | 798 |
| `370c6106` | 2 — seven dead `CustomShapes` tokens and their pins deleted | 795 |
| `3cea6445` | 3, 5, 8 — zero-caller indicators and legacy aliases deleted; `Tweens.linear/easeInOut`; `Indicator.Streaming*`; `pressMorph` = `fast`; theme pins its own Disclosure / HeaderPulse tokens | 797 |
| `9ee1ed8c` | 4 — `ExpressiveSizes.Icon / IconSmall / IconLarge`; `ExpressiveButton` draws them | 798 |
| `7d2dfc6f` | 6, 7 — `CopyRules` + `CopyRulesTest`; `MainActivity` clearance on `HomeListInsets` | 803 |
| (this) | REPORT.md | 803 |

## Part 1 — state card shape and bubble tokens (`f7303102`)

- `ui/components/ErrorContent.kt:119` the `ExpressiveStateCard` container reads `StateCardShape`; `:173` `internal val StateCardShape = ExpressiveShapes.Card`. `ExpressiveEmptyState` (`ExpressiveSurfaces.kt:312`) and `ErrorContent` (`ErrorContent.kt:71`) both draw `ExpressiveStateCard`, so both are on the 28dp card squircle (audit #1 / F3). The card was already `ExpressiveShapes.Card` at the base commit; this pins it so neither state can drift back to a round corner. `ErrorContentCopyTest.kt:41` `emptyAndErrorStatesShareTheCardSquircle` (`assertSame(ExpressiveShapes.Card, StateCardShape)`).
- `ui/theme/Shapes.kt:267-285` `ExpressiveShapes.Bubble`: `Rounded = RoundedCornerShape(34.dp)`; `Compact.User` 18/6/18/18, `Compact.Assistant` 6/18/18/18, `Compact.System` 20; `Geometric.User` 18/4/18/10, `Geometric.Assistant` 4/18/10/18, `Geometric.System` 16 — read value for value from `MessageBubble.bubbleShapeFor` (`MessageBubble.kt:938-939, 959-986`), in chat's `topStart, topEnd, bottomStart, bottomEnd` order. Plain `RoundedCornerShape`, not squircle, per the W5 bubble-family ruling. Pinned in `ShapeTokensTest.kt:198` `bubbleFamilyTokensCarryTheChatRadiiExactly` (every value, user/assistant inequality, not-a-squircle).

## Part 2 — dead `CustomShapes` tokens (`370c6106`)

Repo grep (`app/src/main`, `app/src/test`, `app/src/androidTest`, word-exact) for every `CustomShapes.X` consumer outside `Shapes.kt`: `Fab`, `BottomSheet`, `CodeBlock`, `ProviderCard`, `Dialog`, `MessageActionButton`, `SendButton` each had **0 `main` consumers**; their only readers were the `ShapeTokensTest` pins (`:26, :32, :37, :45-46, :210-211` at base). The `Fab` KDoc's claim of one `SystemPromptField` consumer was stale: that file draws `ExpressiveShapes.Card` / `ExpressiveShapes.Tile` / `MaterialTheme.shapes.medium` (`SystemPromptField.kt:101, 121, 210`), no `Fab`. So no migration for any owner.

- `Shapes.kt:442-445`: the seven members deleted; comment records which went and why. `CircleShape` import dropped (`SendButton` was its only use). KDoc at `:221-222` / `:230-231` no longer names the deleted aliases.
- `ShapeTokensTest.kt`: the five pins (`customDialogIsTheExpressiveDialog`, `codeBlockIsTheContentBlock`, `customBottomSheetIsTheExpressiveBottomSheet`, `providerCardIsTheExpressiveCard`, `fabAliasIsTheMediumRoundCornerUntilItsLastSiteMoves`) deleted; the `ExpressiveShapes.Dialog == SquircleShape(28)`, `BottomSheet == TopSquircleShape(32)`, `Sheet == SquircleShape(32)` value asserts they carried were kept in a surviving test, so no token value lost its pin.
- Kept (live consumers, counts from the same grep): `InlineCode` 1, `Button` 2, `ButtonPressed` 1, `MessageInputContainer` 1 (`ArenaPromptInput`, home-settings W6 item), `ConversationItem` 7, `Dropdown` 3, `ModelPicker` 3, `Pill` 22, `TopAppBar` 1, `SearchBar` 4.

## Part 3 — dead composables (`3cea6445`)

Exact word-boundary grep over `app/src` for every top-level function in `ExpressiveLoadingIndicator.kt`:

| Composable | Callers outside the file | Action |
|---|---|---|
| `M3ExpressiveLoadingIndicator` | 0 external; 2 in-file (`M3ExpressiveLoadingWithText`, `M3ExpressiveFullscreenLoading`) | kept (the morphing hero indicator; the in-file wrappers are its public entry points) |
| `M3ExpressivePulsingDots` | 1 comment (`StreamingIndicator.kt:153`); in-file via `M3ExpressiveInlineLoading` | kept |
| `M3ExpressiveInlineLoading` | `AddProviderSheet.kt:540`, `OnboardingScreen.kt:341` | kept |
| `M3ExpressiveCircularProgress` | 0 (only the two legacy aliases) | **deleted** |
| `M3ExpressiveLinearProgress` | 0 | **deleted** |
| `ExpressiveLoadingIndicator`, `ExpressivePulsingIndicator`, `ExpressiveTypingIndicator`, `ExpressiveCircularProgress`, `ExpressiveSpinningArc`, `ExpressiveLoadingWithText`, `ExpressiveInlineLoading`, `ExpressiveFullscreenLoading` (legacy alias block) | 0 each (the two `ExpressiveLoadingIndicator` hits are a KDoc and a test comment) | **deleted** |
| `M3ExpressiveDeterminateProgress`, `M3ExpressiveLoadingWithText`, `M3ExpressiveFullscreenLoading`, `MorphingSendButton` | 0 | left (the brief names only the three; listed for the orchestrator) |

The brief's `ExpressiveArcIndicator` / `ExpressiveLinearIndicator` names do not exist; the arc and bar are `M3ExpressiveCircularProgress` / `M3ExpressiveLinearProgress`, deleted above. `ExpressiveLoadingIndicator.kt:273-278` records the deletion. Orphaned imports (`Stroke`, `StrokeCap`, `Offset`) removed; file 915 -> 737 lines.

With the arc and bar gone, `MaterialChatMotion.Durations.Indicator.ArcRotation / ArcSweep / LinearPass / LinearWidth` had no consumer: deleted (`Motion.kt:648-654` KDoc records it), with their `MotionTokensTest` asserts.

## Part 4 — icon ladder (`9ee1ed8c`)

- `ui/theme/ExpressiveDimensions.kt:57, 60, 63`: `ExpressiveSizes.Icon = 20.dp`, `IconSmall = 18.dp`, `IconLarge = 24.dp`; `:20` `Chip.Icon` is now `get() = IconSmall` (value unchanged, 18).
- `ui/components/ExpressiveButton.kt:198, 214, 373` -> `ExpressiveSizes.Icon` (were 20dp literals); `:306, :453` -> `ExpressiveSizes.IconLarge` (were 24dp). No value changed; the file now has no raw icon size.
- `ExpressiveDimensionsTest.kt:40` `iconLadderIsEighteenTwentyTwentyFour`: the three values, `Chip.Icon == IconSmall`, strict ordering, and `Icon == IndicatorInline` (a button swapping its glyph for a spinner keeps its width).

## Part 5 — motion (`3cea6445`)

- `Motion.kt:211` `fun <T> pressMorph(): SpringSpec<T> = fast()`. One spec, two names: `pressMorph` is kept because it documents intent at every `Press.*` site; `fast` is the M3E token name. KDoc at `:200-210` says so. `MotionTokensTest.kt:17-27` pins `fast == pressMorph == Press.Progress == Press.Scale == Press.Shape`.
- `Motion.kt:668, 671, 674` `Durations.Indicator.StreamingSpin = 3000`, `StreamingMorph = 2400`, `StreamingShimmer = 1400` — the three raw periods chat lists (`StreamingIndicator.kt:83, 85, 311-314, 425`). Own tokens, not aliases of `MorphShape` / `HeaderPulse`, so each loop can be retuned alone; `MotionTokensTest.kt:96` pins the values and notes `StreamingShimmer == HeaderPulse` today.
- `Motion.kt:559` `MaterialChatMotion.Tweens.linear(durationMillis)` and `:566` `Tweens.easeInOut(durationMillis)`: the only two tween shapes the app's loops use (linear restart, ease-in-out). `ExpressiveLoadingIndicator.kt:106, 121` already draw them; `MotionTokensTest.kt:109` pins duration, easing and zero delay.

## Part 6 — copy rules (`7d2dfc6f`)

- `ui/theme/CopyRules.kt` (new): `TRY_AGAIN = "Try again"`, `GENERIC_HEADLINE`, `REQUEST_FAILED`, `FAILURE_VERB = "Could not"`, `BANNED_FAILURE_VERBS` (Failed to / Unable to / Couldn't / Cannot / Can't), `ELLIPSIS` (U+2026), `failureLine(action)`, and two predicates: `isPhrase` (snackbar / label / phase line: sentence case, no trailing period, no `!`, no `...`, no "please", not opened by a banned verb) and `isNotice` (one or more sentences, each ending with a full stop, same shared rules). Decision taken once: **"Could not …"** (39 live sites already use it; 85 "Failed to", 7 "Unable to", 5 "Couldn't" remain in surfaces — migration table).
- `ui/components/ErrorContent.kt:39, 42`: `ErrorContentCopy.TRY_AGAIN` / `GENERIC_HEADLINE` are `const` aliases of `CopyRules`.
- `ui/theme/CopyRulesTest.kt` (new, 5 tests): the cross-owner equality pins moved here from `ErrorContentCopyTest.kt:19-20` (chat `ChatErrorCopy.TRY_AGAIN`, assistant `AssistantCopy.AFTER_ERROR`, both `REQUEST_FAILED`s); every `ChatErrorCopy` notice and every `AssistantCopy.NOTICES` / `PHRASES` line passes the matching predicate today (green at `7d2dfc6f`). `ErrorContentCopyTest.kt:19-23` now pins only the component's own aliases.

## Part 7 — `MainActivity` clearance (`7d2dfc6f`)

- `MainActivity.kt:351`: the ad banner's bottom padding is `listBottomClearance()` while the toolbar is shown (`HomeListInsets.bottomClearance(navigationBarInset)` = inset + 16 + 64 + 16) and `HomeListInsets.ToolbarBottomMargin` (16dp) otherwise; `:347` horizontal inset is `ExpressiveSpacing.Inset` (12dp, unchanged); `:389` the toolbar row's `padding(bottom = 16.dp)` is `HomeListInsets.ToolbarBottomMargin`, so the constant `HomeListInsets` documents as "MainActivity's padding" is now read by MainActivity. Imports `:58-59, :62`.
- Behaviour change (Play flavor only, `premiumState.shouldShowAds`): the old literal 104dp ignored the navigation-bar inset, so with a 48dp three-button bar the banner's bottom (104) sat below the toolbar's top edge (48 + 16 + 64 = 128) and overlapped it; now the banner always clears the toolbar by `RowGap` (16dp). On gesture navigation (≈24dp inset) the banner moves from 104 to 120dp.

## Part 8 — test pins (`3cea6445`)

- `MotionTokensTest.kt:65` `headerPulseIsAOneSecondFourLinearRestartLoop` asserts the token value and the `decorativeSpin(HeaderPulse)` shape only; the "ChatTopBar draws this" comment is gone.
- Received from `AssistantMotionTest.kt:27-46`: `MotionTokensTest.kt:17-27` (`Press.* == fast`) and `:29-40` `disclosureTokensAreTheExpandAndCollapseSprings` (`Disclosure.Expand` 0.75/420 = `Spatial.expand`, `Shrink` 1.0/420 = `Spatial.collapse`). The assistant test still passes unchanged; it may delete its theme-value lines (A1 below).

## Migration table — what each owner consumes from this pass

Nothing deleted here had a consumer outside `ui/theme` / `ui/components` tests, so **no rebase conflict is expected from a deletion**. Every row below is a consumption the surface owner should make; none is required for the build.

### chat

| # | File:line | Change | Notes |
|---|---|---|---|
| C1 | `ui/screens/chat/components/MessageBubble.kt:938-939` | `RoundedCornerShape(20.dp)` / `(16.dp)` -> `ExpressiveShapes.Bubble.Compact.System` / `Geometric.System` | value-identical |
| C2 | `MessageBubble.kt:959` | `RoundedCornerShape(34.dp)` -> `ExpressiveShapes.Bubble.Rounded` | value-identical |
| C3 | `MessageBubble.kt:960-972` | COMPACT user/assistant literals -> `Bubble.Compact.User` / `Bubble.Compact.Assistant` | value-identical |
| C4 | `MessageBubble.kt:974-986` | GEOMETRIC user/assistant literals -> `Bubble.Geometric.User` / `Bubble.Geometric.Assistant` | value-identical |
| C5 | `MessageBubble.kt:927` KDoc | "`ExpressiveShapes.Bubble.*` is a pending theme decision" -> it exists; `bubbleShapeFor` is the family function, the tokens are its radii | doc |
| C6 | `StreamingIndicator.kt:83, 311` | `3000` -> `MaterialChatMotion.Durations.Indicator.StreamingSpin` | value-identical |
| C7 | `StreamingIndicator.kt:85, 312-314` | `2400` -> `Durations.Indicator.StreamingMorph` | value-identical |
| C8 | `StreamingIndicator.kt:425` | `1400` -> `Durations.Indicator.StreamingShimmer` | value-identical |
| C9 | `StreamingIndicator.kt:470-486` `gatedInfiniteFloat` | take a `TweenSpec<Float>` (callers pass `MaterialChatMotion.Tweens.linear(StreamingSpin)` / `Tweens.easeInOut(StreamingMorph)`) instead of `(durationMillis, easing)`, and drop the raw `tween(` at `:486` and the `:462-468` comment claiming no tokens exist | optional; retires the last raw `tween(` in chat |
| C10 | `ChatErrorCopy.kt:36, 39` | `const val REQUEST_FAILED = CopyRules.REQUEST_FAILED`; `const val TRY_AGAIN = CopyRules.TRY_AGAIN` | optional; `CopyRulesTest` already pins equality, so a drift fails the theme test either way |
| C11 | `ChatErrorCopyTest.kt:96-100` | may stay (pins chat == assistant); the theme now pins both against `CopyRules` | no change required |

### home-settings

| # | File:line | Change | Notes |
|---|---|---|---|
| H1 | `ui/screens/settings/SettingsScreen.kt:1971-1990` `previewBubbleShape` | delete; draw `ExpressiveShapes.Bubble.Rounded` / `Compact.User|Assistant` / `Geometric.User|Assistant` (or chat's `internal bubbleShapeFor(style, isUser, isAssistant = !isUser, MessageGroupPosition.Single)`) | **visible change**: ROUNDED preview 28 -> 34; COMPACT 18/4/18/10 -> 18/6/18/18 (user) and 6/18/18/18 (assistant); GEOMETRIC 16/6 -> 18/4/18/10 and 4/18/10/18. The preview finally shows what chat draws (audit #1) |
| H2 | `ui/screens/mindmap/components/MindMapCanvas.kt:135` | `tween(durationMillis = Durations.StreamingIndicator, easing = LinearEasing)` -> `MaterialChatMotion.Tweens.linear(MaterialChatMotion.Durations.StreamingIndicator)`; drop the `tween` / `LinearEasing` imports | value-identical |
| H3 | `ui/MainViewModel.kt:60, 73, 86`; `ui/screens/insights/InsightsViewModel.kt:58`; `ui/screens/settings/SettingsViewModel.kt:340, 712, 824, 841, 878` | `e.message ?: "Failed to …"` -> `CopyRules.failureLine("…")` with no exception pass-through | audit #2 + the verb ruling; 85 "Failed to", 7 "Unable to", 5 "Couldn't" literals in `ui/**` (grep `"Failed to\|Unable to\|Couldn't"`) |
| H4 | `SettingsViewModel.kt:927-1225` (every `"Failed to save … setting"` snackbar) | `CopyRules.failureLine("save … setting")` | same |
| H5 | `ui/screens/settings/AssistantVoiceSettingsViewModel.kt:83, 108, 113, 148, 221` | "… Please retry." / "Reopen to retry." fail `CopyRules.isNotice` ("please", "retry") -> "… and try again." | carried from the W6 assistant report; not pinned by any test yet |
| H6 | `ui/screens/conversations/HomeListInsets.kt:20-24` | now also read by `MainActivity.kt:351, 389` for the ad banner and the toolbar row margin; retuning `ToolbarBottomMargin` / `ToolbarHeight` / `RowGap` moves both | contract note, no change |

### assistant

| # | File:line | Change | Notes |
|---|---|---|---|
| A1 | `assistant/ui/AssistantMotionTest.kt:27-36, 38-46` | delete the theme-value asserts (`Press.Shape/Progress` 0.6/800, `fast == Press.Progress`, `Disclosure.Expand` 0.75/420, `Shrink` 1.0/420); `MotionTokensTest.kt:17-27, 29-40` now pins them. Keep the two lines comparing `Shrink` to `AssistantMotion.exit()` — that is an assistant contract | optional; the test passes as is |
| A2 | `assistant/ui/AssistantCopy.kt:37, 59` | `const val ERROR_VERB = CopyRules.TRY_AGAIN`; `const val REQUEST_FAILED = CopyRules.REQUEST_FAILED` | optional; equality is pinned in `CopyRulesTest` |
| A3 | `assistant/ui/AssistantCopyTest.kt:161, 178` | the notice / phrase rule tests may call `CopyRules.isNotice` / `isPhrase` instead of their own predicate | optional |
| A4 | `assistant/ui/AssistantExpressiveStyle.kt:39` `stateSwitch() = Spatial.fast()` | no change; `pressMorph` now delegates to `fast`, values unchanged | — |
| A5 | `AssistantSizes.ChipIcon = 16` | unchanged by ruling; `ExpressiveSizes.Chip.Icon` is now a getter aliasing `IconSmall` (still 18) | — |

### orchestrator / unowned

- `ExpressiveLoadingIndicator.kt`: `M3ExpressiveDeterminateProgress`, `M3ExpressiveLoadingWithText`, `M3ExpressiveFullscreenLoading`, `MorphingSendButton` have 0 callers (word-exact grep) and were not named in the brief; decide whether they go next wave.
- The five domain/data ASCII-ellipsis sites from W6 (`ConversationRepositoryImpl.kt:498,500`, `SendMessageUseCase.kt:825,1064`, `GenerateConversationTitleUseCase.kt:324,339`, `GetBuiltinPersonasUseCase.kt:104`) are still unassigned; `CopyRules.ELLIPSIS` is the constant to use.

## Deliberately left alone

- `ExpressiveShapes.Chip` / `CustomShapes.Pill`, `expressiveControlShape` on `shapeMorph()`, `ExpressiveEmptyState` on every screen, assistant `ChipIcon` 16: rulings.
- Pressable round-vs-squircle (audit F7, `ExpressiveButton.kt:105, 293, 360, 435, 546` animated `RoundedCornerShape(cornerRadius)`): not in this brief; untouched.
- `CustomShapes.Button`, `ButtonPressed`, `MessageInputContainer`, `ConversationItem`, `Dropdown`, `ModelPicker`, `TopAppBar`, `SearchBar`: live consumers, kept.
- `ExpressiveSnackbar.kt`, `ExpressiveSurfaces.kt` (ambient washes `:68-70`, disabled alpha `:196`): re-read, no change.
- `MaterialChatMotion.Tweens` KDoc still says "Spring animation specifications for legacy call sites" above the spring-returning `Enter` / `Exit` / `Fade` / `ScreenTransition`; the two new tween helpers sit beside them. Renaming the object is a wider migration (`Tweens.StandardDuration` etc. have consumers); not done.
- `SmoothStreamingText.kt`: untouched.

## Needs another owner

- **chat**: C1–C9 (token consumption, all value-identical), C10 optional.
- **home-settings**: H1 (**the one visible fix in this table**: the settings preview finally matches the bubble), H2, H3–H5 (copy verb + exception pass-through; the predicates to test against are `CopyRules.isPhrase` / `isNotice`).
- **assistant**: A1 (delete the moved pins), A2–A3 optional.
- **orchestrator**: the four remaining zero-caller composables in `ExpressiveLoadingIndicator.kt`; the five domain/data ellipsis sites.

## Not verifiable without a device

- Part 7: the Play-flavor ad banner's new resting position (`listBottomClearance()` instead of 104dp) and its slide-in/out against the toolbar under three-button and gesture navigation. The arithmetic is unit-tested in `HomeListInsetsTest`; the rendered stack (banner above toolbar above nav bar) was not seen. The GitHub flavor never composes the banner.
- Part 3: that `M3ExpressiveLoadingIndicator` draws the same frames through `Tweens.linear` / `Tweens.easeInOut` as through the raw `tween(` calls (same duration, easing, repeat mode; pinned in `MotionTokensTest`, not rendered).
- Part 4: `ExpressiveButton` glyphs at the token sizes (20 / 24, unchanged values; not rendered).
- Part 5: `pressMorph()` delegating to `fast()` is value-identical (0.6 / 800); no press was observed.
- Parts 1, 2, 6, 8: additive tokens, deletions with zero consumers, test moves; nothing renders differently until a surface consumes a token.
- No screenshot, emulator or hosted run was made.
