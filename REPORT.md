# Pass W8 — Assistant surface (branch `pass/w8-assistant`, rebased onto `origin/main` 2ae059d5 after the W8 theme merge)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, and their tests. Nothing under `ui/theme`, `chat/**`, `scripts/`, `.github/`, `SmoothStreamingText.kt`, `MainActivity` or any other owner's file was edited. Nothing pushed. No emulator, no device. Static reading plus JVM tests only.

## Commits (in order, after the rebase)

| Hash | Part | Summary |
|---|---|---|
| `0e0e2051` | 1 | Orchestrator ruling: a device-action-only exchange creates no conversation; the toolbar withholds Open in app while `currentConversationId == null` (`AssistantUiState.conversationSaved` / `openInAppAvailable`); pinned |
| `d2fb4260` | 2 | Sequence walk round 5: a cut-off answer is saved as far as the pill showed it; the attachment-limit notice leaves once obeyed; four pins |
| (next) | 3, theme rows | `AssistantCopy.NO_CONNECTION` / `RATE_LIMITED` alias `CopyRules` (A2); `ConnectedPressedCorner == ExpressiveChipDefaults.PressedRadius` pinned (A3); A1 re-weighed and kept raw with the measured reasons in the chip KDoc; every window and voice line checked against `CopyRules.isNotice` / `isPhrase`; every Gemini HTTP status and every neural install failure mapped to a typed notice |
| (last) | — | REPORT.md |

## Gates

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0 before every commit and after the rebase.
- `:app:testGithubDebugUnitTest` before every commit. Base 6ade255c: 826. After Part 1: 827. After Part 2: 830. After the rebase onto the W8 theme merge plus Part 3 / theme rows: **840 tests, 0 failures, 0 errors, 0 skipped**. Added by this pass: `AssistantViewModelTest` +3 (+ the `openInAppAvailable` lines in an existing pin), `AssistantViewModelAttachmentsTest` +1, `AssistantCopyTest` +2, `AssistantMotionTest` +1.
- One intermediate failure, fixed before the Part 2 commit: `a failed stream settles the partial answer and the retry reuses the saved question` — the test's stream stays open after `StreamingState.Error`, so the retry's `cancelStreaming()` cancelled it and the new catch path saved a partial the error path had already settled. Fix: `shownPartial` is cleared on `Error` and `Completed`; only a turn that was genuinely cut off mid-answer persists anything. One unit-test compile error (missing `stage` argument on a `SherpaModelState.Downloading` pin) fixed in place.
- No test, testTag, contentDescription, script or workflow touched or weakened; no androidTest added. No `FontStyle.Italic`. No preference key changed. Pill geometry (76dp row, 38dp `PillCorner`), `AssistantKeyboardSpacing`, keep-awake, particle physics, recognizer/TTS selection and the action-directive protocol untouched. `ChipIcon` stays 16dp.
- Rebase onto `2ae059d5` was clean (no conflicts); grep of `assistant/**` for the symbols the theme deleted (`M3ExpressiveDeterminateProgress|M3ExpressiveLoadingWithText|M3ExpressiveFullscreenLoading|MorphingSendButton|MessageInputContainer`) -> no hits.

## Part 1 — the ruling (`0e0e2051`)

