# Pass W5 — Assistant surface (branch `pass/w5-assistant`, rebased onto main `c22e062` after the W5 theme merge)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, and their tests. No theme, chat, home, settings-screen, `MainActivity`, script or workflow file was edited. Nothing pushed. No emulator, no device.

## Commits (in order, after the rebase)

| Hash | Part | Summary |
|---|---|---|
| `7e4df1f` | 2 | Small shape morphs draw `Press.Shape` / `Press.Progress`; the opened pill's height disclosures draw `Disclosure.Expand` / `Shrink`; `AssistantMotionTest` pins the springs and the exit settle times |
| `4ed5232` | copy | `AssistantCopy.ERROR_VERB = "Try again"` (never "Retry") and the notice period rule pinned; voice-layer TTS failures use the same verb; Sherpa install-failure notice gets its missing full stop |
| `c343d91` | 5 | Answer-surface notice capped from the surface's measured height instead of weighted 1:1 (the W4 weights left the message 0dp with the keyboard up); `AssistantAnswerFit` + test |
| `8208d55` | 1, 3 | Answer/notice/thinking surfaces on `ExpressiveShapes.AnswerSurface/NoticeSurface/ThinkingSurface`, chips on `ExpressiveShapes.Chip`; `AssistantSizes`/`AssistantSpacing` alias `ExpressiveSizes`/`ExpressiveSpacing` where the value matches; `AssistantSurfacePalette : ContrastSlotSet`; voice-settings dialog buttons on `dialogActionColor()` |
| `43aebd6` | 4 | Session's `ACTION_OPEN_SETTINGS` aliases `MainActivity.ACTION_OPEN_SETTINGS` (routed on main by `17170a9`) |
| (this commit) | 6 | REPORT.md |

## Gates

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 -q :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0 before every commit.
- `:app:testGithubDebugUnitTest` before every commit: final **757 tests, 0 failures, 0 errors, 0 skipped** (`app/build/test-results/testGithubDebugUnitTest/*.xml`). Base `ec82b40` had 712; the delta is the W5 theme/home merges plus the assistant's new tests: `AssistantMotionTest` 4, `AssistantAnswerFitTest` 6, `AssistantContrastSlotTest` 2, `AssistantCopyTest` +2.
- No test, testTag, contentDescription, script or workflow touched. No `FontStyle.Italic`. Pill geometry (76dp row, 38dp `PillCorner`, `AssistantShapes.Pill`), `AssistantKeyboardSpacing`, keep-awake, particle physics, recognizer/TTS selection, action directives untouched.

## Part 1 — shapes (`8208d55`)

- `AssistantOverlay.kt:1243` answer surface `shape = ExpressiveShapes.AnswerSurface`; `:1660` notice surface `ExpressiveShapes.NoticeSurface`; `components/AssistantResponseCard.kt:253` thinking surface `ExpressiveShapes.ThinkingSurface`. Radii unchanged (36/36/28/28, 24/24/18/18, 20/20/8/20); the class changes from circular to G2 squircle.
- `AssistantExpressiveStyle.kt:89-108`: `AssistantShapes.Answer/Notice/Thinking` are kept as `RoundedCornerShape` radii records because the theme's `ShapeTokensTest.kt:174-178` (not mine to edit) pins them equal to those `RoundedCornerShape`s and equal in radii to the three theme tokens. No assistant composable draws them any more; the KDoc says so. See Needs another owner.
- `AssistantExpressiveStyle.kt:101` `AssistantShapes.Chip: Shape get() = ExpressiveShapes.Chip` (capsule, same geometry as the old `RoundedCornerShape(50)`); `AssistantOverlay.kt:1532` `AssistantActionChip(shape: Shape)` widened from `RoundedCornerShape`. `Toolbar`, the connected-group shapes and `ConnectedPressed` stay `RoundedCornerShape` (the connected group needs per-position radii the theme has no token for; the pressed 12dp is the app-wide press corner).
- `AssistantShapes.Pill` untouched (geometry contract).

## Part 2 — motion (`7e4df1f`)

