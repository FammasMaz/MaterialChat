# Pass W7 — Assistant surface (branch `pass/w7-assistant`, rebased onto `origin/main` 213efa87 after the W7 theme merge)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, and their tests. Nothing under `ui/theme`, `chat/**`, `scripts/`, `.github/`, `SmoothStreamingText.kt`, `MainActivity`, `AssistantVoiceSettingsViewModel.kt` or any other owner's file was edited. Nothing pushed. No emulator, no device. Static reading plus JVM tests only.

## Commits (in order, after the rebase)

| Hash | Part | Summary |
|---|---|---|
| `63ac8970` | 1 | Surface entrances on `Spatial.container()`, exits on the sheet's critically damped exit curve; `AssistantMotionTest` rewritten around assistant behaviour and exit math |
| `0ef7e324` | 2, 3 | Click haptic on the chip and toolbar family; raw `FilledTonalButton` kept and documented; the theme press-token cross-pin dropped |
| `9ba43923` | 5, 6 | Sequence walk round 4: three contradictions fixed, four pins added; voice panel on `dialogActionButtonColors()` and the picker's `shapes.medium` corner |
| `7c22186a` | theme rows | `AssistantCopy.ERROR_VERB`/`REQUEST_FAILED` alias `CopyRules`; `ChipIcon` delta documented against `ExpressiveSizes.IconSmall` |
| (this) | — | REPORT.md |

## Gates

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0 before every commit and after the rebase.
- `:app:testGithubDebugUnitTest` before every commit. Final: **809 tests, 0 failures, 0 errors, 0 skipped** (`app/build/test-results/testGithubDebugUnitTest/*.xml`). Added by this pass: `AssistantMotionTest` net +2 (6 tests, was 4), `AssistantViewModelTest` +4. One intermediate run failed on a wrong pin in my own new test (the hint is once per chat, not once per question); the pin was corrected and the commit amended before the rebase (`9ba43923` is the corrected one).
- After the rebase: `grep` of `assistant/**` and the two panels for `CustomShapes.Fab|BottomSheet|CodeBlock|ProviderCard|Dialog|MessageActionButton|SendButton` and `M3ExpressiveCircularProgress|LinearProgress` -> no hits; compile exit 0.
- No test, testTag, contentDescription, script or workflow touched or weakened; no androidTest added. No `FontStyle.Italic`. No preference key changed. Pill geometry (76dp row, 38dp `PillCorner`), `AssistantKeyboardSpacing`, keep-awake, particle physics, recognizer/TTS selection and the action-directive protocol untouched. `SpeechRecognitionManagerTest` not rewritten.

## Part 1 — entrances on the container spring (`63ac8970`) [W6 #15]