- `AssistantViewModel.kt`: `AssistantUiState.conversationSaved` (new field, KDoc at the data class) is set to true at the one place a conversation row is created (`send()`, right after `currentConversationId = created`) and reset by `startNewChat()`. `AssistantUiState.openInAppAvailable = conversationSaved && !answerInFlight`.
- `AssistantOverlay.kt` (`AssistantOverlayContent`, the `showOpenInApp =` argument): `hasContent && !uiState.answerInFlight` -> `hasContent && uiState.openInAppAvailable`. The W6 in-flight rule is kept inside the new property; the one-line change the W7 report described.
- The local device-action branch of `send()` never touches `createConversationUseCase` or `conversationRepository`, so a device-action-only exchange still creates no conversation (nothing was changed there; the pin proves it).
- Pins (`AssistantViewModelTest`): `a device-action-only exchange creates no conversation and withholds Open in app` (no `withProviderAndModel`, no `addMessage`, `conversationSaved` false, chip withheld; a model turn in the same chat sets it, the chip is still withheld while the answer is awaited, offered on completion, withdrawn by New chat); `open in app is withheld while the answer is awaited or arriving` extended with the four `openInAppAvailable` cases.
- Not changed: `openInApp()` still emits `OpenInApp(conversationId = currentConversationId)` and the session still handles a null id (the `AssistantNoticeAction.OpenApp` path for `NO_PROVIDER` legitimately opens the app with no conversation). The existing pin `a spoken action result stops when the overlay hides for Open in app` still calls `openInApp()` directly on a device-action exchange to exercise the stop path; the toolbar no longer offers that tap, the ViewModel method stays callable.

## Part 2 — sequence walk round 5 (`d2fb4260`)

Read from `AssistantViewModel`, `AssistantOverlay` (`AssistantOverlayContent`, the sheet column, `AssistantAttachRevealContent`, `AssistantNoticeSurface`), `AssistantAttachRevealState`, `AssistantAnswerFit`, `AssistantNotices.resolve`, `MaterialChatVoiceInteractionSession` (`onShow`, `onHide`, `onCreateContentView`), `ChatRepositoryImpl` (what `Cancelled` carries) and the chat delegate's Stop path (`persistPartial`).

### E. rotation / locale change mid-answer (`key(showTrigger)`)

| Step | ViewModel | Overlay / session |
|---|---|---|
| Answer streaming | `streamingJob` alive, `isStreaming` | answer surface, `assistant_sheet` |
| Rotate | nothing: `showTrigger` only moves in `onOverlayShown`, which `onShow` calls once per `overlayShown` (`MaterialChatVoiceInteractionSession.kt:110-125`) | the session is a `ComposeView` inside the session window, not an Activity: no recreation; `key(showTrigger)` (`AssistantOverlay.kt:430`) does not change, so `AssistantOverlayContent` is recomposed, not restarted; `isVisible`, `keyboardMode`, `attachReveal` survive |
| `LocalConfiguration` consumers | — | `AssistantOverlay.kt:1241` answer `maxHeight = screenHeightDp * 0.55`, `AssistantResponseCard.kt:160` bubble width 0.82: both follow the new configuration |
| Locale | copy is Kotlin constants | nothing half-translated |

Already pinned at the seam: `auto listen is consumed once per visible invocation` asserts that a second `onOverlayShown()` while visible leaves `showTrigger` unchanged (no new invocation, no second microphone). Checked, no change. The IME and `AssistantKeyboardSpacing` continuity across a mid-answer rotation stays on the unverified list (W7 E).

### D. invocation while the previous answer is still arriving or speaking (`cancelStreaming` path)

| Step | ViewModel |
|---|---|
| Streaming, speaking | `streamingJob`, `speechQueue`, `ttsJob` |
| New question typed (`submitTyped` -> `onInterrupt` -> `cancelStreaming`), or new invocation (`onHide` -> `clearConversation` -> `cancelStreaming`) | `turn++`, `streamingJob.cancel()`, `stopSpeaking()`, `settleStreamingMessages()` |
| Before W8 | the partial answer stayed on the surface and in `_messages`, but nothing was saved: the next question's history (`getMessages`) carried two user rows in a row while the pill showed Q, partial A, Q, and the model answered a history it had never seen |

**Contradiction 1 (fixed):** `AssistantViewModel.kt` `persistPartialAnswer(conversationId, partial)`: a turn cut off mid-answer saves the answer as far as the pill showed it (`shownPartial`, updated on every `Streaming` snapshot, cleared on `Completed`, `Error`, `Cancelled` and the screen-directive resend) under the turn's own conversation id, inside `withContext(NonCancellable)` from the turn's `CancellationException` catch and from the `Cancelled` branch, only when the question row was saved. Chat's Stop does the same (`persistPartial`). The next turn `join`s the cancelled one before it saves its own row, so the order is Q, partial A, Q. A turn that produced nothing visible saves nothing. Pins: `a question typed over an arriving answer saves the answer as far as it got first`, `a new invocation over an arriving answer keeps it and an answer that never started saves nothing`.

