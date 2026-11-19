# Pass W6 — Assistant surface (branch `pass/w6-assistant`, rebased onto `origin/main` 3293eaa1 after the W6 theme merge)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, and their tests. Nothing under `ui/theme`, `scripts/`, `.github/`, `SmoothStreamingText.kt`, `MainActivity`, `AssistantVoiceSettingsViewModel.kt` or any other owner's file was edited. Nothing pushed. No emulator, no device.

## Commits (in order)

| Hash | Part | Summary |
|---|---|---|
| `51fa89ec` | 1 | `AssistantShapes.Answer/Notice/Thinking` deleted (theme's `ShapeTokensTest` pins the radii directly since ad765acd); the three W5 radii comments dropped; `Toolbar` aliases `CustomShapes.Pill`; leftover inventory verified |
| `0285d4a7` | 2 | Sequence walk round 3: six contradictions fixed in the ViewModel/overlay state machine, each pinned by a JVM test |
| `a8447f22` | 3 | `VoiceCopy`: one copy table for every voice-layer sentence; `AssistantCopy` aliases the recognizer sentences and lists every voice sentence for the rule test |
| `40ae32ec` | 4 | Voice dialog: the neural model's install states on one component, "Try again" for the two "Retry" buttons, disabled "Remove stored key", help-line period convention, spacing on `ExpressiveSpacing`; screen-context panel: a leading icon on every row, spacing on `ExpressiveSpacing` |
| (this commit) | — | REPORT.md |

## Gates

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0 before every commit.
- `:app:testGithubDebugUnitTest` before every commit, 0 failures each time. After the rebase the base suite was **764**; final **772 tests, 0 failures, 0 errors, 0 skipped** (`app/build/test-results/testGithubDebugUnitTest/*.xml`). Added: `AssistantViewModelTest` +5, `AssistantAttachRevealTest` +1, `AssistantCopyTest` +2.
- No test, testTag, contentDescription, script or workflow touched or weakened; no androidTest added. No `FontStyle.Italic`. No preference key changed. Pill geometry (76dp row, 38dp `PillCorner`, `AssistantShapes.Pill`), `AssistantKeyboardSpacing`, keep-awake, particle physics, recognizer/TTS selection and the action-directive protocol untouched.

## Part 1 — leftover inventory (`51fa89ec`)

| Item | Result |
|---|---|
| Raw `spring(` / `tween(` under `assistant/**` | None. Every spec is a `ExpressiveMotion` token or an `AssistantMotion` alias of one (`AssistantExpressiveStyle.kt:33-77`). |
| `.copy(alpha =` on text or icons | None. The three hits are the scrim fill (`AssistantOverlay.kt:365`, `SCRIM_ALPHA`) and two resolver candidates inside `AssistantContrast.kt:141,303` that are gated for contrast before use. |
| Raw `RoundedCornerShape(` | Remaining sites all have a reason: `Pill` (contract); the animated corners (`AssistantOverlay.kt` glyph container, attach affordance, primary button, notice action chip) take a `Dp` that moves on `Press.Shape`/`stateSwitch`, which a fixed theme shape cannot; `connectedShape()` and `ConnectedPressed` (per-position radii, no theme token); the Screen chip's 8dp thumbnail and the chrome icon buttons (`GlyphRoundCorner`). `Toolbar` was `RoundedCornerShape(50)`, value-identical to `CustomShapes.Pill`, and now aliases it (`AssistantExpressiveStyle.kt:98`). |
| `AssistantShapes.Answer/Notice/Thinking` | Deleted (`AssistantExpressiveStyle.kt`, the former `:88-108`); the three "W5: … radii" comments at `AssistantOverlay.kt:1242,1659` and `AssistantResponseCard.kt:252` dropped. Zero main-code consumers; `AssistantContrastSlotTest` still asserts `AssistantShapes.Chip === ExpressiveShapes.Chip`. |
| ASCII `...` in user-visible strings | None; the only hits are code comments (`AssistantAction.kt:197`, `ActionDirectives.kt:32`). |
| dp literals `AssistantSizes`/`ExpressiveSizes` now model | None left in the overlay beyond the contracts (76dp row, 40dp blur radius, the waveform bar geometry, `heightIn(max = 600.dp)` sheet cap) and the elevations. |

## Part 2 — sequence walk round 3 (`0285d4a7`)

Read from `AssistantViewModel` + `AssistantNotices.resolve` + `AssistantOverlayContent`. Columns: pill (glyph / state line / primary button / trailing chrome), response surface, notice (where, container, action), chips in the opened pill, toolbar. "—" = not shown.

### A. invoke → listen → no speech → re-listen

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| Invoke (mic permission, voice on) | `requestAutoListen` consumed once for this `showTrigger`; glyph Capturing (primary container, waveform); "Listening…" after `Ready`; primary = Stop (capturing); keyboard toggle + close | — | — | — | — |
| Timeout (`ERROR_SPEECH_TIMEOUT`) | glyph Idle; state line "Try again" (`restingLine` of a `RetryListening` notice); primary = Mic | — | above the pill, error container: "No speech heard. Try again." + outlined chip "Try again" (`RetryListening`) | — | — |
| Re-listen (chip or Mic) | `hideKeyboard`, `onInterrupt`, `startVoiceInput` → **W6 clears `error`/`notice`**; back to the first row | — | leaves on the state-switch spring (`lastNotice` keeps its text for the exit) | — | — |

Auto-listen does not refire after the notice: the invocation is already consumed and nothing in this sequence increments `showTrigger` (pinned by the existing once-per-invocation tests).

### B. invoke with keyboard → type → send → answer → follow-up

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| Keyboard | `enterKeyboard` → `onInterrupt` (any auto-listen stops); text field with "Ask anything"; `+` affordance; keyboard-hide toggle; primary = Mic | — | — | — | — |
| Type | toggle folds (`!hasText`); primary = Send | — | — | — | — |
| Send | `submitTyped` → `onInterrupt`, **W6 `attachReveal.questionSent()`**, `sendQuery`; field empties; glyph Thinking (secondary container, rounded square, loading indicator); primary = Stop (interrupt); close folds into the surface | rises: question bubble + thinking surface | — | reveal closed | New chat, Close; **W6 Open in app withheld (`answerInFlight`)**; Copy absent (no assistant text) |
| Answer streams | glyph Thinking → Speaking while TTS; keyboard mode keeps the field (no state line) | answer text + typing dots; auto-scroll | — | — | Copy appears (`hasAssistantText`); Open in app still withheld |
| Complete | glyph Idle (after speech drains); primary = Mic | dots gone | — | — | Open in app appears (labelled or compact by measured width), New chat, Copy, Close |
| Follow-up | type → Send as above | both exchanges, scrolled to the newest | — | — | same |

### C. attach photo → camera cancelled → attach again

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| `+` | affordance rotates 45° into a tonal square; pill opens upward | — | — | "Take photo" / "Attach photo" connected pair | — |
| Take photo | `modeChosen()` closes the reveal; `requestAttach` stops listening, `attachInProgress = true`, session launches `AssistantAttachActivity` and hides; `clearConversation` keeps `textInput` | — | — | folds | — |
| Camera cancelled | activity `close()` → `reopenSession(RESUME)` → `onOverlayShown(resumeAfterAttach = true)`: no auto-listen, `showTrigger++`; **W6: the overlay recomposes in keyboard mode when the draft is non-blank**, so the typed question, `+` and Send are on screen (before: resting prompt with the draft hidden behind it and no `+`) | — | none (a cancelled picker publishes nothing) | — | — |
| Attach again | `+` → reveal → Attach photo → picker → inbox → `pendingAttachments` | — | — | thumbnail row (`assistant_attachments`) keeps the pill opened; reveal closed; `+` stays while something is attached | — |

Residual: with a blank draft the resumed pill starts at rest (no `+`), so the user re-enters keyboard mode to attach; recorded, not changed.

### D. screen context withheld → Open settings → return

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| Screen question, no capture | `screenDecision` = `UNAVAILABLE` → `markScreenUnavailable` once per invocation; the send proceeds; state line "Ask anything" after the answer (hint is not an error) | question + answer | inside the surface, info container: `ScreenContextAccess.notice` + chip "Open assistant settings" (`assistant_screen_settings`) | — | Open in app, New chat, Copy, Close |
| Open assistant settings | session launches the system page through `assistantLauncher()` → `hide()` → `onHide` releases the capture (`onScreenContext(null)` clears `screenContextUnavailable` and the once-flag) and `clearConversation` clears `error`/`notice` | — | — | — | — |
| Return (new invocation) | within the continue window: the exchange is still shown, notice gone, a new capture can be staged; outside it: `startNewChat`, resting pill | as left or fresh | — | Screen chip if a capture arrives and the question asks for it | as left |

### E. 429 → wait → retry

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| 429 (`httpStatus = 429`) | `handleError(RATE_LIMITED)`: **W6 `settleStreamingMessages()`** (dots stop), `isLoading`/`isStreaming` false; state line "Try again"; primary = Mic | question bubble (+ any partial text, no dots) | inside the surface, error container: "The model is busy right now. Wait a moment and try again." + "Try again" (`RetryRequest`) | — | Open in app (the question is saved), New chat, Copy if partial text, Close |
| Wait | unchanged; no auto-listen | | | | |
| Try again | `retryLastQuery`: **W6 drops the partial answer and the question bubble together, `send(reuseSavedQuestion = true)` reuses the saved row when it is still the conversation's last message**; glyph Thinking; "Thinking…" | question bubble redrawn once + thinking surface | gone | — | Open in app withheld again while in flight |

### F. 401 → Open settings → return

| Step | Pill | Surface | Notice | Chips | Toolbar |
|---|---|---|---|---|---|
| 401/403 | `handleError(PROVIDER_REJECTED)`; state line "Ask anything" (no retry invited) | question (+ settled partial) | error container: "The provider rejected the request. Check the API key in settings." + "Open settings" (`OpenSettings`) | — | as in E |
| Open settings | `AssistantEvent.OpenSettings` → session `openSettings()` → `MainActivity.ACTION_OPEN_SETTINGS` via `assistantLauncher()` → `hide()` → `clearConversation` clears the failure | — | — | — | — |
| Return | new invocation; the failed question's row is in the saved conversation; notice gone | as left or fresh | — | — | — |

### Contradictions fixed

| # | Contradiction (before) | Fix | Test |
|---|---|---|---|
| 1 | A mid-stream failure left the streaming marker on the last message: typing dots running under the failure notice (toolbar without a settled answer). | `handleError` and the `Cancelled` branch call `settleStreamingMessages()` (`AssistantViewModel.kt:733,765,775,910`). | `a failed stream settles the partial answer and the retry reuses the saved question` |
| 2 | "Try again" stacked a second question bubble under the half answer and saved the question a second time (the model's history carried it twice). | `retryLastQuery` drops the partial + bubble (`:886-902`); `send(reuseSavedQuestion = true)` reuses the row the first attempt saved when it is still the conversation's last message and no photo/screen goes with the retry (`:162,286,565-578`). | same + `a rate limit names the wait and the retry resends the question once` |
| 3 | `startVoiceInput` never cleared `uiState.error`/`notice`; `AssistantNotices` ranks `error` first, so a speech-output failure (or a voice fallback notice) masked a later timeout's "No speech heard. Try again." with a stale notice that offered no action — two notices' worth of state, one shown, the wrong one. | `startVoiceInput` clears both (`:319-326`). | `a new listening attempt clears the stale speech-output error so a timeout is reported` |
| 4 | Typed send left the photo reveal open above the arriving answer (keyboard mode stays, so nothing closed it). | `AssistantAttachRevealState.questionSent()` (`AssistantAttachReveal.kt:33`), called from `submitTyped` (`AssistantOverlay.kt:621`). | `AssistantAttachRevealTest.sendingTheQuestionCollapses` |
| 5 | Resume after the picker/camera recomposed the overlay under `key(showTrigger)` with `keyboardMode = false`: the kept draft was present but hidden behind "Ask anything", no `+`, no Send. | `keyboardMode` starts true when `textInput` is non-blank (`AssistantOverlay.kt:555`). | ViewModel side already pinned (`typed text survives the hide…`); the composition side is unverified without a device. |
| 6 | Open in app was offered while the answer was in flight; tapping it hides the session, which cancels the stream, and the partial answer is only saved on completion. | `AssistantUiState.answerInFlight` (`:1169`); the toolbar withholds the chip while it is true (`AssistantOverlay.kt:753`). | `open in app is withheld while the answer is awaited or arriving` |

Checked and not a contradiction: two notices at once (`AssistantNotices.resolve` returns one; the standalone surface is hidden whenever the answer surface is up); a stale Screen chip after send (consumed in `sendQuery`); auto-listen firing after a notice (consumed per invocation; only `onOverlayShown` increments `showTrigger`, and the resume path pre-consumes it). `a rejected key opens settings and the hide clears the failure` pins sequence F's ViewModel side.

## Part 3 — voice copy (`a8447f22`)

- `assistant/voice/VoiceCopy.kt` (new): every sentence the recognizer, the speech backends, the voice fallbacks and the neural-model install can show, under the assistant's rules (full stops, sentence case, "try again" as the only error verb, no "please", no exception text). In the voice package so the backends do not depend on the overlay. `samples()` lists them all for the rule test.
- Routed sites: `AssistantSpeechRuntime.kt:70,81,88,103,125`; `GeminiTtsBackend.kt:55,62,255-261` (the `GeminiTtsFailure` diagnostics); `SherpaTtsBackend.kt:73,112,114,116`; `SherpaModelState.kt:59-61`; `SherpaModelStore.kt:133,145,153,162,167,172,176,204-205`; `TextToSpeechManager.kt:184,192,195,254,256`; `VoiceState.kt:105` (`getErrorMessage` reads the same table, so an unknown code falls back to `RECOGNITION_FAILED` instead of "An error occurred. Please try again.").
- `AssistantCopy.kt:47,49,67` alias `VoiceCopy`'s recognizer sentences; `:85` `VOICE_NOTICES`; `:88-98` `recognitionError` delegates to `VoiceCopy.recognition`. Pill-facing wording unchanged.
- Rewordings (user-visible, settings-facing diagnostics): "Speech unavailable." → "Speech output is not available."; "Text-to-speech not initialized" → "Speech output is not ready. Try again."; "Speech synthesis failed (code: N)" → "Speech output failed. Try again." (no code leaks); "Configure a Gemini speech key in voice settings." → "No Gemini speech key is set. Add one in voice settings."; "use Android speech" → "use the Android voice"; "permission denied" → "permission was denied"; "connectivity" → "connection"; "invalid or unsupported audio" → "audio that could not be played"; the neural install-failure notice is now three sentences ("X failed to install. Reason. Using the Android voice.") instead of a parenthesis. Tests that assert by substring (`SherpaModelStateTest`, `SherpaTtsBackendTest`, `SherpaModelStoreTest`, `AssistantSpeechRuntimeTest`) still pass unchanged.
- `AssistantCopyTest.everyVoiceLayerSentenceFollowsTheCopyRules` (`:178`): ≥40 sentences, full stops, sentence case, no please/retry/exception, every `GeminiTtsFailure` present, the recognizer table one sentence per code 1..9 and the generic fallback for 99. `theManagersInternalClientDiagnosticsNeverReachThePill` (`:212`): the three `SpeechRecognitionManager` diagnostics (`:109,123,211`, "… Please try again.") are mapped to `RECOGNITION_FAILED` by `recognitionError` under `ERROR_CLIENT`; left as literals because `SpeechRecognitionManagerTest` pins them by exact text (changing them means editing that test's pins).

## Part 4 — settings panels (`40ae32ec`)

`AssistantVoiceSettingsPanel.kt`:
- `:326-360` `SherpaModelInstallRow`: the four install states (not downloaded / downloading-verifying-installing / installed / failed) on one component with one order — status line, progress (`LinearWavyProgressIndicator`, determinate only while bytes arrive), one action. Before, each state built its own stack in a different order (button-then-help vs help-then-button).
- `:120-124` "Retry models" → "Try again" (the help line above names what failed); `:360` "Retry download" → "Try again". No "Retry" left under my ownership.
- `:263` "Remove stored key on save" disabled while no key is stored or removal is already queued.
- `:317` `helpLine()`: help lines carry no trailing period (the settings supporting-text convention); the two voice sentences that reach the dialog from `VoiceCopy` (`previewMessage`, the install failure) pass through it so the dialog does not mix the two conventions.
- `:104,333,392` spacing on `ExpressiveSpacing.Inset`/`Gutter` instead of `12.dp`/`16.dp`.
- Row order unchanged: conversation → speech input → speech output; backend-specific rows under the backend choice.

`AssistantScreenContextSettingsPanel.kt`:
- `:99` the "Always include the screen" row gets a leading icon (`AllInclusive`); it was the only row of the group without one, so its title column did not align.
- `:71-72` spacing on `ExpressiveSpacing.Inset`.
- Disabled state: the three dependent rows are hidden (not disabled) while the master switch is off, as before; `SettingsToggleRow` has no `enabled` parameter (see Needs another owner).

## Deliberately left alone

- `ChipIcon` 16dp (documented delta, orchestrator ruling) and `CompactTarget` = `ExpressiveSizes.Compact` (32).
- The sheet-level `weight(1f, fill = false)` pair and the fully stacked pill + notice + keyboard residual (W5 states 1, 5).
- The three `SpeechRecognitionManager` internal diagnostics (see Part 3).
- `DeviceActionHandler` outcome sentences (spoken action results, a different family; already sentences with full stops).
- The resumed-pill-at-rest residual with a blank draft (sequence C).
- Open in app on the toolbar after a failure: kept (the question is saved, the conversation exists).

## Needs another owner

- **home-settings** (`ui/screens/settings/AssistantVoiceSettingsViewModel.kt:83,148,221`): "Check provider settings and retry.", "Voice preview timed out. Please retry.", "Unable to save all voice settings. Please retry." → "… and try again." / "Try again." (the error-verb and no-"please" rules); `:108,113` "Reopen to retry." → "Reopen to try again.". These reach my dialog as `message`/`previewMessage`.
- **home-settings** (`ui/screens/settings/components/SettingsExpressive.kt:142`): `SettingsToggleRow` has no `enabled` parameter; a disabled (rather than hidden) state for dependent rows needs it.
- **chat** (carried): `util/AttachmentLimits.limitNotice()`/`skippedNotice()` reach the assistant notice surface; already on the period rule.
- **orchestrator** (carried): `ChipIcon` 16 vs `ExpressiveSizes.Chip.Icon` 18.

## Not verifiable without a device

- Every rendered outcome of Part 4 (the install row's four states, the wavy indicator, the disabled button, the new row icon) and of `Toolbar` on `CustomShapes.Pill` (value-identical, unrendered).
- Sequence C step 3: the overlay recomposing in keyboard mode on resume — `focusRequester.requestFocus()` and `keyboard?.show()` on a fresh composition under `key(showTrigger)` while the session window is being re-shown; whether the IME comes up by itself.
- The reveal closing on a typed send (state is pinned; the fold animation is not).
- Sequences D and F end to end (system settings page / `MainActivity.ACTION_OPEN_SETTINGS` through `assistantLauncher()`, the hide, the return) — the ViewModel side is pinned, the launch path is not.
- The retry redraw in the `LazyColumn` (auto-scroll after the two items are dropped and one re-added).
- Open in app disappearing and reappearing around a stream without a layout jump in the toolbar's measured-width switch.
- Everything carried from W2–W5 (squircle swap, answer-fit caps, exit settle timing, `dialogActionColor()` fallbacks, `StartEllipsis` at API 26, six-line notice cap).