- `AssistantExpressiveStyle.kt:41-53` `surfaceReveal()` now returns `container()` (= `ExpressiveMotion.Spatial.container()`, 0.6 / 380) instead of `Spatial.default()` (0.8 / 380). Consumers: `AssistantOverlay.kt:724-733` (answer surface `slideInVertically` + `scaleIn`) and `AssistantResponseCard.kt:242` (thinking surface's corner-anchored `Animatable`).
- The answer surface's **exit** (`AssistantOverlay.kt:735-744` `slideOutVertically` + `scaleOut`) moved from `surfaceReveal()` to `AssistantMotion.exit()` (`Spatial.collapse()`, 1.0 / 420). Reason: the container spring's under-damped envelope (e^(-0.6·√380·t) to 1 %) is ~394 ms, past the session's 320 ms `AssistantExitMotion` window, so a surface dismissed while the window is leaving would be cut off mid-motion. On the exit token the sheet's drop, the folding regions and the surface's slide-out are one curve (M3: overshoot on arrival, never on departure).
- `AssistantMotionTest.kt` (rewritten): `entrancesArriveOnTheSharedContainerSpring` (surfaceReveal == Spatial.container == AssistantMotion.container, damping < 1), `theEntranceSpringCouldNotServeAsAnExit` (the envelope above > 320 ms, which is the proof the exit had to move), `disclosuresFoldOnTheSheetsOwnExitCurve` (exit == Spatial.collapse; Shrink has exit's damping and stiffness; Expand shares the stiffness — assistant contracts, no theme literal), `everyExitSettlesInsideTheWindow` (alpha 99 % at 150–180 ms; exit 90 % at 180–200 ms < 320; 99 % at 315–335 ms; alpha before exit) unchanged in substance, now also asserts exit damping is exactly 1.0.

## Part 2 — raw M3 buttons under `assistant/**` (`0ef7e324`) [W6 #5/#9]

Inventory (grep `Button(`): `AssistantOverlay.kt:1069` `IconButton` (attach affordance, animated corner), `:1163-1187` `FilledIconButton` ×4 (primary mic/send/stop, animated shape + pop), `:1431` `FilledTonalIconButton` (toolbar connected group), `:1544` `FilledTonalButton` (`AssistantActionChip`), `:1598` `IconButton` (Screen chip remove), `:1634` `IconButton` (chrome keyboard/close); `AssistantVoiceSettingsPanel.kt:277,288` `TextButton` (dialog actions, the M3 dialog convention the audit already ruled fine).

- `AssistantActionChip` (`:1544`) **stays on the raw `FilledTonalButton`**, documented in its KDoc (`:1540-1550`): `ExpressiveButton` fixes a 56dp minimum height, 28dp inset, a hand-rolled 28 -> 16dp round morph, 20dp icons and the theme tonal pair, and exposes no shape, colour or height; the chip is the 40dp chip capsule, draws the contrast-resolved chip pair, takes a per-position connected shape for the attach pair, and presses on the library's own M3 Expressive `ButtonShapes` morph. Forcing it onto `ExpressiveButton` would break the toolbar's measured-width fit (`AssistantAnswerToolbar`) and the connected pair.
- What was genuinely missing versus `ExpressiveButton` was the click haptic: `AssistantActionChip` and `AssistantToolbarAction` (`:1431`) now wrap `onClick` with `HapticPattern.CLICK` through `rememberHapticFeedback()` (`:1437-1440`, `:1562-1565`), so a chip or toolbar tap feels like every other button tap in the app. The primary button, the affordance and the chrome buttons already have their own feedback paths and were not changed.
- Icon buttons are not `ExpressiveButton` candidates (different component; their shapes are the per-position/animated contracts W5 ruled on).

## Part 3 — theme-token pins out of `AssistantMotionTest` (`0ef7e324`, `63ac8970`) [W6 #15]

- Deleted: the 0.6/800 literal asserts on `Press.Shape`/`Press.Progress`, `fast == Press.Progress`, `Disclosure.Expand` 0.75/420 and `Shrink` 1.0/420 literals (`MotionTokensTest.kt:17-27, 29-40` now holds them per `pass-w7-theme.md` A1).
- Kept: `stateSwitch() == Spatial.fast()` (assistant's own alias), `exit() == Spatial.collapse()`, `Shrink` has exit's damping and stiffness, `Expand` has exit's stiffness, and all exit-settle math. The class KDoc states the split.

## Part 4 — copy: `CopyRules` and the three `ERROR_CLIENT` diagnostics (`7c22186a`, no change for the diagnostics)

- `AssistantCopy.kt:37-40, 61` (`7c22186a`): `ERROR_VERB = CopyRules.TRY_AGAIN`, `REQUEST_FAILED = CopyRules.REQUEST_FAILED` (theme row A2); `AssistantCopyTest` keeps its own rules and does not pin chat equality (`CopyRulesTest` does). A3 (calling `CopyRules.isNotice`/`isPhrase` from `AssistantCopyTest`) not taken: the assistant's predicates are stricter (they also ban "retry" and "exception", and check `VOICE_NOTICES` sentence by sentence), and the theme row marks it optional.
- `SpeechRecognitionManager.kt:109, 123, 211` ("... Please try again.") left as they are. Verified path: the only consumer of `SpeechRecognitionResult.Error.message` is `AssistantViewModel.kt:371`, which passes it through `AssistantCopy.recognitionError(code, message)` (`AssistantCopy.kt:88-98`); under `ERROR_CLIENT` that returns `RECOGNIZER_UNAVAILABLE` (when the text says "not available") or `RECOGNITION_FAILED`, never the engine text. `AssistantCopyTest.theManagersInternalClientDiagnosticsNeverReachThePill` still pins this. Rewriting them would mean editing `SpeechRecognitionManagerTest`'s exact-text pins, which the brief forbids.

## Part 5 — sequence walk round 4 (`9ba43923`)

Read from `AssistantViewModel`, `AssistantNotices.resolve`, `AssistantOverlayContent` and `MaterialChatVoiceInteractionSession`. Columns as in W6.

### A. screen-context withheld -> Settings -> return -> ask again

| Step | ViewModel | Overlay |
|---|---|---|
| Screen question, no capture | `screenDecision` = `UNAVAILABLE` -> `markScreenUnavailable()` once per invocation (`screenUnavailableShown`); send proceeds | hint (info container, "Open assistant settings" chip `assistant_screen_settings`) inside the answer surface |
| Open assistant settings | session `assistantLauncher().launch(systemIntent)` -> `hide()` -> `onHide`: `clearConversation()`, `releaseScreenContext()` -> `onScreenContext(null)` resets `screenContextUnavailable` and the once-flag | gone |
| Return (new invocation) | continue window (default 0): `startNewChat()`; otherwise the exchange is kept and a capture can be staged | fresh or as left |
| Ask again, still no capture | once-flag reset by `onScreenContext(null)` -> hint shown again | hint again |

**Contradiction 1 (fixed):** inside one invocation, `startNewChat()` cleared the messages but neither `screenContextUnavailable` nor `screenUnavailableShown`. Result: the hint stayed above an *empty* pill (a hint about an exchange that no longer exists), and the next screen question in the same invocation got no hint at all because the once-flag was still consumed. Fix: `startNewChat()` resets both (`AssistantViewModel.kt:291-306`). Pin: `new chat clears the screen hint and a later screen question shows it again` (also pins the existing once-per-chat rule: the third question in the same chat clears the marker on send and is not told again).

### B. photo attached -> 429 -> Try again

| Step | ViewModel |
|---|---|
| `+` -> Attach photo -> picker -> inbox | `takeDeliveredAttachments()` -> `_pendingAttachments` (thumbnail row `assistant_attachments`) |
| Send | `send()` takes `attachments`, empties the tray, creates the conversation, `addMessage(userMessage with attachments)`, `savedQuestion = userMessage` |
| 429 | `handleError(RATE_LIMITED)`; messages settled; notice "The model is busy right now. Wait a moment and try again." + Try again |
| Try again | `retryLastQuery()` -> `send(reuseSavedQuestion = true)`; tray is empty, so `reused = savedQuestion` (same conversation, same text, still the last row) -> the **saved row carries the photo** and goes out again; nothing is saved twice |

The photo survives the retry through the saved row (the W6 design). Pin: `a photo goes out again on retry through the saved question row` (captures `sendMessage`'s `messages` and asserts the retry's history has the photo exactly once, one saved row, tray empty).

**Contradiction 2 (fixed):** a failure *before* the question row is saved (no provider, conversation creation throwing, `getProvider` throwing) had already emptied the tray; `handleError` then offered "Try again", but the retry had neither a saved row nor pending photos, so it would silently resend the text **without the photo**, and the photo file was never discarded either (orphaned on disk, invisible to the user). Fix: `send()` tracks `questionSaved`; the no-provider branch and the catch-all put the turn's attachments back in front of the tray (`restoreAttachments`, `AssistantViewModel.kt:447-449, 534, 763-766, 1090-1093`), so the thumbnail row shows what is still attached and the retry sends it. Pin: `a photo returns to the tray when the turn fails before the question is saved`.

### C. Open in app during speaking

| Step | ViewModel | Session |
|---|---|---|
| Answer complete, TTS speaking | `isSpeaking`; queue draining | toolbar: Open in app offered (`answerInFlight` false) |
| Open in app | `openInApp()` -> `AssistantEvent.OpenInApp(conversationId)` | `openInApp()` -> `MainActivity OPEN_CHAT` -> `hide()` -> `onHide` -> `clearConversation()` -> `cancelStreaming()` + `stopSpeaking()`: queue cancelled, `ttsJob` cancelled, `textToSpeechManager.stop()` |

For a streamed answer the partial is already saved (the toolbar withholds the chip until completion, W6), and speech stops with the hide: consistent.

**Contradiction 3 (fixed):** a *device action* result ("Flashlight on.") is spoken by `speakActionResult`, which launched its coroutine untracked (`viewModelScope.launch`, not `ttsJob`). `stopSpeaking()` cancels `ttsJob` and calls `textToSpeechManager.stop()`, which stops the Android engine but not a Gemini (cloud) or sherpa utterance driven by `speechRuntime.speak`; the untracked job kept running after Stop, New chat, Open in app and the hide, so the overlay could be gone while the phone was still talking. Fix: `speakActionResult` assigns its job to `ttsJob` (`AssistantViewModel.kt:834-837`), so every stop path cancels it like the streamed answer's speech. Pin: `a spoken action result stops when the overlay hides for Open in app` (the runtime's `speak` is released on the hide, `isSpeaking` false, `android.stop()` called).

Residual, recorded: Open in app after a local device action with no model turn emits `OpenInApp(conversationId = null)` (no conversation was created), so the app opens on the chat list, not on this exchange. The exchange is not saved anywhere; the toolbar offers the chip because `hasContent` is true. Not changed: creating a conversation for a one-line device outcome is a product decision, and `AssistantLaunchRoute` handles a null id by design. Listed for the orchestrator.

### D. invocation while the previous session is still speaking

The session is a new `VoiceInteractionSession` with its own `ViewModelStore`; `SpeechRecognitionManager`, `TextToSpeechManager`, `AssistantSpeechRuntime` and `SherpaTtsBackend` are `@Singleton` (`AssistantModule.kt`). The previous session's `onHide`/`onDestroy` -> `clearConversation()`/`onCleared()` -> `stopSpeaking()` + `textToSpeechManager.release()`; the new session's ViewModel calls `initialize()` again (`AssistantViewModel.kt:178`), and `TextToSpeechManager.initialize()` restarts the engine when `engine == null` (`TextToSpeechManager.kt:60-68`). With contradiction 3 fixed there is no untracked speech job that could outlive the old ViewModel. `release()` bumps `generation`, so a late callback from the old engine is ignored. Checked, no change.

### E. rotation / locale change mid-answer

The overlay is a `ComposeView` inside the session window (`onCreateContentView`), not an Activity: a configuration change does not recreate the session, the ViewModel (`viewModelStoreInstance`) or the stream. `AssistantOverlayContent` is keyed on `showTrigger`, which only `onOverlayShown` increments, so a rotation recomposes without restarting the animations; `LocalConfiguration` is read for the sheet's `maxHeight` (`AssistantOverlay.kt:1238`) and follows the new height. Copy is all Kotlin constants (no resources), so a locale change cannot leave half a window in another language. Checked, no change; whether the IME and the keyboard-spacing path survive a mid-answer rotation is listed as unverified.

## Part 6 — settings panels (`9ba43923`)

`AssistantVoiceSettingsPanel.kt`:
- `:278-294` the two dialog action `TextButton`s draw `dialogActionButtonColors()` (`ui/components/DialogActionColors.kt`), the one helper every other dialog host on home/settings draws, instead of building `textButtonColors(dialogActionColor())` by hand twice. Same resolved colour.
- `:390` `VoiceChoice` corner `MaterialTheme.shapes.large` -> `shapes.medium`: the Settings screen's model picker row it imitates draws `shapes.medium` (`SettingsScreen.kt:2383`); the voice row was one step rounder than the row it copies.
- Already on the pattern from W6 and re-read: help lines `bodySmall`/`onSurfaceVariant` without trailing period via `helpLine()`; section titles `titleMedium`; spacing on `ExpressiveSpacing.Inset`/`Gutter`; `ExpressiveButton` for every in-dialog action; install row on one component.

`AssistantScreenContextSettingsPanel.kt`: re-read against `SettingsExpressive.kt` (`SettingsGroup` + `SettingsToggleRow`, segmented shapes/colours, 24dp leading icons, `titleSmall`/`bodySmall` type pair, `ExpressiveButton` Outlined for the full-width action, `ExpressiveSpacing.Inset` spacing, help text in the supporting style without trailing period). Already on the shared rows since W5/W6; nothing to change. `ExpressiveSizes.Icon*`: the rows' icon sizes are inside `SettingsToggleRow` (home-settings), not in this file.

## Theme rows consumed (`7c22186a`)

| Row | Done |
|---|---|
| A1 | `AssistantMotionTest` theme-value asserts deleted (done in `63ac8970`/`0ef7e324`); exit-math and `Shrink`-vs-`exit()` kept |
| A2 | `AssistantCopy.ERROR_VERB`/`REQUEST_FAILED` alias `CopyRules` |
| A3 | not taken (assistant predicates are stricter; optional) |
| A4 | no change (`stateSwitch() = Spatial.fast()`; `pressMorph` delegating to `fast` is value-identical) |
| A5 | `AssistantSizes.ChipIcon` stays 16 (ruling); KDoc (`AssistantExpressiveStyle.kt:213-220`) now names `ExpressiveSizes.IconSmall` as the rung the 2dp delta is measured against |
| deleted symbols | none referenced under `assistant/**` or the two panels |

## Deliberately left alone

- `AssistantActionChip` on raw `FilledTonalButton` (Part 2, documented); `ChipIcon` 16 (ruling); `CompactTarget = ExpressiveSizes.Compact`.
- The three `SpeechRecognitionManager` diagnostics (Part 4).
- `AssistantMotion.playful()` for the sheet's scale entrance (`AssistantOverlay.kt:679`): a hero moment, the brief's #15 names only the slide/scale surface entrances.
- Open in app after an unsaved local device action (sequence C residual).
- The W6 residuals: sheet-level `weight(1f, fill = false)`, resumed pill at rest with a blank draft, `DeviceActionHandler` outcome sentences.

## Needs another owner

- **home-settings** (`ui/screens/settings/AssistantVoiceSettingsViewModel.kt:83, 108, 113, 148, 221`): "Please retry." / "Reopen to retry." fail `CopyRules.isNotice`; these reach my dialog as `message`/`previewMessage` (carried from W6; theme row H5).
- **home-settings** (`SettingsExpressive.kt:142` `SettingsToggleRow`): no `enabled` parameter; dependent screen-context rows are hidden rather than disabled (carried).
- **orchestrator**: whether a device-action-only exchange should create a conversation so Open in app lands on it (sequence C residual); otherwise the toolbar could withhold the chip when `currentConversationId == null`, which is a one-line assistant change once decided.
- **theme** (optional): an `ExpressiveButton` overload taking shape/colours/height would let the chip drop the raw M3 button; not requested, recorded only.

## Not verifiable without a device

- The feel of the container spring on the answer and thinking entrances (0.6 vs 0.8 damping: a small visible overshoot on arrival) and the exit on the collapse token; the math is pinned, the render is not.
- The click haptic on the chips and the toolbar actions (needs a vibrator).
- The restored photo thumbnails reappearing in the opened pill after a pre-save failure (state pinned; the `assistant_attachments` row recomposing is not).
- Speech actually stopping on the hide for the Gemini and sherpa backends (the JVM pin cancels the runtime's `speak`; the backends' own cancellation on a real device is W6/W5 territory).
- `VoiceChoice` on `shapes.medium` beside the Settings picker; `dialogActionButtonColors()` painting the same colour it did (value-identical).
- Rotation/locale change mid-answer: IME and keyboard-spacing continuity (sequence E).
- Everything carried from W2–W6 (squircle swap, answer-fit caps, resume in keyboard mode, settings launch paths, `StartEllipsis` at API 26, six-line notice cap).