Speech: with W7 contradiction 3 (`speakActionResult` on `ttsJob`) every speech job is owned; `cancelStreaming` -> `stopSpeaking` cancels the queue and the job and stops the Android engine. The new session's ViewModel re-initialises the singleton `TextToSpeechManager` (W7 D). Checked, no change.

The `answerInFlight` KDoc was corrected: Open in app is still withheld while the answer arrives, but the reason is now that the app would open on an answer cut off mid-sentence, not that the partial would be lost.

### F. Screen chip + four photos + notice in the pill, IME up, 360dp / font scale 1.3

| Step | ViewModel | Overlay |
|---|---|---|
| Screen question typed, capture present | `reconsiderScreenStaging` writes the chip | pill opens: Screen chip row (`assistant_screen_chip`) |
| Four photos from the picker | `takeDeliveredAttachments` keeps 4 | `assistant_attachments` row under the chip; the reveal's two attach actions fold away (`modeChosen`) |
| `+` again | `requestAttach`: `room <= 0` -> `notice = limitNotice()` | the notice surface above the pill (`weight(1f, fill = false)`, W4: the notice yields height, the pill never does); message scrolls inside its share, chip keeps 40dp |
| Height budget | — | sheet ~344dp with the IME up on 640dp; opened pill 268dp (chip 40 + 12 inset, thumbnails 64 + 16, actions closed, row 76) plus the 8dp gap leaves the notice ~68dp: one line of `bodyMedium` at 1.3 (26dp) plus 24dp chrome fits; the limit notice (two sentences, ~75 characters, three lines at 1.3 on 280dp) scrolls inside it. Nothing overruns the window |
| Remove a photo (obeying "Remove one to add another") | before W8: the notice stayed up over a pill that now has room, and a successful `+` tap next also left it standing | — |

**Contradiction 2 (fixed):** `removeAttachment` and a `requestAttach` that has room clear the notice when, and only when, it is `AttachmentLimits.limitNotice()` (`clearLimitNotice()`); any other notice (a failed camera launch, a voice fallback) is left alone. Pin (`AssistantViewModelAttachmentsTest`): `the limit notice leaves with the removal it asks for and only then`.

No third contradiction found in this round: the screen chip is not counted against the photo limit (`attachmentCount` only opens the pill), the W7 pre-save restore of photos still puts them in front of the tray, and the notice/answer precedence (`AssistantNotices.resolve`) is unchanged.

## Part 3 — voice layer copy (this commit)