- `AssistantMotion.morph()` removed (`AssistantExpressiveStyle.kt:62-65` comment); sites: glyph container corner `AssistantOverlay.kt:854` -> `ExpressiveMotion.Press.Shape`; `+` -> `x` rotation `:1037` -> `Press.Progress`; its corner `:1047` -> `Press.Shape`. The notice action chip `:1744` was already on `Press.Shape`.
- `surfaceReveal()` kept only for the answer surface's slide + scale (`:717-733`); the four height disclosures (`:808/:813` opened pill region, `:1459/:1461` thumbnails + Screen chip, `:1490/:1492` attach actions) draw `Disclosure.Expand` / `Disclosure.Shrink`.
- Exit settle, by spec math (`AssistantMotionTest.everyExitSettlesInsideTheWindow`, critically damped unit spring `x(t)/x0 = (1 + wt) e^(-wt)`):
  - `AssistantMotion.exit()` = `Spatial.collapse()` (1.0 / 420, omega 20.5 rad/s): 90 % of travel at **190 ms**, 99 % at **324 ms**.
  - `Disclosure.Shrink` is the same spec, so a folding region leaves on the sheet's own curve.
  - `AssistantMotion.effect()` (1.0 / 1600, omega 40 rad/s), the sheet alpha and every `fadeOut`: 99 % at **166 ms**.
  - `AssistantExitMotion` window: 320 ms at animator scale 1.0 (0 ms at scale 0). The last 1 % of the drop/shrink lands 4 ms after the window, invisible because the sheet's alpha (through `ModulateAlpha`) is at 1 % by 166 ms. Feel unchanged on the morphs: `Press.Shape` (0.6 / 800) is value-identical to the `Spatial.fast()` the assistant aliased before (pinned).
- `Press.Shape` is `SpringSpec<Dp>` and `Press.Progress` `SpringSpec<Float>`, so each site takes the typed token rather than a generic `morph<T>()`.

## Part 3 — sizes, spacing, contrast slot (`8208d55`)

- `AssistantExpressiveStyle.kt:178` `AssistantSpacing.Inset = ExpressiveSpacing.Inset` (12); `:181` `ChipInset = ExpressiveSizes.Chip.Inset` (14); `:200` `AssistantSizes.ChipHeight = ExpressiveSizes.Chip.Height` (40); `:214` `Target = ExpressiveSizes.Target` (48); `:217` `Control = ExpressiveSizes.Control` (40); `:230` `CompactTarget = ExpressiveSizes.Compact` (32).
- Two deltas documented, not changed (`:202-212`, `:224-230`):
  - `ChipIcon` stays **16dp**; `ExpressiveSizes.Chip.Icon` is 18. The assistant's 18dp `NoticeGlyph` is deliberately one step above the chip icon so the notice's glyph leads its message; the toolbar's measured-width chip/group switch and the Screen chip's 28dp thumbnail are laid out against 16. Moving to 18 changes four sites by 2dp and needs rendering to judge.
  - `CompactTarget` is the theme's 32dp `Compact`, not the 44dp `CompactTarget`: the Screen chip's x sits inside a 40dp chip, so 44 cannot fit. The assistant name is kept for its call sites.
- `AssistantContrast.kt:68-77` `AssistantContrastPair : ContrastSlot` (`slotName = name`, `fill = container`; `ratio`/`passes` kept as the assistant computes them, equal to the interface defaults, pinned); `:184-191` `AssistantSurfacePalette : ContrastSlotSet` (`surfaceName = "assistant"`, `slots = pairs()`, `SURFACE` constant). `AssistantContrastSlotTest` pins the set against `pairs()` on light and dark schemes and the size/spacing aliases plus the two deltas. The theme's `ThemeContrastTest.kt:59-65` adapter can now collapse to the palette's own resolver (theme-owned; see Needs another owner).

## Part 4 — `OPEN_SETTINGS` (`43aebd6`)

`17170a9` on main routes `com.materialchat.OPEN_SETTINGS` to the Settings screen. `MaterialChatVoiceInteractionSession.kt:521` now aliases `MainActivity.ACTION_OPEN_SETTINGS` (one constant, no string duplicate), `:433-445` KDoc no longer says "not yet routed". Intent as sent: `Intent(context, MainActivity::class.java)`, flags `NEW_TASK | CLEAR_TOP`, `action = com.materialchat.OPEN_SETTINGS`, no extras, through `assistantLauncher().launch(intent)` (plain-launch fallback; the overlay hides on success). `MainActivity` not edited.

## Copy (`4ed5232`)