- `AssistantCopyTest.everyLineOfTheWindowClearsTheThemesCopyPredicates`: every `AssistantCopy.NOTICES` and `VOICE_NOTICES` line passes `CopyRules.isNotice`; every `PHRASES` line and every `AssistantNoticeAction` label passes `CopyRules.isPhrase`; every recognizer code 1..9 maps to a notice. The assistant's own stricter rule tests (ban on "retry", "exception") stay; the theme predicates are the floor. (W7 row A3, now taken.)
- `AssistantCopyTest.everyGeminiAndNeuralFailureMapsToATypedNotice`: beyond the five sampled bodies, every HTTP status Gemini speech can answer with (400, 401, 403, 404, 408, 429, 500, 502, 503, 529 via `GeminiTtsFailure.fromHttp`) lands on a fixed diagnostic in the rule-tested list; the 429 body parser names `NO_QUOTA` only for a zero `quotaValue` and `QUOTA` for anything else including garbage and a body carrying a secret, which never reaches the diagnostic; every `GeminiTtsException.message` is a notice. Every neural install failure the store can report (`DOWNLOAD_INCOMPLETE`, `CHECKSUM_MISMATCH`, `MOVE_FAILED`, `INSTALL_FAILED`, `INSTALL_FAILED_UNEXPECTED`, `NO_INTERNET`, `DOWNLOAD_FAILED`, `notEnoughStorage`, `packageMissing`) reaches the pill through `sherpaFallbackMessage` as reason-first, Android-voice-last, and both it and the `fallingBack` wrapper are notices; `NotDownloaded` and `Downloading` too; `Installed` is null.
- Found and left as is (not in my ownership to rewrite without touching pinned tests): `TextToSpeechManager.kt:118, 128` "Failed to initialize text-to-speech" (`:128` with `error.message` pass-through), `:156` "Voice configuration failed", `:235` "Text-to-speech released". These are `TtsInitResult.Error.message`; the assistant consumer (`AssistantViewModel.kt:185`) only flips `isTtsAvailable` and never shows the text, so nothing reaches the pill. The other consumer is `AssistantVoiceSettingsViewModel` (home-settings), which also only tests `is TtsInitResult.Error`. The W8 theme report lists the two lines for the voice owner; see "Needs another owner".

## Theme rows consumed (this commit)

| Row | Done |
|---|---|
| A1 | **Kept raw**, KDoc rewritten with the measured reasons (`AssistantOverlay.kt`, `AssistantActionChip`). The orchestrator's condition was "only if the 40dp geometry and the measured-width Open-in-app fallback survive unchanged". They do not: (1) `ExpressiveButton`'s `ButtonContent` separates icon and label with a fixed 8dp spacer and draws the 18dp `ExpressiveSizes.Chip.Icon`; the chip draws `AssistantSpacing.Gap` (6dp) and the 16dp `ChipIcon` (ruling). `AssistantAnswerToolbar` measures the labelled chip from exactly those tokens (`ChipInset * 2 + ChipIcon + Gap + label`) to decide whether the label fits beside the icon group; a chip drawn 4dp wider than it is measured passes the fit check and is then clipped by its own capsule at the 360dp / 1.3 boundary the check exists for. (2) A fixed `shape` on `ExpressiveButton` animates only the scale dip; the chip presses on the library's `ButtonShapes` morph to `ConnectedPressed` (12dp), the same corner the toolbar group and the chrome buttons press to. (3) `ExpressiveButton` sets a 40dp *minimum*; the chip is exactly 40dp, which the opened pill's height budget assumes. A custom `ExpressiveButtonSize(icon = 16.dp)` fixes only the icon, not the spacer or the morph. |
| A2 | `AssistantCopy.NO_CONNECTION = CopyRules.NO_CONNECTION`, `RATE_LIMITED = CopyRules.RATE_LIMITED`; `AssistantCopyTest` keeps its rule pins and gains the equality lines inside the predicate test (no pure cross-equality pin existed to drop; `CopyRulesTest:35` holds it) |
| A3 | `AssistantMotionTest.thePressMorphLandsOnTheChipFamilysPressedRadius`: `ExpressiveChipDefaults.PressedRadius == AssistantShapes.ConnectedPressedCorner` (12dp); KDoc on the constant names the pin. Pin, not alias: the assistant's corner is a geometry contract the hosted gate and the toolbar group share, and a theme retune should fail a test here rather than move the pill's chrome silently |
| deleted symbols | none referenced under `assistant/**` or the two panels |

## Settings panels

`AssistantVoiceSettingsPanel.kt` and `AssistantScreenContextSettingsPanel.kt` re-read after the rebase against `ExpressiveChips.kt` (new) and the `ExpressiveButton` additions: neither panel draws an M3 chip, both are on `ExpressiveButton` / `dialogActionButtonColors()` since W7. Nothing to change.

## Deliberately left alone