- `AssistantCopy.kt:30-33` `ERROR_VERB = "Try again"`, `AFTER_ERROR = ERROR_VERB`; `AssistantNotices.kt:9,11` `RetryRequest` / `RetryListening` labels read it. `AssistantCopyTest.theErrorVerbIsTryAgainNeverRetry` (`:103`) fails on "Retry" anywhere in the fixed notices, phrases, action labels or the recognizer table, and requires "try again" inside `NO_CONNECTION`, `RATE_LIMITED`, `REQUEST_FAILED`.
- Period rule (`AssistantCopy.kt:12-19` doc, `:64-74` `NOTICES` / `PHRASES` lists, `AssistantCopyTest.noticesAreSentencesWithFullStopsAndPhrasesCarryNone` `:127`): every notice is one or more sentences, each ending in a full stop, sentence case, no "please"; phase lines, status lines, markers and chip labels carry none.
- Voice-layer sentences that reach the pill as `error` / `notice`: `AssistantSpeechRuntime.kt:81` "... and try again." (was "and retry"); `GeminiTtsBackend.kt:55` "Try again, or use Android speech."; `:260` `TRANSPORT` "Try again later."; `SherpaModelState.kt:62` "... failed to install (reason). Using the Android voice." (the full stop between the two sentences was missing). The remaining Gemini/Sherpa diagnostics already follow both rules and name the product deliberately (they are settings-facing diagnostics, kept).
- `AssistantVoiceSettingsPanel.kt:120` "Retry models" and `:248` "Retry download" are button labels in the settings dialog, not notices; the home-settings owner kept "Retry" out of their verbs this wave too, so these two are the only "Retry" left under my ownership. Left as is: they name a specific thing to retry (models, download), which "Try again" alone would not; recorded here so the ruling can be applied if the orchestrator wants them changed.
- Help lines in the two panels (`VoiceHelp`, the screen-context explanation `AssistantScreenContextSettingsPanel.kt:123`) are supporting text without trailing periods, consistent with every settings row; not notices, not changed.

## Part 5 — state walk round 3 (static, measured)

Assumptions as in W4: 360dp wide, 640dp tall window, IME ~280dp, sheet 336dp wide and ~344dp tall with the keyboard up; bodyMedium 20sp -> one notice line 20dp at font scale 1.0, 26dp at 1.3.

| # | State | Measurement | Result |
|---|---|---|---|
| 1 | Notice + four thumbnails + Screen chip in the pill, keyboard up | Opened pill with the attach actions closed: 76 + 12+40 (Screen chip) + 16+72 (thumbnails) = 216dp; the sheet leaves 344 − 8 − 216 = **120dp** for the notice; chrome 74 -> message **46dp** = one line at 1.3, two at 1.0, scrolling. With the attach actions also open (+52): notice gets **68dp**, below its 74dp chrome, so the chip keeps its 40dp and the message row measures **0dp**. | Acceptable in the common case; the fully stacked case is a residual (the pill's geometry contract wins by design; the only alternatives are overrunning the window or collapsing the opened pill). The chip and the Screen chip's remove target stay reachable. Not changed. |
| 2 | 40-line code block in the 0.55-screen answer cap | Surface 352dp; the `LazyColumn` (`AssistantResponseCard.kt:117-121`, weighted, `fill = false`) scrolls one tall item; `MarkdownText` code block is `horizontalScroll` + `softWrap = false` (`MarkdownText.kt:957-959`), orthogonal to the vertical list. Toolbar (56dp) and the Using-screen marker are unweighted, so they stay inside the clip. | OK. |
| 3 | Weighted notice at 1.3, keyboard open, 640dp window — inside the answer surface | Surface 260dp, content 228dp. W4's two `weight(1f, fill = false)` children: Column weights do not redistribute what a `fill = false` child leaves, so each got (228 − 56 − 24) / 2 = **74dp**, exactly the notice's chrome (24 inset + 10 gap + 40 chip): message **0dp** at every font scale. **Fixed (`c343d91`)**: `AssistantOverlay.kt:1254-1260` measures the surface in a `BoxWithConstraints`; the notice is `heightIn(max = noticeCap)` (`:1308`) with `noticeCap = content − toolbar 56 − marker − gaps − 48dp exchange floor` = **100dp** -> message **26dp** (one line at 1.3, one at 1.0; the list, which scrolls, takes the rest and keeps at least 48dp). With the Using-screen marker: cap 62dp, message 0dp (residual: the chip still fits; the toolbar never leaves the clip because the cap subtracts it). Without the keyboard: cap 192dp, `PROVIDER_REJECTED` (three lines at 1.3) fits whole. `AssistantAnswerFitTest` pins all of these. | Fixed; residual recorded. |
| 4 | Same state, notice standing alone above the pill (no answer surface) | 344 − 8 − 76 = 260dp available; the five-line screen notice at 1.3 is 130 + 74 = 204dp. | OK. |
| 5 | Sheet-level weights competing (`AssistantOverlay.kt:735` response, `:772` notice) | Hypothesis raised and **rejected**: an exited `AnimatedVisibility` emits no layout node (the content `Layout` is only composed while the target or current state is visible, or during seeking / initial animations), so the invisible sibling carries no weight; the two only overlap during the ~170 ms cross-fade. Reasoned from the library's published implementation; the Gradle cache holds no `animation-android` sources, so this is not read from the exact 1.11.0-alpha06 text. | No change. |
| 6 | Thinking / speaking / listening with a long partial transcript / toolbar at 360dp / error states | Unchanged from W4 (nothing in this pass touched those layouts). | OK. |

## Dialog buttons (`8208d55`)

`AssistantVoiceSettingsPanel.kt:286-304`: Save and Close draw `ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.dialogActionColor())`; the theme gate measured raw `primary` on `surfaceContainerHigh` at 4.07:1 (VIOLET light) and 4.13:1 (CITRUS light). Both dialogs already had `ExpressiveShapes.Dialog` + `dialogEntrance()` from W4. The loading dialog has no buttons. `AssistantScreenContextSettingsPanel.kt` has no dialog.

## Deliberately left alone

- `AssistantShapes.Answer/Notice/Thinking` values and class (pinned by the theme's test); `Pill`, `Toolbar`, connected-group shapes.
- `ChipIcon` 16dp and the 32dp compact target (documented above).
- The sheet-level `weight(1f, fill = false)` on the response and standalone notice (state 5).
- The fully stacked pill + notice + keyboard residual (state 1) and the marker residual (state 3).
- "Retry models" / "Retry download" button labels in the voice dialog (see Copy).
- `AssistantCopy.AFTER_ERROR` not re-pointed at `ErrorContentCopy.TRY_AGAIN`: the theme's `ErrorContentCopyTest` already pins them equal, and the assistant's copy object must stay pure of UI-component imports for its JVM tests.
- `SmoothStreamingText`, pill geometry, tags, keyboard spacing, keep-awake, particles, recognizer/TTS, directives.

## Needs another owner

- **theme** (`ui/theme/ShapeTokensTest.kt:174-178`): the assertion pins `AssistantShapes.Answer/Notice/Thinking` as `RoundedCornerShape`; now that no assistant composable draws them, the pin can compare radii only (or the three vals can be deleted once the test is repointed). Until then they stay as the radii record.
- **theme** (`ui/theme/ThemeContrastTest.kt:59-65`): the `"assistant"` adapter line can become `AssistantSurfacePalette::resolve` (the palette implements `ContrastSlotSet` as of `8208d55`).
- **theme / orchestrator**: decide `ChipIcon` 16 vs `ExpressiveSizes.Chip.Icon` 18 for the assistant (needs a rendered look at the toolbar chip, the Screen chip and the Using-screen marker).
- **chat** (carried): `util/AttachmentLimits.limitNotice()` / `skippedNotice()` copy reaches the assistant's notice surface; it already follows the period rule.

## Not verifiable without a device

- Every rendered outcome of the squircle swap (answer, notice, thinking surfaces) and of `ExpressiveShapes.Chip` on the chips; RTL mirroring of `AnswerSurface`/`ThinkingSurface`.
- The `BoxWithConstraints` in the answer surface reporting bounded height under the real host (`heightIn(max = 0.55 screen)` inside the sheet's weighted slot with the IME up); the 100dp cap and the 26dp message line were computed, not seen; that the message `verticalScroll`s inside its cap rather than clipping.
- State 1's 46dp / 0dp message heights and state 5's no-node claim for an exited `AnimatedVisibility`.
- `Press.Shape` / `Disclosure.Expand/Shrink` feel: value-identical on the morphs; the four disclosures move from 0.8/380 to 0.75/420 (expand) and 1.0/420 (shrink), a visible but unrendered change. Exit settle times are spec math, not frame timing.
- `dialogActionColor()` falling back to `onSurface` on VIOLET and CITRUS light in the voice dialog.
- The 401/403 "Open settings" chip landing on the Settings screen end to end (session launch path + `17170a9`).
- Everything carried from the W2-W4 lists (toolbar measured-width switch, `StartEllipsis` at API 26, six-line notice cap, rate-limit bodies beyond the sampled set, exit fade with `lastNotice`).