- `AssistantActionChip` raw (A1 above). `ChipIcon` 16 (ruling). `CompactTarget = ExpressiveSizes.Compact`.
- `openInApp()` emitting a null conversation id: still reachable through the `NO_PROVIDER` notice action, by design.
- The three `SpeechRecognitionManager` diagnostics and the four `TextToSpeechManager` init strings (never shown; pinned tests on the manager).
- `AssistantMotion.playful()` sheet entrance; the W6/W7 residuals (sheet-level `weight(1f, fill = false)`, resumed pill with a blank draft, `DeviceActionHandler` outcome sentences).
- The answer-surface `maxHeight` fraction and the bubble width fraction reading `LocalConfiguration` (sequence E: they follow a rotation correctly; no token exists and none was added).

## Needs another owner

- **voice / orchestrator** (`assistant/voice/TextToSpeechManager.kt:118, 128, 156, 235`): "Failed to initialize text-to-speech" (with `error.message` pass-through at `:128`), "Voice configuration failed", "Text-to-speech released" fail `CopyRules.BANNED_FAILURE_VERBS` / `isNotice`. No consumer shows them today (both ViewModels only test `is Error`), so this is hygiene, not a visible defect; rewriting means touching `TextToSpeechManagerTest`, which the brief forbids me to weaken. Suggested: `CopyRules.failureLine("start speech output")` etc. without the exception text. Listed in the W8 theme report as unowned.
- **home-settings** (`AssistantVoiceSettingsViewModel.kt:83, 108, 113, 148, 221`): "Please retry." / "Reopen to retry." still fail `CopyRules.isNotice` (carried from W6/W7, theme row H5); they reach my dialog as `message` / `previewMessage`.
- **home-settings** (`SettingsExpressive.kt` `SettingsToggleRow`): no `enabled` parameter; dependent screen-context rows are hidden rather than disabled (carried).
- **theme** (optional, if A1 is ever wanted): `ExpressiveButton` would need a `pressedShape` (the W8 theme report already lists this for the orchestrator) and an `ExpressiveButtonSize.gap` so the icon-label spacer is a token the toolbar can measure. With both, the chip could migrate without changing a pixel.
- **chat** (information only): the assistant now persists a cut-off partial answer exactly like chat's Stop does; if chat changes what `persistPartial` saves (e.g. a marker that the answer was interrupted), the assistant's `persistPartialAnswer` should follow so the two Stop semantics stay one.

## Not verifiable without a device

- Part 1: the toolbar actually omitting the labelled chip / the compact icon after a device action (state pinned; `AssistantAnswerToolbar`'s measured-width branch with `showOpenInApp = false` is not rendered).
- Part 2 D: the saved partial row appearing in the chat list conversation and in the model's next context on a real provider (repository calls pinned; Room and the wire not exercised). A cut-off answer's partial on a provider whose `Cancelled` carries `partialContent` is replaced by `state.partialContent ?: ""` in `uiState.response` as before; the saved row uses what the pill showed.
- Part 2 F: the opened pill with Screen chip + four thumbnails + the limit notice at 360dp / font scale 1.3 with the IME up: the budget above is arithmetic on the tokens; the composition was not rendered. The notice folding away on the removal (`AnimatedVisibility` exit on `stateSwitch()`) is not rendered.
- Part 2 E: rotation / locale change mid-answer: IME and keyboard-spacing continuity, the answer surface re-measuring `maxHeight`, the thinking surface's corner-anchored animatable surviving the recomposition.
- Part 3: none of the Gemini / sherpa failures was produced on a device; the mapping is pure Kotlin and pinned end to end on the JVM.
- A1: the chip was not re-rendered (unchanged code); the claim that `ExpressiveButton` would draw it 4dp wider is read from `ButtonContent` (8dp spacer, 18dp icon) and the toolbar's measurement, not measured on screen.
- Everything carried from W2–W7 (squircle swap, answer-fit caps, resume in keyboard mode, settings launch paths, `StartEllipsis` at API 26, six-line notice cap, container-spring entrances, click haptics, speech stopping on hide for the cloud and neural backends).
- No screenshot, emulator or hosted run was made.
